package com.example.domain.processor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance image processing engine implementing OpenCV-like matrix operations
 * and enhancement algorithms natively in optimized Kotlin.
 */
object OpenCvEnhancer {

    // 1. Auto Brightness Correction using Luminance Mapping
    fun autoBrightness(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        
        // Analyze average luminance
        var sumLuminance = 0L
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Sample every 4th pixel for speed
        val step = 4
        var count = 0
        for (i in pixels.indices step step) {
            val color = pixels[i]
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val luminance = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
            sumLuminance += luminance
            count++
        }
        
        val avgLuminance = if (count > 0) sumLuminance / count else 127
        val targetLuminance = 128 // Perfect middle-gray luminance
        val diff = targetLuminance - avgLuminance
        val brightnessOffset = diff.toFloat() * 0.4f // Restrained scale factor to keep natural lighting

        // Apply brightness offset via Canvas and ColorMatrix for extreme hardware speed
        val canvas = Canvas(output)
        val paint = Paint()
        val matrix = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, brightnessOffset,
            0f, 1f, 0f, 0f, brightnessOffset,
            0f, 0f, 1f, 0f, brightnessOffset,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    // 2. Auto Contrast Enhancement (Histogram Stretching)
    fun autoContrast(bitmap: Bitmap, lowClipPct: Float = 0.01f, highClipPct: Float = 0.99f): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Calculate mono-histogram
        val hist = IntArray(256)
        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val lum = ((0.299f * r + 0.587f * g + 0.114f * b).toInt()).coerceIn(0, 255)
            hist[lum]++
        }
        
        // Compute threshold boundaries based on clipping percentages
        val totalCount = pixels.size
        val lowThreshold = (totalCount * lowClipPct).toInt()
        val highThreshold = (totalCount * highClipPct).toInt()
        
        var minLum = 0
        var cumulative = 0
        for (i in 0..255) {
            cumulative += hist[i]
            if (cumulative >= lowThreshold) {
                minLum = i
                break
            }
        }
        
        var maxLum = 255
        cumulative = 0
        for (i in 255 downTo 0) {
            cumulative += hist[i]
            if (cumulative >= (totalCount - highThreshold)) {
                maxLum = i
                break
            }
        }
        
        if (maxLum <= minLum) {
            maxLum = 255
            minLum = 0
        }
        
        // Build contrast stretching lookup table
        val lut = IntArray(256)
        val range = (maxLum - minLum).toFloat()
        for (i in 0..255) {
            lut[i] = ((i - minLum) / range * 255f).toInt().coerceIn(0, 255)
        }
        
        val outputPixels = IntArray(width * height)
        for (i in pixels.indices) {
            val color = pixels[i]
            val a = Color.alpha(color)
            val r = lut[Color.red(color)]
            val g = lut[Color.green(color)]
            val b = lut[Color.blue(color)]
            outputPixels[i] = Color.argb(a, r, g, b)
        }
        
