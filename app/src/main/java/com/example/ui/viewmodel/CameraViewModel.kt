package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.CapturedPhoto
import com.example.data.repository.PhotoRepository
import com.example.domain.processor.PostProcessingPipeline
import com.example.domain.processor.SceneDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraViewModel(private val repository: PhotoRepository) : ViewModel() {
    private val TAG = "CameraViewModel"

    // Configuration states
    private val _flashMode = MutableStateFlow(ImageCapture.FLASH_MODE_OFF)
    val flashMode: StateFlow<Int> = _flashMode.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(false)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private val _zoomRatio = MutableStateFlow(1.0f)
    val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()

    private val _hdrEnabled = MutableStateFlow(true)
    val hdrEnabled: StateFlow<Boolean> = _hdrEnabled.asStateFlow()

    private val _portraitModeEnabled = MutableStateFlow(false)
    val portraitModeEnabled: StateFlow<Boolean> = _portraitModeEnabled.asStateFlow()

    private val _blurRadius = MutableStateFlow(12)
    val blurRadius: StateFlow<Int> = _blurRadius.asStateFlow()

    private val _activeFilter = MutableStateFlow("none")
    val activeFilter: StateFlow<String> = _activeFilter.asStateFlow()

    // Processing UI States
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // Selected photo for detail/slider compare activity
    private val _selectedPhoto = MutableStateFlow<CapturedPhoto?>(null)
    val selectedPhoto: StateFlow<CapturedPhoto?> = _selectedPhoto.asStateFlow()

    // Repository database photos
    val photos: StateFlow<List<CapturedPhoto>> = repository.getAllPhotos()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun toggleFlash() {
        val next = when (_flashMode.value) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
        _flashMode.value = next
    }

    fun toggleCameraLens() {
        _isFrontCamera.value = !_isFrontCamera.value
    }

    fun setZoom(ratio: Float) {
        _zoomRatio.value = ratio.coerceIn(1.0f, 8.0f)
    }

    fun toggleHdr() {
        _hdrEnabled.value = !_hdrEnabled.value
    }

    fun togglePortrait() {
        _portraitModeEnabled.value = !_portraitModeEnabled.value
    }

    fun setBlurRadius(radius: Int) {
        _blurRadius.value = radius.coerceIn(2, 30)
    }

    fun setActiveFilter(filterId: String) {
        _activeFilter.value = filterId
    }

    fun selectPhoto(photo: CapturedPhoto?) {
        _selectedPhoto.value = photo
    }

    fun deletePhoto(context: Context, photo: CapturedPhoto) {
        viewModelScope.launch {
            // Remove physical JPG file assets from storage
            withContext(Dispatchers.IO) {
                try {
                    File(photo.filePath).delete()
                    photo.enhancedFilePath?.let { File(it).delete() }
                } catch (e: Exception) {
                    Log.e(TAG, "Error cleaning files: ${e.message}")
                }
            }
            repository.deletePhoto(photo)
            if (_selectedPhoto.value?.id == photo.id) {
                _selectedPhoto.value = null
            }
        }
    }

    fun clearHistory(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val dir = context.getExternalFilesDir("OfflineAICamera")
                    dir?.deleteRecursively()
                } catch (e: Exception) {
                    Log.e(TAG, "Error bulk clearing screen files: ${e.message}")
                }
            }
            repository.clearHistory()
            _selectedPhoto.value = null
        }
    }

    /**
     * Intercepts captured ImageProxy directly, performs fully offline AI pipeline,
     * saves files, and populates SQLite history database.
     */
    fun processAndSaveCapturedImage(context: Context, imageProxy: ImageProxy) {
        _isProcessing.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Initialize model context once if needed
                SceneDetector.init(context)

                // Convert ImageProxy to standard Bitmap and rotate properly based on EXIF parameters
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                val buffer = imageProxy.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                
                val correctedBitmap = if (rotationDegrees != 0) {
                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                } else {
                    rawBitmap
                }
                
                imageProxy.close() // Close proxy promptly to allow CameraX buffer reuse

                // 2. Execute highly parallel Post Processing pipeline
                val result = PostProcessingPipeline.process(
                    inputBitmap = correctedBitmap,
                    applyHdr = _hdrEnabled.value,
                    applyPortraitBlur = _portraitModeEnabled.value,
                    blurScale = _blurRadius.value,
                    applyLiveFilterId = _activeFilter.value
                )

                // 3. Save "Before" (uncorrected) and "After" (enhanced) representations on storage
                val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
                val storageDir = context.getExternalFilesDir("OfflineAICamera") ?: context.filesDir
                if (!storageDir.exists()) storageDir.mkdirs()

                val beforeFile = File(storageDir, "IMG_BEFORE_$timestampStr.jpg")
                val afterFile = File(storageDir, "IMG_AFTER_$timestampStr.jpg")

                withContext(Dispatchers.IO) {
                    FileOutputStream(beforeFile).use { out ->
                        correctedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    FileOutputStream(afterFile).use { out ->
                        result.outputBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                }

                // 4. Save entity row to local Room database
                val photoEntity = CapturedPhoto(
                    filePath = beforeFile.absolutePath,
                    enhancedFilePath = afterFile.absolutePath,
                    sceneType = result.sceneType,
                    faceCount = result.faceCount,
                    brightness = result.avgBrightness,
                    contrast = result.avgContrast,
                    isEnhanced = true,
                    appliedFilters = buildString {
                        if (_hdrEnabled.value) append("HDR")
                        if (_activeFilter.value != "none") {
                            if (isNotEmpty()) append(", ")
                            append(_activeFilter.value.replaceFirstChar { it.uppercase() })
                        }
                    },
                    processingTimeMs = result.processingTimeMs,
                    timestamp = System.currentTimeMillis()
                )

                val primaryId = repository.insertPhoto(photoEntity)
                val insertedPhoto = photoEntity.copy(id = primaryId)

                // Open this newly captured image in comparison view immediately
                _selectedPhoto.value = insertedPhoto
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in image capture pipeline: ${e.message}", e)
            } finally {
                _isProcessing.value = false
            }
        }
    }
}

// Custom simple provider factory to make injection seamless and stable!
class CameraViewModelFactory(private val repository: PhotoRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CameraViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CameraViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
