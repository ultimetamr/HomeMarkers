package com.spatialapps.homemarkers

import com.spatialapps.homemarkers.domain.ExpiryState
import com.spatialapps.homemarkers.domain.HomeMarker
import com.spatialapps.homemarkers.domain.MarkerColor
import com.spatialapps.homemarkers.domain.MarkerSearch
import com.spatialapps.homemarkers.domain.MarkerGuidanceCalculator
import com.spatialapps.homemarkers.domain.MarkerHighlightPresentationPolicy
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMarkerDomainTest {
    private fun marker(
        name: String,
        location: String = "厨房储物柜",
        note: String = "备用",
        color: MarkerColor = MarkerColor.LIGHT_BLUE,
        expiry: LocalDate? = null,
    ) = HomeMarker(name = name, location = location, note = note, color = color, expiry = expiry)

    @Test fun fuzzySearchMatchesNameLocationNoteAndColor() {
        val markers = listOf(
            marker("沙拉酱", note = "开封后冷藏"),
            marker("电池", location = "工具柜", color = MarkerColor.LIGHT_GRAY),
        )
        assertEquals("沙拉酱", MarkerSearch.filter(markers, "沙酱").single().name)
        assertEquals("电池", MarkerSearch.filter(markers, "浅灰").single().name)
        assertEquals("沙拉酱", MarkerSearch.filter(markers, "冷藏").single().name)
    }

    @Test fun markersDefaultToLightBlueAndLegacyCategoriesRemainReadable() {
        assertEquals(MarkerColor.LIGHT_BLUE, marker("默认颜色").color)
        assertEquals(MarkerColor.PALE_YELLOW, MarkerColor.fromStorage("KITCHEN"))
        assertEquals(MarkerColor.LIGHT_PURPLE, MarkerColor.fromStorage("BEDROOM"))
        assertEquals(MarkerColor.LIGHT_BLUE, MarkerColor.fromStorage("BATHROOM"))
        assertEquals(MarkerColor.LIGHT_GRAY, MarkerColor.fromStorage("TOOLS"))
        assertEquals(MarkerColor.LIGHT_BLUE, MarkerColor.fromStorage("UNKNOWN"))
    }

    @Test fun expiryStatesCoverNormalNearAndExpired() {
        assertEquals(ExpiryState.EXPIRED, marker("过期", expiry = LocalDate.now().minusDays(1)).expiryState)
        assertEquals(ExpiryState.NEAR_EXPIRY, marker("临期", expiry = LocalDate.now().plusDays(7)).expiryState)
        assertEquals(ExpiryState.NORMAL, marker("正常", expiry = LocalDate.now().plusDays(8)).expiryState)
        assertEquals(ExpiryState.NORMAL, marker("无期限").expiryState)
    }

    @Test fun searchHandlesMoreThanTwentyMarkers() {
        val markers = (1..25).map { marker("物品$it", location = if (it % 2 == 0) "卧室" else "厨房") }
        assertEquals(25, MarkerSearch.filter(markers, "").size)
        assertTrue(MarkerSearch.filter(markers, "物品25").any { it.name == "物品25" })
    }

    @Test fun guidanceReportsRelativeDirectionAndDistance() {
        val guidance = MarkerGuidanceCalculator.calculate(
            eyeX = 0f, eyeY = 1.6f, eyeZ = 0f,
            forwardX = 0f, forwardZ = -1f,
            targetX = 1f, targetY = 1.6f, targetZ = -1f,
        )
        assertEquals("右前方", guidance.direction)
        assertTrue(kotlin.math.abs(guidance.distanceMeters - 1.414f) < .01f)
    }

    @Test fun highlightPresentationUsesStaticOpacityOnly() {
        assertEquals(1f, MarkerHighlightPresentationPolicy.alpha(dimmed = false))
        assertEquals(0.18f, MarkerHighlightPresentationPolicy.alpha(dimmed = true))
    }
}
