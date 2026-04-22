package  com.deepgamer.agriexpert.models

import androidx.room.*

@Dao
interface FarmActivityDao {
    @Query("""
    SELECT * FROM farm_activities
    WHERE date BETWEEN :startDate AND :endDate
""")
    fun getActivitiesByDate(startDate: Long, endDate: Long): List<FarmActivity>


    @Insert
    fun insertActivity(activity: FarmActivity)

    @Delete
    fun deleteActivity(activity: FarmActivity)

    @Update
    fun updateActivity(activity: FarmActivity)
    @Query("SELECT DISTINCT date FROM farm_activities")
    fun getAllActivityDates(): List<Long>

    @Query("SELECT * FROM farm_activities ORDER BY id DESC LIMIT 1")
    fun getLastInserted(): FarmActivity?

}
