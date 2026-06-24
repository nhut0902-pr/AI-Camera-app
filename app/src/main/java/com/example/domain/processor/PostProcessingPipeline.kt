package com.example.domain.processor

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.system.measureTimeMillis

data class ProcessedResult(
    val outputBitmap: Bitmap,
    val sceneType: String,
    val faceCount: Int,
    val avgBrightness: Float,
    val avgContrast: Float,
    val processingTimeMs: Long
)

object PostProcessingPipeline {
    private const val TAG = "PostProcessingPipeline"

    /**
     * Complete Offline High-Speed Image Processing Pipeline
     * Passes raw captured image through all stages:
     * Face detection -> Scene Classification -> Brightness correction -> White balance -> HDR enhancement -> Portrait filters
     */
    fun process(
        inputBitmap: Bitmap,
        applyHdr: Boolean = true,
        applyPortraitBlur: Boolean = false,
        blurScale: Int = 12,
        applyLiveFilterId: String = "none"
    ): ProcessedResult {
        var currentBitmap = inputBitmap
        var faceCount = 0
        var sceneType = "Generic"
        var elapsed = 0L
        
        elapsed = measureTimeMillis {
            // Stage 1: Face Detection & Face Area Local Tuning
            val facePair = FaceDetector.processFaces(currentBitmap)
            currentBitmap = facePair.first
            faceCount = facePair.second
            
            // Stage 2: AI Scene Detection (TFLite MobileNet core)
            sceneType = SceneDetector.detectScene(currentBitmap, faceCount)
            Log.d(TAG, "Pipeline Scene Tag resolved: $sceneType")
            
            // Stage 3: Auto Brightness Correction
            currentBitmap = OpenCvEnhancer.autoBrightness(currentBitmap)
            
            // Stage 4: Auto White Balance
            currentBitmap = OpenCvEnhancer.autoWhiteCBalance(currentBitmap)
            
            // Stage 5: Contrast Correction
            currentBitmap = OpenCvEnhancer.autoContrast(currentBitmap)
            
            // Stage 6: Direct Scene-Based Optimization Custom Filters
            currentBitmap = OpenCvEnhancer.colorEnhancement(currentBitmap, sceneType)
            
            // Stage 7: Smart HDR Style Processing (If requested or scene warrants details pop)
            if (applyHdr || sceneType == "Landscape" || sceneType == "Vehicle" || sceneType == "Beach") {
                currentBitmap = OpenCvEnhancer.smartHdrStyle(currentBitmap)
            }
            
            // Stage 8: Portrait DSLR Blur Processing
            // Automatically trigger portrait blur if user toggles Portrait OR scene is Person with active face profiles
            if (applyPortraitBlur || (faceCount > 0 && sceneType == "Person")) {
                currentBitmap = PortraitSegmenter.applyPortraitBlur(
                    bitmap = currentBitmap,
                    blurRadius = blurScale,
                    faceCount = faceCount
                )
            }

            // Stage 9: Live AI Filters and Real-time Effects synchronization
            currentBitmap = applyRealTimeFilter(currentBitmap, applyLiveFilterId)
        }
        
        // Analyze final brightness/contrast markers for metadata saving
        val stats = calcBitmapStats(currentBitmap)
        
        return ProcessedResult(
            outputBitmap = currentBitmap,
            sceneType = sceneType,
            faceCount = faceCount,
            avgBrightness = stats.first,
            avgContrast = stats.second,
            processingTimeMs = elapsed
        )
    }

    /**
     * Applies identical high-fidelity real-time color grading or beautification filters to captured media.
     */
    private fun applyRealTimeFilter(bitmap: Bitmap, filterId: String): Bitmap {
        if (filterId == "none" || filterId.isEmpty()) return bitmap
        
        val matrixValues = when (filterId) {
            "cyberpunk" -> floatArrayOf(
                1.30f, 0.15f, -0.25f, 0f, 15f,
                -0.15f, 0.95f, 0.35f, 0f, -10f,
                0.15f, -0.45f, 1.70f, 0f, 30f,
                0f, 0f, 0f, 1f, 0f
            )
            "beautify" -> floatArrayOf(
                1.08f, 0f, 0f, 0f, 12f,
                0f, 1.08f, 0f, 0f, 12f,
                0f, 0f, 1.08f, 0f, 12f,
                0f, 0f, 0f, 1f, 0f
            )
            "noir" -> floatArrayOf(
                0.33f, 0.59f, 0.11f, 0f, -5f,
                0.33f, 0.59f, 0.11f, 0f, -5f,
                0.33f, 0.59f, 0.11f, 0f, -5f,
                0f, 0f, 0f, 1f, 0f
            )
            "vintage" -> floatArrayOf(
                1.12f, 0.05f, -0.05f, 0f, 14f,
                0.05f, 1.07f, -0.05f, 0f, 8f,
                -0.12f, -0.12f, 0.82f, 0f, -14f,
                0f, 0f, 0f, 1f, 0f
            )
            "teal_orange" -> floatArrayOf(
                1.18f, 0f, 0f, 0f, 18f,
                0f, 0.92f, 0f, 0f, -6f,
                0f, 0f, 1.15f, 0f, 22f,
                0f, 0f, 0f, 1f, 0f
            )
            else -> return bitmap
        }
        
        // Let's first smooth the skin for beautification
        var processed = bitmap
        if (filterId == "beautify") {
            processed = OpenCvEnhancer.nightDenoise(bitmap)
        }
        
        val output = Bitmap.createBitmap(processed.width, processed.height, processed.config ?: Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint()
        val matrix = android.graphics.ColorMatrix(matrixValues)
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(processed, 0f, 0f, paint)
        return output
    }
    
    // Quick helper to evaluate brightness and contrast metrics
    private fun calcBitmapStats(bitmap: Bitmap): Pair<Float, Float> {
        val w = bitmap.width
        val h = bitmap.height
        val step = 16
        var sumLum = 0f
        var count = 0
        
        // Compute mean
        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val pix = bitmap.getPixel(x, y)
                val lum = (0.299f * Color.red(pix) + 0.587f * Color.green(pix) + 0.114f * Color.blue(pix)) / 255f
                sumLum += lum
                count++
            }
        }
        
        val avgL = if (count > 0) sumLum / count else 0.5f
        
        // Compute variance as Contrast
        var sumVar = 0f
        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val pix = bitmap.getPixel(x, y)
                val lum = (0.299f * Color.red(pix) + 0.587f * Color.green(pix) + 0.114f * Color.blue(pix)) / 255f
                val diff = lum - avgL
                sumVar += diff * diff
            }
        }
        
        val avgVar = if (count > 0) Math.sqrt((sumVar / count).toDouble()).toFloat() else 0.2f
        return Pair(avgL, avgVar)
    }
}
