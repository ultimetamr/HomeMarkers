package com.spatialapps.homemarkers.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao interface HomeMarkerDao {
    @Query("SELECT * FROM home_markers ORDER BY name") fun observe(): Flow<List<HomeMarkerEntity>>
    @Upsert suspend fun upsert(marker: HomeMarkerEntity)
    @Query("DELETE FROM home_markers WHERE id = :id") suspend fun delete(id: String)
}
