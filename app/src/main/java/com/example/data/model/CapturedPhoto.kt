package com.example.data.model

import androidx.room.*

@Entity(tableName = "captured_photos")
data class CapturedPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val enhancedFilePath: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val sceneType: String = "Unknown",
    val brightness: Float = 1.0f,
    val contrast: Float = 1.0f,
    val isEnhanced: Boolean = false,
    val appliedFilters: String = "",
    val faceCount: Int = 0,
    val processingTimeMs: Long = 0L
)
