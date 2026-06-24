package com.example.domain.processor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.FileInputStream
import java.nio.channels.FileChannel

object SceneDetector {
    private const val TAG = "SceneDetector"
    private var interpreter: Any? = null

    /**
     * Initialize TensorFlow Lite Interpreter gracefully using safe Reflection compiles
     * Bypasses strict transitive double-bindings inside cloud builds.
     */
    fun init(context: Context) {
        if (interpreter != null) return
        try {
            val modelFile = context.assets.openFd("mobilenet.tflite")
            val stream = FileInputStream(modelFile.fileDescriptor)
            val fileChannel = stream.channel
            val startOffset = modelFile.startOffset
            val declaredLength = modelFile.declaredLength
            val buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            
            val interpreterClass = Class.forName("org.tensorflow.lite.Interpreter")
            val optionsClass = Class.forName("org.tensorflow.lite.Interpreter\$Options")
            val optionsInstance = optionsClass.getConstructor().newInstance()
            
            try {
                val setNumThreads = optionsClass.getMethod("setNumThreads", Int::class.javaPrimitiveType)
                setNumThreads.invoke(optionsInstance, 4)
                val setUseNNAPI = optionsClass.getMethod("setUseNNAPI", Boolean::class.javaPrimitiveType)
                setUseNNAPI.invoke(optionsInstance, true)
            } catch (ignored: Exception) {
                Log.d(TAG, "Dynamic options parameters skipped: ${ignored.message}")
            }
            
            val constructor = interpreterClass.getConstructor(java.nio.ByteBuffer::class.java, optionsClass)
            interpreter = constructor.newInstance(buffer, optionsInstance)
            Log.d(TAG, "TensorFlow Lite MobileNet scene detector loaded successfully via Reflection")
        } catch (e: Exception) {
            Log.e(TAG, "TFLite loading bypassed. Loading Rule-Based Pixel Analyst: ${e.message}")
            interpreter = null
        }
    }

    fun detectScene(bitmap: Bitmap, faceCount: Int = 0): String {
        val currentInterpreter = interpreter
        if (currentInterpreter != null) {
            try {
                // Resize to MobileNet input dimensions (224 x 224, 3 channels)
                val resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
                val inputBuffer = ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4).apply {
                    order(ByteOrder.nativeOrder())
                }
                
                val intValues = IntArray(224 * 224)
                resized.getPixels(intValues, 0, 224, 0, 0, 224, 224)
                
                inputBuffer.rewind()
                for (pixelValue in intValues) {
                    inputBuffer.putFloat(((pixelValue shr 16 and 0xFF) - 127.5f) / 127.5f)
                    inputBuffer.putFloat(((pixelValue shr 8 and 0xFF) - 127.5f) / 127.5f)
                    inputBuffer.putFloat(((pixelValue and 0xFF) - 127.5f) / 127.5f)
                }

                // 2D output array for 1001 class probabilities
                val outputMap = Array(1) { FloatArray(1001) }
                
                val runMethod = currentInterpreter.javaClass.getMethod("run", Any::class.java, Any::class.java)
                runMethod.invoke(currentInterpreter, inputBuffer, outputMap)
                
                return resolveSceneFromClassifier(outputMap[0], faceCount, bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "TFLite execution failed, falling back to analytic pixel classifier: ${e.message}")
            }
        }

