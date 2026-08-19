package com.spatialapps.homemarkers.domain

import kotlin.math.atan2
import kotlin.math.sqrt

data class MarkerGuidance(val direction: String, val distanceMeters: Float)

object MarkerGuidanceCalculator {
    fun calculate(
        eyeX: Float,
        eyeY: Float,
        eyeZ: Float,
        forwardX: Float,
        forwardZ: Float,
        targetX: Float,
        targetY: Float,
        targetZ: Float,
    ): MarkerGuidance {
        val dx = targetX - eyeX
        val dy = targetY - eyeY
        val dz = targetZ - eyeZ
        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        val forwardLength = sqrt(forwardX * forwardX + forwardZ * forwardZ).coerceAtLeast(.0001f)
        val fx = forwardX / forwardLength
        val fz = forwardZ / forwardLength
        val angle = Math.toDegrees(atan2(dx * -fz + dz * fx, dx * fx + dz * fz).toDouble()).toFloat()
        val horizontal = when {
            angle >= -22.5f && angle < 22.5f -> "正前方"
            angle >= 22.5f && angle < 67.5f -> "右前方"
            angle >= 67.5f && angle < 112.5f -> "右侧"
            angle >= 112.5f && angle < 157.5f -> "右后方"
            angle >= 157.5f || angle < -157.5f -> "后方"
            angle >= -157.5f && angle < -112.5f -> "左后方"
            angle >= -112.5f && angle < -67.5f -> "左侧"
            else -> "左前方"
        }
        val vertical = when {
            dy > .45f -> "上方"
            dy < -.45f -> "下方"
            else -> ""
        }
        return MarkerGuidance(
            direction = listOf(horizontal, vertical).filter(String::isNotEmpty).joinToString("·"),
            distanceMeters = distance,
        )
    }
}
