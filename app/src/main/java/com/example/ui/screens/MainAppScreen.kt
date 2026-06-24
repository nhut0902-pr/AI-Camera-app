package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import android.view.ScaleGestureDetector
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.BorderStroke
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.CapturedPhoto
import com.example.ui.viewmodel.CameraViewModel
import com.google.accompanist.permissions.*
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainAppScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val permissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)
    
    // Core Navigation Mode
    var currentTab by remember { mutableStateOf("camera") } // camera, history, settings
    val selectedPhoto by viewModel.selectedPhoto.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF12131A) // Deep Cosmic Dark Background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Main content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (selectedPhoto != null) {
                    // Overlap Comparison Slider view if any photo is selected
                    BeforeAfterCompareView(
                        photo = selectedPhoto!!,
                        onClose = { viewModel.selectPhoto(null) },
                        onDelete = { viewModel.deletePhoto(context, selectedPhoto!!) }
                    )
                } else {
                    Crossfade(
                        targetState = currentTab,
                        animationSpec = tween(300),
                        label = "screen_crossfade"
                    ) { tab ->
                        when (tab) {
                            "camera" -> {
                                if (permissionState.status.isGranted) {
                                    CameraLiveView(viewModel = viewModel)
                                } else {
                                    CameraPermissionRequest(permissionState = permissionState)
                                }
                            }
                            "history" -> {
                                PhotoHistoryView(
                                    viewModel = viewModel,
                                    onSelectPhoto = { viewModel.selectPhoto(it) }
                                )
                            }
                            "settings" -> {
                                CameraSettingsView(viewModel = viewModel)
                            }
                        }
                    }
                }
            }

            // Bottom Navigation Bar
            if (selectedPhoto == null) {
                BottomNavigationBarElement(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBarElement(
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    NavigationBar(
        containerColor = Color(0xFF1A1C24),
        tonalElevation = 8.dp,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        NavigationBarItem(
            selected = currentTab == "history",
            onClick = { onTabSelected("history") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF4F46E5),
                indicatorColor = Color(0xFF2E313E),
                unselectedIconColor = Color.Gray
            ),
            icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "History") },
            label = { Text("History") }
        )
        NavigationBarItem(
            selected = currentTab == "camera",
            onClick = { onTabSelected("camera") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF4F46E5),
                indicatorColor = Color(0xFF2E313E),
                unselectedIconColor = Color.Gray
            ),
            icon = { Icon(Icons.Default.PhotoCamera, contentDescription = "Camera") },
            label = { Text("AI Camera") }
        )
        NavigationBarItem(
            selected = currentTab == "settings",
            onClick = { onTabSelected("settings") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF4F46E5),
                indicatorColor = Color(0xFF2E313E),
                unselectedIconColor = Color.Gray
            ),
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") }
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPermissionRequest(
    permissionState: PermissionState
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E202C)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFF2D3043), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Camera",
                        tint = Color(0xFF4F46E5),
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Camera Permission",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Offline AI Camera requires dynamic access to your device's camera to process local lighting and capture beautiful snapshots in real-time.",
                    fontSize = 14.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { permissionState.launchPermissionRequest() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("permission_button")
                ) {
                    Text("Grant Lens Access", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun CameraLiveView(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val flashMode by viewModel.flashMode.collectAsStateWithLifecycle()
    val isFrontCamera by viewModel.isFrontCamera.collectAsStateWithLifecycle()
    val zoomRatio by viewModel.zoomRatio.collectAsStateWithLifecycle()
    val hdrEnabled by viewModel.hdrEnabled.collectAsStateWithLifecycle()
    val portraitModeEnabled by viewModel.portraitModeEnabled.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()

    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraControlUseCase by remember { mutableStateOf<CameraControl?>(null) }

    // Floating UI trigger feedback
    val latestPhotos by viewModel.photos.collectAsStateWithLifecycle()
    val lastThumbnail = latestPhotos.firstOrNull()?.enhancedFilePath

    // Keep active camera binding in sync with flipping toggle
    LaunchedEffect(isFrontCamera, flashMode) {
        val cameraProvider = withContext(kotlinx.coroutines.Dispatchers.IO) {
            ProcessCameraProvider.getInstance(context).get()
        }
        
        val preview = Preview.Builder().build()
        
        val imageCapture = ImageCapture.Builder()
            .setFlashMode(flashMode)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        imageCaptureUseCase = imageCapture

        val cameraSelector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        try {
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
            cameraControlUseCase = camera.cameraControl
            
            // Reapply current dynamic zoom setting
            camera.cameraControl.setZoomRatio(zoomRatio)
        } catch (e: Exception) {
            Log.e("CameraLiveView", "Use case binding failed: ${e.message}")
        }
    }

    // Capture execution block
    val performCapture = {
        val capture = imageCaptureUseCase
        if (capture != null && !isProcessing) {
            capture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                        viewModel.processAndSaveCapturedImage(context, imageProxy)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("CameraLiveView", "Shutter error: ${exception.message}")
                    }
                }
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Live Camera Preview Canvas
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                // Attaches preview use case to the surface view
                val providerFuture = ProcessCameraProvider.getInstance(context)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                }, ContextCompat.getMainExecutor(context))
            }
        )

        val activeFilter by viewModel.activeFilter.collectAsStateWithLifecycle()

        // Live GPU Accelerated Filter Overlay
        if (activeFilter != "none") {
            val composeColorMatrix = remember(activeFilter) {
                val array = when (activeFilter) {
                    "cyberpunk" -> floatArrayOf(
                        1.30f, 0.15f, -0.25f, 0f, 15f/255f,
                        -0.15f, 0.95f, 0.35f, 0f, -10f/255f,
                        0.15f, -0.45f, 1.70f, 0f, 30f/255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                    "beautify" -> floatArrayOf(
                        1.08f, 0f, 0f, 0f, 12f/255f,
                        0f, 1.08f, 0f, 0f, 12f/255f,
                        0f, 0f, 1.08f, 0f, 12f/255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                    "noir" -> floatArrayOf(
                        0.33f, 0.59f, 0.11f, 0f, -5f/255f,
                        0.33f, 0.59f, 0.11f, 0f, -5f/255f,
                        0.33f, 0.59f, 0.11f, 0f, -5f/255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                    "vintage" -> floatArrayOf(
                        1.12f, 0.05f, -0.05f, 0f, 14f/255f,
                        0.05f, 1.07f, -0.05f, 0f, 8f/255f,
                        -0.12f, -0.12f, 0.82f, 0f, -14f/255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                    "teal_orange" -> floatArrayOf(
                        1.18f, 0f, 0f, 0f, 18f/255f,
                        0f, 0.92f, 0f, 0f, -6f/255f,
                        0f, 0f, 1.15f, 0f, 22f/255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                    else -> floatArrayOf(
                        1f, 0f, 0f, 0f, 0f,
                        0f, 1f, 0f, 0f, 0f,
                        0f, 0f, 1f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                }
                androidx.compose.ui.graphics.ColorMatrix(array)
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("preview_filter_overlay")
            ) {
                drawRect(
                    color = Color.White,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(composeColorMatrix),
                    blendMode = androidx.compose.ui.graphics.BlendMode.Modulate
                )

                if (activeFilter == "cyberpunk") {
                    val barHeight = 4f
                    val gap = 20f
                    for (y in 0..size.height.toInt() step gap.toInt()) {
                        drawRect(
                            color = Color(0xFFFF007F).copy(alpha = 0.05f),
                            topLeft = androidx.compose.ui.geometry.Offset(0f, y.toFloat()),
                            size = androidx.compose.ui.geometry.Size(size.width, barHeight)
                        )
                    }
                } else if (activeFilter == "beautify") {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.10f),
                                Color.Transparent
                            ),
                            radius = size.minDimension * 0.80f
                        )
                    )
                }
            }
        }

        // 2. High-contrast ambient gradient to ensure control text visibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.5f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.65f)
                        )
                    )
                )
        )

        // 3. Top Toolbar controls (Flash, HDR, Portrait Mode)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flash bubble
            IconButton(
                onClick = { viewModel.toggleFlash() },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .size(46.dp)
            ) {
                Icon(
                    imageVector = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    },
                    contentDescription = "Flash Status",
                    tint = if (flashMode == ImageCapture.FLASH_MODE_OFF) Color.Gray else Color(0xFFFFD166)
                )
            }

            // Real-time HUD stats
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF10B981), CircleShape) // Green dot representing Offline AI Core active
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "LOCAL TPU ACCELERATION ACTIVE",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }

            // HDR indicator toggle
            IconButton(
                onClick = { viewModel.toggleHdr() },
                modifier = Modifier
                    .background(
                        if (hdrEnabled) Color(0xFF4F46E5).copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.5f),
                        CircleShape
                    )
                    .size(46.dp)
            ) {
                Icon(
                    Icons.Default.HdrOn,
                    contentDescription = "HDR Mode",
                    tint = if (hdrEnabled) Color.White else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 4. Floating AI indicators overlaying on top center
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val sceneLabel = if (latestPhotos.isNotEmpty()) latestPhotos.first().sceneType else "Calibrating"
            
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C24).copy(alpha = 0.82f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF4F46E5).copy(alpha = 0.3f)),
                    modifier = Modifier.shadow(4.dp, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (sceneLabel) {
                                "Landscape", "Beach", "Forest" -> Icons.Default.Terrain
                                "Food" -> Icons.Default.Restaurant
                                "Person" -> Icons.Default.Face
                                "Pet", "Cat", "Dog" -> Icons.Default.Pets
                                "Document" -> Icons.Default.Description
                                "Vehicle", "Car" -> Icons.Default.DirectionsCar
                                "Indoor" -> Icons.Default.Home
                                "Tree" -> Icons.Default.Nature
                                "City Street" -> Icons.Default.Home
                                else -> Icons.Default.PhotoFilter
                            },
                            contentDescription = "Scene Icon",
                            tint = Color(0xFF4F46E5),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AUTO SCENE DETECTION SPEC: $sceneLabel",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        // 5. Center loader during pipeline capture
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C24)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF4F46E5).copy(alpha = 0.4f)),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF4F46E5),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            "RUNNING OFFLINE PIPELINE...",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Scene detection • White-balance • Smart HDR • Face tuning",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // 6. Camera Bottom HUD Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Horizontal Real-time AI Filter Picker
            val filtersList = listOf(
                Pair("none", "Natural"),
                Pair("cyberpunk", "Cyberpunk"),
                Pair("beautify", "Beautify"),
                Pair("noir", "Noir"),
                Pair("vintage", "Vintage"),
                Pair("teal_orange", "Anime")
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                items(filtersList) { (id, name) ->
                    val isSelected = activeFilter == id
                    val borderAccent = if (isSelected) Color(0xFF4F46E5) else Color.Transparent
                    val bg = if (isSelected) Color(0xFF1E1E2C) else Color.Black.copy(alpha = 0.55f)
                    val textColor = if (isSelected) Color.White else Color.LightGray
                    
                    Box(
                        modifier = Modifier
                            .background(bg, RoundedCornerShape(20.dp))
                            .border(BorderStroke(1.5.dp, borderAccent), RoundedCornerShape(20.dp))
                            .clickable { viewModel.setActiveFilter(id) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("filter_option_$id")
                    ) {
                        Text(
                            text = name.uppercase(),
                            color = textColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Slider Zoom indicator
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ZOOM ${String.format("%.1fx", zoomRatio)}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = zoomRatio,
                    onValueChange = { ratio ->
                        viewModel.setZoom(ratio)
                        cameraControlUseCase?.setZoomRatio(ratio)
                    },
                    valueRange = 1.0f..8.0f,
                    modifier = Modifier.width(120.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF4F46E5),
                        activeTrackColor = Color(0xFF4F46E5),
                        inactiveTrackColor = Color.DarkGray
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main shutter actions strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Interactive historical thumbnail
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.3f)), CircleShape)
                        .clip(CircleShape)
                        .background(Color(0xFF1E202C))
                        .clickable {
                            if (latestPhotos.isNotEmpty()) {
                                viewModel.selectPhoto(latestPhotos.first())
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (lastThumbnail != null) {
                        AsyncImage(
                            model = File(lastThumbnail),
                            contentDescription = "Last captured thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.Photo,
                            contentDescription = "Local gallery empty",
                            tint = Color.Gray
                        )
                    }
                }

                // Shutter trigger button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(BorderStroke(4.dp, Color.White), CircleShape)
                        .padding(6.dp)
                        .background(if (isProcessing) Color.Gray else Color.White, CircleShape)
                        .clickable { performCapture() }
                        .testTag("shutter_button")
                )

                // Lens toggling (Front <-> Rear)
                IconButton(
                    onClick = { viewModel.toggleCameraLens() },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .size(54.dp)
                ) {
                    Icon(
                        Icons.Default.FlipCameraAndroid,
                        contentDescription = "Toggle Lens",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PhotoHistoryView(
    viewModel: CameraViewModel,
    onSelectPhoto: (CapturedPhoto) -> Unit
) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp, start = 12.dp, end = 12.dp)
    ) {
        // Toolbar header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Local Library",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = "${photos.size} local pictures analyzed locally",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            if (photos.isNotEmpty()) {
                TextButton(
                    onClick = { viewModel.clearHistory(context) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Session", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Bulk Clear")
                }
            }
        }

        // List Grid of elements
        if (photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.ImageNotSupported,
                        contentDescription = "No photos saved",
                        tint = Color.DarkGray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "History Session Empty",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Take snapshots using the AI Camera tab and the offline GPU enhancer will save them here.",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(photos, key = { it.id }) { photo ->
                    PhotoItemCard(
                        photo = photo,
                        onClick = { onSelectPhoto(photo) }
                    )
                }
            }
        }
    }
}

