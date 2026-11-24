package com.devdeepsy.nidusflora.models

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FarmActivityDao {

    @Query(
        """
        SELECT * FROM farm_activities
        WHERE date BETWEEN :startDate AND :endDate
        """
    )
    fun getActivitiesByDate(startDate: Long, endDate: Long): Flow<List<FarmActivity>>

    @Insert
    suspend fun insertActivity(activity: FarmActivity)

    @Delete
    suspend fun deleteActivity(activity: FarmActivity)

    @Update
    suspend fun updateActivity(activity: FarmActivity)

    @Query("SELECT DISTINCT date FROM farm_activities")
    fun getAllActivityDates(): Flow<List<Long>>

    @Query("SELECT * FROM farm_activities ORDER BY id DESC LIMIT 1")
    suspend fun getLastInserted(): FarmActivity?
}