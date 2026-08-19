package com.spatialapps.homemarkers.platform

import android.content.pm.ApplicationInfo
import android.os.Bundle
import com.pico.spatial.ui.platform.stub.SpatialLaunchActivity
import kotlinx.coroutines.flow.MutableStateFlow

class LaunchActivity : SpatialLaunchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            DebugLaunchArguments.highlightDistanceMeters.value =
                intent.getFloatExtra(EXTRA_QA_HIGHLIGHT_DISTANCE_METERS, 0f)
            DebugLaunchArguments.placementArmed.value =
                intent.getBooleanExtra(EXTRA_QA_PLACEMENT_ARMED, false)
            DebugLaunchArguments.editorVisible.value =
                intent.getBooleanExtra(EXTRA_QA_EDITOR_VISIBLE, false)
        }
        super.onCreate(savedInstanceState)
    }

    companion object {
        const val EXTRA_QA_HIGHLIGHT_DISTANCE_METERS = "qa_highlight_distance_meters"
        const val EXTRA_QA_PLACEMENT_ARMED = "qa_placement_armed"
        const val EXTRA_QA_EDITOR_VISIBLE = "qa_editor_visible"
    }
}

object DebugLaunchArguments {
    val highlightDistanceMeters = MutableStateFlow(0f)
    val placementArmed = MutableStateFlow(false)
    val editorVisible = MutableStateFlow(false)
}