        // Option 3: Rule-Based chromatic contrast scene mapping
        return runPixelSceneAnalysis(bitmap, faceCount)
    }

    private fun resolveSceneFromClassifier(probabilities: FloatArray, faceCount: Int, bitmap: Bitmap): String {
        if (faceCount > 0) return "Person"
        
        var maxIdx = -1
        var maxVal = -1.0f
        for (i in probabilities.indices) {
            if (probabilities[i] > maxVal) {
                maxVal = probabilities[i]
                maxIdx = i
            }
        }

        // MobileNet standard categories mapping expanded for specific object/environment detection
        return when (maxIdx) {
            in 281..285 -> "Cat"
            in 151..268 -> "Dog"
            in listOf(407, 436, 468, 511, 627, 656, 661, 751, 817, 864, 675) -> "Car"
            971 -> "Beach" // Sandbar / beach
            972, 973, 974 -> "Landscape"
            970, in 975..980 -> {
                // Determine Forest or Tree or Landscape using green levels
                val chroma = runPixelSceneAnalysis(bitmap, faceCount)
                if (chroma == "Forest" || chroma == "Tree") chroma else "Landscape"
            }
            in 927..961 -> "Food"
            in listOf(518, 618, 917, 921) -> "Document"
            else -> {
                // If the classifier returns generic outdoors/indoors, let's refine using pixel spectrum
                val pixelClass = runPixelSceneAnalysis(bitmap, faceCount)
                if (pixelClass in listOf("Beach", "Forest", "Tree", "City Street", "Cat", "Dog", "Car")) {
                    pixelClass
                } else {
                    if (maxIdx % 2 == 0) "Outdoor" else "Indoor"
                }
            }
        }
    }

    private fun runPixelSceneAnalysis(bitmap: Bitmap, faceCount: Int): String {
        if (faceCount > 0) return "Person"

        val width = bitmap.width
        val height = bitmap.height
        
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        
        var greenFoliageCount = 0
        var oceanBlueCount = 0
        var goldenSandCount = 0
        var concreteGrayCount = 0
        var furWarmCount = 0
        var inkBlackCount = 0
        var count = 0
        
        val step = 16
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val color = bitmap.getPixel(x, y)
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                
                sumR += r
                sumG += g
                sumB += b
                count++
                
                // Forest/Tree green signature (G is clearly dominant)
                if (g > r + 12 && g > b + 12) {
                    greenFoliageCount++
                }
                
                // Ocean/Sky blue signature (B is clearly dominant)
                if (b > r + 15 && b > g + 8) {
                    oceanBlueCount++
                }
                
                // Golden sand or sunset warm signature (R & G are high, B is low)
                if (r > 160 && g > 140 && b < 100) {
                    goldenSandCount++
                }
                
                // Asphalt / Building gray signature (R, G, B are tightly bunched)
                val maxVal = maxOf(r, maxOf(g, b))
                val minVal = minOf(r, minOf(g, b))
                if ((maxVal - minVal) < 15 && maxVal > 60 && maxVal < 190) {
                    concreteGrayCount++
                }
                
                // Fur tones (Rich warm brown, tan, amber)
                if (r > g + 25 && g > b + 15 && r > 110 && r < 210) {
                    furWarmCount++
                }
                
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                if (lum < 55) {
                    inkBlackCount++
                }
            }
        }
        
        if (count == 0) return "Outdoor"
        
        val pctGreen = greenFoliageCount.toFloat() / count
        val pctOcean = oceanBlueCount.toFloat() / count
        val pctSand = goldenSandCount.toFloat() / count
        val pctConcrete = concreteGrayCount.toFloat() / count
        val pctFur = furWarmCount.toFloat() / count
        val pctInk = inkBlackCount.toFloat() / count
        
        val avgR = sumR / count
        val avgG = sumG / count
        val avgB = sumB / count
        val avgLuminance = (avgR + avgG + avgB) / 3
        
        // 1. High Contrast Document Detection
        if (pctInk > 0.15f && avgR > 185 && avgG > 185 && avgB > 185) {
            return "Document"
        }
        
        // 2. Beach Detection (High water blue and sand gold)
        if (pctOcean > 0.15f && (pctSand > 0.08f || avgR > 140 && avgG > 130 && avgB < 110)) {
            return "Beach"
        }
        
        // 3. Forest & Tree Detection
        if (pctGreen > 0.25f) {
            return "Forest"
        } else if (pctGreen > 0.12f) {
            return "Tree"
        }
        
        // 4. City Street Detection (High gray concrete density, outdoor brightness)
        if (pctConcrete > 0.25f && avgLuminance > 90) {
            return "City Street"
        }
        
        // 5. Food Detection (Golden rich saturation on plate focus)
        if (pctSand > 0.15f && avgR > avgB + 35) {
            return "Food"
        }
        
        // 6. Generic Pets/Animals if no specific classification
        if (pctFur > 0.20f) {
            return "Pet"
        }

        return when {
            avgLuminance < 80 -> "Indoor"
            avgLuminance > 180 -> "Outdoor"
            else -> "Outdoor"
        }
    }
}
