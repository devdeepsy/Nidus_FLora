package com.devdeepsy.nidusflora.models

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [FarmActivity::class, CropEntry::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun farmActivityDao(): FarmActivityDao
    abstract fun cropEntryDao(): CropEntryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS crop_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        cropName TEXT NOT NULL,
                        sowingDate INTEGER NOT NULL,
                        expectedHarvestDate INTEGER NOT NULL,
                        wateringIntervalDays INTEGER NOT NULL DEFAULT 2,
                        lastWateredDate INTEGER,
                        notes TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE farm_activities ADD COLUMN reminderTime INTEGER"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smartfarm_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}