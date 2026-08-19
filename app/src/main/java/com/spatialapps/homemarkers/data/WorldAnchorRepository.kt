package com.spatialapps.homemarkers.data

import android.util.Log
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.sense.world.WorldAnchor
import com.pico.spatial.sense.world.WorldTrackingManager
import com.pico.spatial.sense.world.WorldTrackingResult
import java.util.UUID

/** Full-Space-only persistence bridge. A marker is never saved before this succeeds. */
class WorldAnchorRepository {
    suspend fun create(position: Vector3, rotation: EulerAngles, name: String): String? = when (val result = WorldTrackingManager.createAnchor(position, rotation, name)) {
        is WorldTrackingResult.Success -> result.data?.anchorUUID?.toString()?.also { Log.i(TAG, "created=$it") }
        is WorldTrackingResult.Error -> null.also { Log.w(TAG, "create failed ${result.errorCode}: ${result.errorMessage}") }
    }
    suspend fun loadAll(): Map<String, WorldAnchor> = when (val result = WorldTrackingManager.loadAnchor()) {
        is WorldTrackingResult.Success -> result.data?.associateBy { it.anchorUUID.toString() }.orEmpty()
        is WorldTrackingResult.Error -> emptyMap<String, WorldAnchor>().also { Log.w(TAG, "load failed ${result.errorMessage}") }
    }
    fun subscribe(onChange: (event: String, anchor: WorldAnchor) -> Unit) =
        WorldTrackingManager.subscribeAnchorUpdate { update -> onChange(update.event.name, update.anchor) }
    suspend fun remove(uuid: String): Boolean = runCatching { UUID.fromString(uuid) }.getOrNull()?.let { WorldTrackingManager.removeAnchor(it) is WorldTrackingResult.Success } ?: true
    private companion object { const val TAG = "HomeMarkersAnchor" }
}
