package com.spatialapps.homemarkers.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import com.pico.spatial.core.math.Vector3

enum class MarkerColor(val label: String) {
    LIGHT_BLUE("淡蓝色"),
    PALE_YELLOW("淡黄色"),
    PALE_PINK("淡粉色"),
    PALE_GREEN("淡绿色"),
    LIGHT_PURPLE("淡紫色"),
    LIGHT_GRAY("浅灰色");

    companion object {
        fun fromStorage(value: String): MarkerColor = when (value) {
            "KITCHEN" -> PALE_YELLOW
            "BEDROOM" -> LIGHT_PURPLE
            "BATHROOM" -> LIGHT_BLUE
            "TOOLS" -> LIGHT_GRAY
            else -> entries.firstOrNull { it.name == value } ?: LIGHT_BLUE
        }
    }
}
enum class ExpiryState { NORMAL, NEAR_EXPIRY, EXPIRED }

data class HomeMarker(
    val id: String = UUID.randomUUID().toString(), val anchorUuid: String? = null,
    val name: String, val location: String, val note: String,
    val expiry: LocalDate? = null, val color: MarkerColor = MarkerColor.LIGHT_BLUE,
    val restored: Boolean = true,
    /** Last recovered world position; never used to impersonate an unavailable persistent anchor. */
    val worldPosition: Vector3? = null,
) {
    val daysRemaining get() = expiry?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }
    val expiryState get() = when { daysRemaining == null -> ExpiryState.NORMAL; daysRemaining!! < 0 -> ExpiryState.EXPIRED; daysRemaining!! <= 7 -> ExpiryState.NEAR_EXPIRY; else -> ExpiryState.NORMAL }
}
