package com.spatialapps.homemarkers

import com.spatialapps.homemarkers.content.HomeMarkersVolume
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        // Persistent anchors are legal only while this Full Space Stage is active.
        DefaultStage {
            PicoTheme {
                HomeMarkersVolume()
            }
        }
    }
