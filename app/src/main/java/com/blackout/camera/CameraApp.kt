package com.blackout.camera

import android.app.Application
import com.blackout.camera.data.Prefs

class CameraApp : Application(), androidx.camera.core.CameraXConfig.Provider {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        // Get the camera system ready while the app is still starting, so the viewfinder appears sooner.
        // This only lists the cameras; it doesn't open one or take any picture.
        androidx.camera.lifecycle.ProcessCameraProvider.getInstance(this)
    }

    override fun getCameraXConfig(): androidx.camera.core.CameraXConfig =
        androidx.camera.core.CameraXConfig.Builder.fromConfig(androidx.camera.camera2.Camera2Config.defaultConfig())
            .setMinimumLoggingLevel(android.util.Log.ERROR)
            .build()
}
