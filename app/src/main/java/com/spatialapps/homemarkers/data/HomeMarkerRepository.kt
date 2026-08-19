package com.spatialapps.homemarkers.data

import com.pico.spatial.core.math.Vector3
import com.spatialapps.homemarkers.data.local.HomeMarkerDao
import com.spatialapps.homemarkers.data.local.HomeMarkerEntity
import com.spatialapps.homemarkers.domain.HomeMarker
import com.spatialapps.homemarkers.domain.MarkerColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class HomeMarkerRepository(private val dao: HomeMarkerDao) {
    fun observe(): Flow<List<HomeMarker>> = dao.observe().map { it.map(HomeMarkerEntity::domain) }
    suspend fun save(marker: HomeMarker, position: Vector3) = dao.upsert(
        HomeMarkerEntity(
            id = marker.id,
            anchorUuid = requireNotNull(marker.anchorUuid),
            name = marker.name,
            location = marker.location,
            note = marker.note,
            expiryEpochDay = marker.expiry?.toEpochDay(),
            color = marker.color.name,
            positionX = position.x,
            positionY = position.y,
            positionZ = position.z,
        ),
    )
    suspend fun delete(id: String) = dao.delete(id)
}
private fun HomeMarkerEntity.domain() = HomeMarker(
    id = id,
    anchorUuid = anchorUuid,
    name = name,
    location = location,
    note = note,
    expiry = expiryEpochDay?.let(LocalDate::ofEpochDay),
    color = MarkerColor.fromStorage(color),
    worldPosition = Vector3(positionX, positionY, positionZ),
)
