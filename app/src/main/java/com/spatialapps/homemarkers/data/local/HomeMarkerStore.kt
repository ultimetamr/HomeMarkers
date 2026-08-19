package com.spatialapps.homemarkers.data.local

import android.content.Context
import androidx.room.Room

object HomeMarkerStore {
    @Volatile private var instance: HomeMarkerDatabase? = null
    fun database(context: Context): HomeMarkerDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(context.applicationContext, HomeMarkerDatabase::class.java, "home-markers.db").build().also { instance = it }
    }
}
