package com.blackout.camera.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/** Your settings. Stored only on this phone. No photo history is kept. */
object Prefs {
    private lateinit var sp: SharedPreferences

    private val _location = mutableStateOf(false)
    private val _noDate = mutableStateOf(false)
    private val _front = mutableStateOf(true)
    private val _grid = mutableStateOf(true)
    private val _badge = mutableStateOf(true)
    private val _quality = mutableIntStateOf(95)
    private val _video = mutableStateOf("1080p")
    private val _iconStyle = mutableStateOf("A")
    private val _iconMain = mutableIntStateOf(0xFF4F9DFF.toInt())
    private val _iconSecond = mutableIntStateOf(0xFFFFFFFF.toInt())

    fun init(context: Context) {
        sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _location.value = sp.getBoolean("location", false)
        _noDate.value = sp.getBoolean("noDate", false)
        _front.value = sp.getBoolean("front", true)
        _grid.value = sp.getBoolean("grid", true)
        _badge.value = sp.getBoolean("badge", true)
        _quality.intValue = sp.getInt("quality", 95)
        _video.value = sp.getString("video", "1080p") ?: "1080p"
        _iconStyle.value = sp.getString("iconStyle", "A") ?: "A"
        _iconMain.intValue = sp.getInt("iconMain", 0xFF4F9DFF.toInt())
        _iconSecond.intValue = sp.getInt("iconSecond", 0xFFFFFFFF.toInt())
    }

    private fun b(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()

    /** Add where the photo was taken. Off unless you turn it on. */
    var location: Boolean get() = _location.value; set(v) { _location.value = v; b("location", v) }
    /** Maximum level: no date inside new photos either, and random file names. */
    var noDate: Boolean get() = _noDate.value; set(v) { _noDate.value = v; b("noDate", v) }
    /** Front camera allowed in this app at all. */
    var front: Boolean get() = _front.value; set(v) { _front.value = v; b("front", v) }
    var grid: Boolean get() = _grid.value; set(v) { _grid.value = v; b("grid", v) }
    var badge: Boolean get() = _badge.value; set(v) { _badge.value = v; b("badge", v) }
    var quality: Int get() = _quality.intValue; set(v) { _quality.intValue = v; sp.edit().putInt("quality", v).apply() }
    var video: String get() = _video.value; set(v) { _video.value = v; sp.edit().putString("video", v).apply() }
    var iconStyle: String get() = _iconStyle.value; set(v) { _iconStyle.value = v; sp.edit().putString("iconStyle", v).apply() }
    var iconMain: Int get() = _iconMain.intValue; set(v) { _iconMain.intValue = v; sp.edit().putInt("iconMain", v).apply() }
    var iconSecond: Int get() = _iconSecond.intValue; set(v) { _iconSecond.intValue = v; sp.edit().putInt("iconSecond", v).apply() }
}
