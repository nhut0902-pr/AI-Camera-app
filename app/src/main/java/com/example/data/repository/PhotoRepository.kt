package com.example.data.repository

import com.example.data.database.PhotoDao
import com.example.data.model.CapturedPhoto
import kotlinx.coroutines.flow.Flow

interface PhotoRepository {
    fun getAllPhotos(): Flow<List<CapturedPhoto>>
    suspend fun getPhotoById(id: Long): CapturedPhoto?
    suspend fun insertPhoto(photo: CapturedPhoto): Long
    suspend fun deletePhoto(photo: CapturedPhoto)
    suspend fun clearHistory()
}

class PhotoRepositoryImpl(private val photoDao: PhotoDao) : PhotoRepository {
    override fun getAllPhotos(): Flow<List<CapturedPhoto>> = photoDao.getAllPhotos()
    override suspend fun getPhotoById(id: Long): CapturedPhoto? = photoDao.getPhotoById(id)
    override suspend fun insertPhoto(photo: CapturedPhoto): Long = photoDao.insertPhoto(photo)
    override suspend fun deletePhoto(photo: CapturedPhoto) = photoDao.deletePhoto(photo)
    override suspend fun clearHistory() = photoDao.clearAll()
}