        output.setPixels(outputPixels, 0, width, 0, 0, width, height)
        return output
    }

    // 3. Auto White Balance using Gray World Algorithm
    fun autoWhiteCBalance(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        val step = 4
        var count = 0L
        
        for (i in pixels.indices step step) {
            val color = pixels[i]
            sumR += Color.red(color)
            sumG += Color.green(color)
            sumB += Color.blue(color)
            count++
        }
        
        if (count == 0L) return bitmap
        
        val avgR = sumR.toDouble() / count
        val avgG = sumG.toDouble() / count
        val avgB = sumB.toDouble() / count
        
        // Gray World assumption: AvgR = AvgG = AvgB = OverallAverage
        val grey = (avgR + avgG + avgB) / 3.0
        val scaleR = if (avgR > 0) (grey / avgR).toFloat() else 1.0f
        val scaleG = if (avgG > 0) (grey / avgG).toFloat() else 1.0f
        val scaleB = if (avgB > 0) (grey / avgB).toFloat() else 1.0f
        
        val canvas = Canvas(output)
        val paint = Paint()
        val matrix = ColorMatrix(floatArrayOf(
            scaleR, 0f, 0f, 0f, 0f,
            0f, scaleG, 0f, 0f, 0f,
            0f, 0f, scaleB, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    // 4. Smart HDR Style Enhancement (Shadow recovery & Unsharp Masking)
    fun smartHdrStyle(bitmap: Bitmap): Bitmap {
        // Brighten shadow regions & sharpen mid-contrast details
        val b1 = autoContrast(bitmap, 0.005f, 0.995f)
        val width = b1.width
        val height = b1.height
        val output = Bitmap.createBitmap(width, height, b1.config ?: Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        b1.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val resultPixels = IntArray(width * height)
        for (i in pixels.indices) {
            val color = pixels[i]
            val a = Color.alpha(color)
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            
            // Recover Shadows (if pixel is very dark, boost elements with non-linear curve)
            val lum = 0.299f * r + 0.587f * g + 0.114f * b
            val shadowBoost = if (lum < 100f) {
                // Adaptive weight: max boost at 0, tapering off towards 100
                (1.0f - (lum / 100f)) * 40f
            } else 0f
            
            // Recover Highlights (if pixel is very bright, dim slightly to reveal sky textures)
            val highlightDim = if (lum > 200f) {
                ((lum - 200f) / 55f) * -20f
            } else 0f
            
            val totalAdj = shadowBoost + highlightDim
            val rOut = (r + totalAdj).toInt().coerceIn(0, 255)
            val gOut = (g + totalAdj).toInt().coerceIn(0, 255)
            val bOut = (b + totalAdj).toInt().coerceIn(0, 255)
            
            resultPixels[i] = Color.argb(a, rOut, gOut, bOut)
        }
        
        output.setPixels(resultPixels, 0, width, 0, 0, width, height)
        // Combine with high-pass sharpening to create the tactile HDR detail feel
        return aiSharpen(output, intensity = 0.25f)
    }

    // 5. AI Sharpening (Laplacian edge blending)
    fun aiSharpen(bitmap: Bitmap, intensity: Float = 0.35f): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val result = IntArray(width * height)
        
        // Sharpen convolution kernel:
        // [ 0  -1   0 ]
        // [-1   5  -1 ]
        // [ 0  -1   0 ]
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val center = pixels[idx]
                val top = pixels[idx - width]
                val bottom = pixels[idx + width]
                val left = pixels[idx - 1]
                val right = pixels[idx + 1]
                
                // Red Channel
                val rC = Color.red(center)
                val rEdge = rC * 5 - Color.red(top) - Color.red(bottom) - Color.red(left) - Color.red(right)
                val rOut = (rC * (1f - intensity) + rEdge * intensity).toInt().coerceIn(0, 255)
                
                // Green Channel
                val gC = Color.green(center)
                val gEdge = gC * 5 - Color.green(top) - Color.green(bottom) - Color.green(left) - Color.green(right)
                val gOut = (gC * (1f - intensity) + gEdge * intensity).toInt().coerceIn(0, 255)
                
                // Blue Channel
                val bC = Color.blue(center)
                val bEdge = bC * 5 - Color.blue(top) - Color.blue(bottom) - Color.blue(left) - Color.blue(right)
                val bOut = (bC * (1f - intensity) + bEdge * intensity).toInt().coerceIn(0, 255)
                
                result[idx] = Color.argb(Color.alpha(center), rOut, gOut, bOut)
            }
        }
        
        // Fill border lines with original pixels to avoid black edges
        for (x in 0 until width) {
            result[x] = pixels[x]
            result[(height - 1) * width + x] = pixels[(height - 1) * width + x]
        }
        for (y in 0 until height) {
            result[y * width] = pixels[y * width]
            result[y * width + (width - 1)] = pixels[y * width + (width - 1)]
        }
        
        output.setPixels(result, 0, width, 0, 0, width, height)
        return output
    }

    // 6. Night Photo Denoising (Bilateral spatial adaptive filter)
    fun nightDenoise(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val result = IntArray(width * height)
        
        // Custom 3x3 Edge-preserving box filter
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val centerColor = pixels[idx]
                
                val cR = Color.red(centerColor)
                val cG = Color.green(centerColor)
                val cB = Color.blue(centerColor)
                
                var totalWeight = 0f
                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                
                // Iterate over 3x3 window
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val nIdx = (y + ky) * width + (x + kx)
                        val nColor = pixels[nIdx]
                        val nR = Color.red(nColor)
                        val nG = Color.green(nColor)
                        val nB = Color.blue(nColor)
                        
                        // Range weight based on color similarity
                        val rDiff = nR - cR
                        val gDiff = nG - cG
                        val bDiff = nB - cB
                        val colorDist = rDiff * rDiff + gDiff * gDiff + bDiff * bDiff
                        
                        // Max distance is 255*255*3. Set standard threshold
                        val similarityThreshold = 1800f
                        val weight = if (colorDist < similarityThreshold) {
                            1.0f - (colorDist / similarityThreshold)
                        } else 0.1f
                        
                        sumR += nR * weight
                        sumG += nG * weight
                        sumB += nB * weight
                        totalWeight += weight
                    }
                }
                
                val finalR = (sumR / totalWeight).toInt().coerceIn(0, 255)
                val finalG = (sumG / totalWeight).toInt().coerceIn(0, 255)
                val finalB = (sumB / totalWeight).toInt().coerceIn(0, 255)
                result[idx] = Color.argb(Color.alpha(centerColor), finalR, finalG, finalB)
            }
        }
        
        // Border copy
        for (x in 0 until width) {
            result[x] = pixels[x]
            result[(height - 1) * width + x] = pixels[(height - 1) * width + x]
        }
        for (y in 0 until height) {
            result[y * width] = pixels[y * width]
            result[y * width + (width - 1)] = pixels[y * width + (width - 1)]
        }
        
        output.setPixels(result, 0, width, 0, 0, width, height)
        return output
    }

    // 7. Dynamic Range Optimization (DRO)
    fun dynamicRangeOptimization(bitmap: Bitmap): Bitmap {
        // Equalize the image luminance without blowing details
        val brightened = autoBrightness(bitmap)
        return autoContrast(brightened, 0.015f, 0.985f)
    }

    // 8. Color Enhancement by Adjusting Saturation curves
    fun colorEnhancement(bitmap: Bitmap, sceneType: String): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        
        // Define color enhancement ratios based on the classified scene type
        val sat = when (sceneType) {
            "Landscape", "Forest" -> 1.50f  // Rich foliage and deep skies
            "Tree" -> 1.40f                 // Organic foliage definition
            "Beach" -> 1.45f                // Vivid sands and deep waters
            "Food" -> 1.35f                 // Savory, vibrant, and warm
            "Pet", "Cat", "Dog" -> 1.20f    // Soft animal fur warmth and detail
            "Person" -> 1.05f               // Protect skin-tone realism
            "Document" -> 0.70f             // High contrast, reduced colors to eliminate shadows/ink bleeds
            "Vehicle", "Car" -> 1.35f       // Metallic reflection sheen and deep highlights
            "City Street" -> 1.25f          // Cinematic metropolitan colors
            "Indoor" -> 1.15f               // Warm room light compensation
            "Outdoor" -> 1.25f              // Sunshine pop
            else -> 1.20f                   // Standard smart booster
        }
        
        val canv = Canvas(output)
        val paint = Paint()
        
        // Standard Material 3 color saturation matrices
        val matrix = ColorMatrix()
        matrix.setSaturation(sat)
        
        // Apply scene-specific temperature scaling
        if (sceneType == "Food" || sceneType == "Indoor" || sceneType == "Cat" || sceneType == "Dog") {
            // Give warmer amber tint
            val tempMatrix = ColorMatrix(floatArrayOf(
                1.06f, 0f, 0f, 0f, 6f,
                0f, 1.03f, 0f, 0f, 3f,
                0f, 0f, 0.94f, 0f, -6f,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(tempMatrix)
        } else if (sceneType == "Landscape" || sceneType == "Forest" || sceneType == "Tree") {
            // Boost greens/blues specifically
            val coolMatrix = ColorMatrix(floatArrayOf(
                0.93f, 0f, 0f, 0f, -6f,
                0f, 1.07f, 0f, 0f, 7f,
                0f, 0f, 1.10f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(coolMatrix)
        } else if (sceneType == "Beach") {
            // Highlight vibrant turquoise sea blues and sandy golden undertones
            val beachMatrix = ColorMatrix(floatArrayOf(
                1.02f, 0f, 0f, 0f, 3f,
                0f, 1.04f, 0f, 0f, 4f,
                0f, 0f, 1.12f, 0f, 12f,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(beachMatrix)
        } else if (sceneType == "City Street" || sceneType == "Car" || sceneType == "Vehicle") {
            // Cool cinematic metallic grade
            val streetMatrix = ColorMatrix(floatArrayOf(
                0.97f, 0f, 0f, 0f, -2f,
                0f, 1.00f, 0f, 0f, 0f,
                0f, 0f, 1.06f, 0f, 6f,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(streetMatrix)
        }
        
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canv.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }
}
