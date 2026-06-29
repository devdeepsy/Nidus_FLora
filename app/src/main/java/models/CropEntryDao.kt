package com.devdeepsy.nidusflora.models

import androidx.room.*

@Dao
interface CropEntryDao {

    @Query("SELECT * FROM crop_entries ORDER BY sowingDate ASC")
    fun getAllCrops(): List<CropEntry>

    @Insert
    fun insertCrop(crop: CropEntry)

    @Delete
    fun deleteCrop(crop: CropEntry)

    @Update
    fun updateCrop(crop: CropEntry)

    @Query("SELECT COUNT(*) FROM crop_entries")
    fun getCropCount(): Int

    @Query("SELECT * FROM crop_entries WHERE cropName = :name ORDER BY id DESC LIMIT 1")
    fun getCropByName(name: String): CropEntry?
}