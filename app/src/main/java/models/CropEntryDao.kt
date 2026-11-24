package com.devdeepsy.nidusflora.models

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CropEntryDao {

    @Query("SELECT * FROM crop_entries ORDER BY sowingDate ASC")
    fun getAllCrops(): Flow<List<CropEntry>>

    @Insert
    suspend fun insertCrop(crop: CropEntry)

    @Delete
    suspend fun deleteCrop(crop: CropEntry)

    @Update
    suspend fun updateCrop(crop: CropEntry)

    @Query("SELECT COUNT(*) FROM crop_entries")
    suspend fun getCropCount(): Int

    @Query("SELECT * FROM crop_entries WHERE cropName = :name ORDER BY id DESC LIMIT 1")
    suspend fun getCropByName(name: String): CropEntry?
}