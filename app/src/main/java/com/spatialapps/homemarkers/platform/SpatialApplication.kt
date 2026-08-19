package com.spatialapps.homemarkers.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.spatialapps.homemarkers.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
