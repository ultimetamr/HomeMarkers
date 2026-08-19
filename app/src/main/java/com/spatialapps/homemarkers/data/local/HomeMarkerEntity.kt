package com.spatialapps.homemarkers.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "home_markers")
data class HomeMarkerEntity(
    @PrimaryKey val id: String,
    val anchorUuid: String,
    val name: String,
    val location: String,
    val note: String,
    val expiryEpochDay: Long?,
    @ColumnInfo(name = "category") val color: String,
    val positionX: Float,
    val positionY: Float,
    val positionZ: Float,
)
