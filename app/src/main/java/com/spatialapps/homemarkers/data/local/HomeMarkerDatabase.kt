package com.spatialapps.homemarkers.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [HomeMarkerEntity::class], version = 1, exportSchema = false)
abstract class HomeMarkerDatabase : RoomDatabase() { abstract fun markerDao(): HomeMarkerDao }
