package com.blackout.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackout.camera.data.Prefs
import com.blackout.camera.settings.AppIcon
import com.blackout.camera.ui.C
import com.blackout.camera.ui.CameraScreen
import com.blackout.camera.ui.CameraTheme
import com.blackout.camera.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // After an update, put back the icon you chose.
        // Done in the background so it never slows down opening the camera.
        val app = applicationContext
        Thread { runCatching { AppIcon.syncAfterUpdate(app, Prefs.iconStyle, Prefs.iconMain, Prefs.iconSecond) } }.start()
        setContent {
            CameraTheme {
                PermissionGate {
                    var settings by rememberSaveable { mutableStateOf(false) }
                    if (settings) SettingsScreen { settings = false } else CameraScreen { settings = true }
                }
            }
        }
    }
}

@Composable
private fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    fun has() = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(has()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = has() }
    LaunchedEffect(Unit) {
        if (!has() || context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        if (!granted && has()) granted = true
        onPauseOrDispose { }
    }
    if (granted) { content(); return }
    Column(
        Modifier.fillMaxSize().background(C.Bg).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Camera needs permission to use the camera", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text("It only opens while you're looking at it. Nothing leaves this phone.", color = C.Sub, fontSize = 14.sp, textAlign = TextAlign.Center)
        Button(onClick = { launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) },
            colors = ButtonDefaults.buttonColors(containerColor = C.Accent, contentColor = Color.White)) { Text("Allow") }
        TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }) { Text("Open app settings", color = C.Sub) }
    }
}
