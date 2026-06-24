package com.example.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.CapturedPhoto
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM captured_photos ORDER BY timestamp DESC")
    fun getAllPhotos(): Flow<List<CapturedPhoto>>

    @Query("SELECT * FROM captured_photos WHERE id = :id")
    suspend fun getPhotoById(id: Long): CapturedPhoto?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: CapturedPhoto): Long

    @Delete
    suspend fun deletePhoto(photo: CapturedPhoto)

    @Query("DELETE FROM captured_photos")
    suspend fun clearAll()
}
