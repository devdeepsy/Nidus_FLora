package  com.deepgamer.agriexpert.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "crop_entries")
data class CropEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cropName: String,
    val sowingDate: Long,
    val expectedHarvestDate: Long,
    val wateringIntervalDays: Int = 2,
    val lastWateredDate: Long? = null,
    val notes: String? = null
)