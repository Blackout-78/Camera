package com.blackout.camera

import android.app.Application
import com.blackout.camera.data.Prefs

class CameraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }
}
