package com.devdeepsy.nidusflora.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "farm_activities")
data class FarmActivity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long, // store as timestamp
    val crop: String,
    val activityType: String,
    val notes: String? = null,
    val reminderTime: Long? = null
)