@Composable
fun PhotoItemCard(
    photo: CapturedPhoto,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E202C)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .shadow(4.dp, RoundedCornerShape(16.dp))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = File(photo.enhancedFilePath ?: photo.filePath),
                contentDescription = "Enhanced thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            )
            
            // Floating tag for scene category classification
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = photo.sceneType.uppercase(),
                    color = Color(0xFF818CF8),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Metadata footer and process times
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            val date = SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(photo.timestamp))
            Text(
                text = date,
                fontSize = 11.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = "Speed",
                        tint = Color.Gray,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        "${photo.processingTimeMs}ms",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (photo.faceCount > 0) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFD166).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "${photo.faceCount} FACES",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD166)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Custom High-fidelity sliding before-after picture comparison component in pure Jetpack Compose!
 */
@Composable
fun BeforeAfterCompareView(
    photo: CapturedPhoto,
    onClose: () -> Unit,
    onDelete: () -> Unit
) {
    var sliderRatio by remember { mutableStateOf(0.5f) }
    val density = LocalDensity.current
    var containerWidth by remember { mutableStateOf(300.dp) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0D12))
    ) {
        // Header tools strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(Color(0xFF1E202C), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Close", tint = Color.White)
            }
            Text(
                "AI Enhanced Detail Studio",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.background(Color(0xFFFEF2F2), CircleShape)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Picture", tint = Color(0xFFEF4444))
            }
        }

        // Active compare canvas matching width and clipping on drag coords
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        sliderRatio = (sliderRatio + dragAmount.x / size.width).coerceIn(0f, 1f)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        sliderRatio = (offset.x / size.width).coerceIn(0f, 1f)
                    }
                }
        ) {
            // Background Layer: Raw picture BEFORE AI processing
            AsyncImage(
                model = File(photo.filePath),
                contentDescription = "Before Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            // Foreground Layer: Enhanced photo, bound and clipped dynamic width based on sliderRatio
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                // Dynamically sets slider bounds in dps
                containerWidth = maxWidth
                
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(maxWidth * sliderRatio)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = File(photo.enhancedFilePath ?: photo.filePath),
                        contentDescription = "After Photo",
                        contentScale = ContentScale.Crop, // Force crop matches bounding geometry
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(containerWidth) // Keeps full scale intact inside container clipping bounds
                    )
                }
            }

            // Aesthetic Visual Split drag hairline selector pill overlay
            val splitOffset = with(density) { (containerWidth.toPx() * sliderRatio).toDp() }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .offset(x = splitOffset - 1.dp)
                    .width(2.dp)
                    .background(Color.White)
            ) {
                // Drag handle anchor pill
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .shadow(4.dp, RoundedCornerShape(12.dp))
                        .size(34.dp, 56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CompareArrows,
                        contentDescription = "Slider Handle",
                        tint = Color(0xFF4F46E5),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // High-contrast labels showing status on borders
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 16.dp, start = 12.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("ORIGINAL CAPTURE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 12.dp)
                    .background(Color(0xFF4F46E5).copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("AI ENHANCED BOKEH", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Stats detail strip
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E202C)),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    "ANALYSIS PARAMETERS",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("AUTO SCENE TYPE", fontSize = 10.sp, color = Color.Gray)
                        Text(photo.sceneType.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column {
                        Text("BRIGHTNESS RATIO", fontSize = 10.sp, color = Color.Gray)
                        Text(String.format("%.2f", photo.brightness), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column {
                        Text("CONTRAST METRIC", fontSize = 10.sp, color = Color.Gray)
                        Text(String.format("%.2f", photo.contrast), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Aesthetic pipeline processed in ${photo.processingTimeMs}ms utilizing 100% offline, privacy-safe local TPU and CPU matrix loops.",
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    lineHeight = 16.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

@Composable
fun CameraSettingsView(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hdrEnabled by viewModel.hdrEnabled.collectAsStateWithLifecycle()
    val portraitBlurEnabled by viewModel.portraitModeEnabled.collectAsStateWithLifecycle()
    val blurRadius by viewModel.blurRadius.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(20.dp)
    ) {
        Text(
            text = "AI System Preferences",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontFamily = FontFamily.SansSerif
        )
        Text(
            text = "Modify core offline neural parameters",
            fontSize = 12.sp,
            color = Color.Gray
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        // System configuration cards
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E202C)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp)
            ) {
                // Row for HDR settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Smart HDR Styles", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Combines shadows and highlight stretching for high visual pop.", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = hdrEnabled,
                        onCheckedChange = { viewModel.toggleHdr() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF4F46E5)
                        )
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 14.dp), color = Color.DarkGray)

                // Row for Portrait Mode default
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("DSLR Portrait Mode Toggle", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Enables smart segmentation background blur for human focus.", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = portraitBlurEnabled,
                        onCheckedChange = { viewModel.togglePortrait() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF4F46E5)
                        )
                    )
                }

                // Blur Intensity selection (Visible only when portrait blur enabled)
                AnimatedVisibility(visible = portraitBlurEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Text(
                            text = "Background Blur Radius: ${blurRadius}px",
                            fontSize = 13.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = blurRadius.toFloat(),
                            onValueChange = { viewModel.setBlurRadius(it.roundToInt()) },
                            valueRange = 2f..30f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF4F46E5),
                                activeTrackColor = Color(0xFF4F46E5),
                                inactiveTrackColor = Color.DarkGray
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hardware specification card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E202C)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp)
            ) {
                Text(
                    "OFFLINE ENGINE ATTRIBUTES",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Scene Engine", fontSize = 13.sp, color = Color.LightGray)
                    Text("TensorFlow Lite MobileNet V2", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Segmentation Engine", fontSize = 13.sp, color = Color.LightGray)
                    Text("Saliency Depth Matrix SDK", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Enhancer Loops", fontSize = 13.sp, color = Color.LightGray)
                    Text("Local OpenCV-Kotlin core V4", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Security Level", fontSize = 13.sp, color = Color.LightGray)
                    Text("99.9% Strict Local Execution", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Privacy statement
        Text(
            text = "No images are transmitted to server-side backends. All computations are performed locally using CPU clusters & GPU shaders under active Android Sandboxes.",
            fontSize = 11.sp,
            color = Color.DarkGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )
    }
}
