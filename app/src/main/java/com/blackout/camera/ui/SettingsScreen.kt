package com.blackout.camera.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackout.camera.data.Prefs
import com.blackout.camera.settings.AppIcon
import com.blackout.camera.settings.IconShapes

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        Prefs.location = r.values.any { it }
        if (!Prefs.location) Toast.makeText(context, "Location stays off: permission wasn't given", Toast.LENGTH_SHORT).show()
    }
    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding()) {
        Row(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.Text) }
            Text("Camera settings", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).navigationBarsPadding()) {
            Section("PRIVACY") {
                Row2("Phone details", "Never written: maker, model, serial numbers, software") { Toggle(true, enabled = false) {} }
                Row2("Location", if (Prefs.location) "On: new photos record where they were taken" else "Off: new photos have no location") {
                    Toggle(Prefs.location) { on ->
                        if (on) locPerm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        else Prefs.location = false
                    }
                }
                Row2("No date either", "Maximum level: no date inside new photos, random file names. Videos still carry their recording time.", divider = false) {
                    Toggle(Prefs.noDate) { Prefs.noDate = it }
                }
            }
            Section("FRONT CAMERA") {
                Row2("Front camera", if (Prefs.front) "On only while you look at it; closes instantly when you switch, leave or lock" else "Off: removed from this app") {
                    Toggle(Prefs.front) { Prefs.front = it }
                }
                Row2("Block cameras for all apps", "Opens Android's privacy settings. Turn off Camera access, or use the Quick Settings tile.", divider = false,
                    onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_PRIVACY_SETTINGS)) }.onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }) {
                    Text("›", color = C.Sub, fontSize = 20.sp)
                }
            }
            Section("SHOOTING") {
                Row2("Photo quality", if (Prefs.quality >= 95) "High · 95%" else "Standard · 85%", onClick = { Prefs.quality = if (Prefs.quality >= 95) 85 else 95 }) {
                    Text("Change", color = C.Sub, fontSize = 14.sp)
                }
                Row2("Video", Prefs.video, onClick = { Prefs.video = when (Prefs.video) { "720p" -> "1080p"; "1080p" -> "4K"; else -> "720p" } }) {
                    Text("Change", color = C.Sub, fontSize = 14.sp)
                }
                Row2("Grid lines", "Shown when the camera opens") { Toggle(Prefs.grid) { Prefs.grid = it } }
                Row2("Privacy badge", "Shows what new photos leave out, on the viewfinder") { Toggle(Prefs.badge) { Prefs.badge = it } }
                Row2("Save to", "DCIM/Camera, so Gallery shows them", divider = false) {}
            }
            Section("APP ICON") { IconSection() }
            Text("Camera works fully offline. It has no internet permission, never runs in the background, and keeps no list of your photos.",
                color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(6.dp))
        }
    }
}

@Composable
private fun IconSection() {
    val context = LocalContext.current
    var picker by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        IconShapes.STYLES.keys.forEach { s ->
            val on = Prefs.iconStyle == s
            val bmp = remember(s, Prefs.iconMain, Prefs.iconSecond) { AppIcon.render(s, Prefs.iconMain, Prefs.iconSecond, 192).asImageBitmap() }
            Column(
                Modifier.clip(RoundedCornerShape(18.dp)).border(2.dp, if (on) C.Accent else Color.Transparent, RoundedCornerShape(18.dp))
                    .clickable { Prefs.iconStyle = s; AppIcon.sync(context, s, Prefs.iconMain, Prefs.iconSecond) }.padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(bmp, "Style $s", Modifier.size(56.dp).clip(CircleShape))
                Text("$s · ${IconShapes.NAMES[s]}", color = if (on) C.Accent else C.Sub, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    HorizontalDivider(color = C.Line)
    Row2("Main color", "Color wheel", onClick = { picker = "main" }) { Swatch(Color(Prefs.iconMain)) }
    Row2("Second color", "Color wheel", onClick = { picker = "second" }) { Swatch(Color(Prefs.iconSecond)) }
    Row2("App drawer icon", "Changes by itself. Tap to refresh if it looks old.", onClick = { AppIcon.sync(context, Prefs.iconStyle, Prefs.iconMain, Prefs.iconSecond) }) {
        Text("›", color = C.Sub, fontSize = 20.sp)
    }
    Row2("Add to home screen", "Exact colors. Updates by itself when you change them.", divider = false, onClick = {
        if (!AppIcon.pinOrUpdate(context, Prefs.iconStyle, Prefs.iconMain, Prefs.iconSecond, ask = true))
            Toast.makeText(context, "Your home screen doesn't allow adding icons this way", Toast.LENGTH_SHORT).show()
    }) { Text("›", color = C.Sub, fontSize = 20.sp) }
    picker?.let { which ->
        ColorPickerDialog(
            title = if (which == "main") "Main color" else "Second color",
            initial = if (which == "main") Prefs.iconMain else Prefs.iconSecond,
            onPick = {
                if (which == "main") Prefs.iconMain = it else Prefs.iconSecond = it
                picker = null
                AppIcon.sync(context, Prefs.iconStyle, Prefs.iconMain, Prefs.iconSecond)
            },
            onDismiss = { picker = null },
            resetLabel = "Default",
            onReset = {
                if (which == "main") Prefs.iconMain = AppIcon.DEFAULT_MAIN else Prefs.iconSecond = AppIcon.DEFAULT_SECOND
                picker = null
                AppIcon.sync(context, Prefs.iconStyle, Prefs.iconMain, Prefs.iconSecond)
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card).padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(title, color = C.Sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
        content()
    }
}

@Composable
private fun Row2(title: String, sub: String, divider: Boolean = true, onClick: (() -> Unit)? = null, end: @Composable () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(sub, color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            end()
        }
        if (divider) HorizontalDivider(color = C.Line)
    }
}

@Composable
private fun Toggle(on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Switch(
        checked = on, onCheckedChange = if (enabled) onChange else null, enabled = enabled,
        colors = SwitchDefaults.colors(checkedTrackColor = C.Accent, checkedThumbColor = Color.White,
            disabledCheckedTrackColor = C.Accent.copy(alpha = 0.6f), disabledCheckedThumbColor = Color.White),
    )
}
