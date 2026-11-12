@file:OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)

package com.blackout.camera.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.GridOff
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.HdrOff
import androidx.compose.material.icons.outlined.HdrOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.blackout.camera.data.Prefs
import com.blackout.camera.data.Saver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class Mode { Night, Photo, Video, Pro, QR }
private enum class Flash { Off, On, Auto }
private val AWB = listOf(
    "Auto" to CaptureRequest.CONTROL_AWB_MODE_AUTO, "Sun" to CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,
    "Cloudy" to CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, "Indoor" to CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
    "Fluor." to CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT,
)

@SuppressLint("MissingPermission")
@Composable
fun CameraScreen(onSettings: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val main = remember { ContextCompat.getMainExecutor(context) }
    val worker = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // Faster to start than the compatible mode.
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    var mode by remember { mutableStateOf(Mode.Photo) }
    var front by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(Flash.Off) }
    var timer by remember { mutableIntStateOf(0) }
    var hdr by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(Prefs.grid) }
    var active by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recSeconds by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var lastUri by remember { mutableStateOf<Uri?>(null) }
    var lastVideo by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // text, good
    var qrText by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    var extNote by remember { mutableStateOf<String?>(null) }
    // Pro
    var manual by remember { mutableStateOf(false) }
    var isoF by remember { mutableFloatStateOf(0.3f) }
    var shutF by remember { mutableFloatStateOf(0.5f) }
    var awb by remember { mutableIntStateOf(0) }
    var ev by remember { mutableIntStateOf(0) }
    // Night vision
    var vision by remember { mutableStateOf(Vision.White) }
    var boost by remember { mutableFloatStateOf(0.6f) }
    var nvFrame by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val vp = remember { VisionProcessor() }
    val nv = mode == Mode.Night && vision != Vision.Night
    SideEffect { vp.vision = vision; vp.boost = boost }
    // Night vision exposure step: higher = longer exposure per frame.
    var nvStep by remember { mutableIntStateOf(1) }

    if (!Prefs.front && front) front = false

    // The camera is open only while this screen is in front: it closes on pause, lock, calls and leaving.
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> active = true
                Lifecycle.Event.ON_PAUSE -> { recording?.stop(); active = false }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); worker.shutdown() }
    }

    val provider by produceState<ProcessCameraProvider?>(null) { value = ProcessCameraProvider.getInstance(context).await() }
    // Night/HDR helpers load only when you pick Night or HDR, so they never slow down opening the camera.
    val needExt = (mode == Mode.Night && !nv) || (mode == Mode.Photo && hdr)
    var extensions by remember { mutableStateOf<ExtensionsManager?>(null) }
    LaunchedEffect(provider, needExt) {
        val p = provider ?: return@LaunchedEffect
        if (needExt && extensions == null) extensions = runCatching { ExtensionsManager.getInstanceAsync(context, p).await() }.getOrNull()
    }

    // Keep photos upright however the phone is held.
    DisposableEffect(imageCapture, videoCapture) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(o: Int) {
                if (o == ORIENTATION_UNKNOWN) return
                val r = when (o) { in 45..134 -> Surface.ROTATION_270; in 135..224 -> Surface.ROTATION_180; in 225..314 -> Surface.ROTATION_90; else -> Surface.ROTATION_0 }
                imageCapture?.targetRotation = r
                videoCapture?.targetRotation = r
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }

    // Open (or close) the camera whenever what's needed changes.
    LaunchedEffect(provider, if (needExt) extensions else null, mode, nv, front, hdr, active, Prefs.quality, Prefs.video) {
        val p = provider ?: return@LaunchedEffect
        p.unbindAll()
        camera = null; imageCapture = null; videoCapture = null
        nvFrame = null; vp.reset()
        if (!active) return@LaunchedEffect
        val base = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val extMode = when {
            mode == Mode.Night && !nv -> ExtensionMode.NIGHT
            mode == Mode.Photo && hdr -> ExtensionMode.HDR
            else -> null
        }
        val ext = extensions
        val selector = if (extMode != null && ext != null && ext.isExtensionAvailable(base, extMode)) {
            extNote = null
            ext.getExtensionEnabledCameraSelector(base, extMode)
        } else {
            // While the Night/HDR helper is still loading, say nothing; it re-opens with it in a moment.
            extNote = if (ext == null) null
            else if (extMode == ExtensionMode.NIGHT) "This phone has no Night mode for apps · taking normal photos"
            else if (extMode == ExtensionMode.HDR) "This phone has no HDR for apps" else null
            base
        }
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        runCatching {
            when {
                mode == Mode.Video -> {
                    val q = when (Prefs.video) { "720p" -> Quality.HD; "4K" -> Quality.UHD; else -> Quality.FHD }
                    val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(q, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))).build()
                    val vc = VideoCapture.withOutput(recorder)
                    camera = p.bindToLifecycle(owner, selector, preview, vc)
                    videoCapture = vc
                }
                nv -> {
                    // Night vision: the camera's frames are brightened and recolored before you see them.
                    val res = androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                        .setAspectRatioStrategy(androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(androidx.camera.core.resolutionselector.ResolutionStrategy(Size(960, 720),
                            androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                        .build()
                    val analysis = ImageAnalysis.Builder().setResolutionSelector(res)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analysis.setAnalyzer(worker) { img ->
                        val bmp = runCatching { vp.process(img) }.getOrNull()
                        img.close()
                        if (bmp != null) { val ib = bmp.asImageBitmap(); main.execute { nvFrame = ib } }
                    }
                    val ic = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setJpegQuality(100)
                        .build()
                    camera = p.bindToLifecycle(owner, selector, preview, analysis, ic)
                    imageCapture = ic
                }
                mode == Mode.QR -> {
                    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analysis.setAnalyzer(worker, QrAnalyzer { text -> main.execute { qrText = text } })
                    camera = p.bindToLifecycle(owner, selector, preview, analysis)
                }
                else -> {
                    val ic = ImageCapture.Builder()
                        // Fast shutter: the picture is taken at once. HDR keeps the slower, higher-quality path.
                        .setCaptureMode(if (hdr) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setJpegQuality(Prefs.quality)
                        .build()
                    camera = p.bindToLifecycle(owner, selector, preview, ic)
                    imageCapture = ic
                }
            }
            zoom = 1f
        }.onFailure { status = "Couldn't open the camera: ${it.message}" to false }
    }

    // Flash and torch.
    LaunchedEffect(flash, imageCapture, camera, mode, nv) {
        imageCapture?.flashMode = when (flash) { Flash.On -> ImageCapture.FLASH_MODE_ON; Flash.Auto -> ImageCapture.FLASH_MODE_AUTO; Flash.Off -> ImageCapture.FLASH_MODE_OFF }
        camera?.cameraControl?.enableTorch((mode == Mode.Video || nv) && flash == Flash.On)
    }

    // Pro settings: manual ISO and shutter, white balance, exposure.
    val c2info = camera?.let { runCatching { Camera2CameraInfo.from(it.cameraInfo) }.getOrNull() }
    val isoRange = c2info?.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
    val expRange = c2info?.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
    val manualOk = c2info?.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true && isoRange != null && expRange != null
    val isoValue = isoRange?.let { (it.lower * (it.upper.toFloat() / it.lower).pow(isoF)).roundToInt() } ?: 0
    val expValue = expRange?.let {
        val hi = minOf(it.upper, 1_000_000_000L).toDouble()
        (it.lower * (hi / it.lower).pow(shutF.toDouble())).roundToLong()
    } ?: 0L
    // Night vision exposure ladder (each frame gathers light for longer), capped by what the phone allows.
    // Longest is 125 ms (about 8 frames a second) so the picture stays smooth; the frame averaging makes up the light.
    val nvLevels = remember(expRange) {
        listOf(33_000_000L, 50_000_000L, 66_000_000L, 100_000_000L, 125_000_000L)
            .filter { expRange == null || it <= expRange.upper }.ifEmpty { listOf(expRange?.upper ?: 33_000_000L) }
    }
    // Highest sensor gain the phone allows (done on the sensor, before the picture loses detail).
    val nvIso = isoRange?.upper ?: 0
    // Watches how much light arrives and lengthens or shortens the exposure to match.
    LaunchedEffect(camera, nv) {
        if (!nv) return@LaunchedEffect
        nvStep = 1
        while (true) {
            delay(600)
            val m = vp.rawMean
            if (m < 45f && nvStep < nvLevels.lastIndex) nvStep++
            else if (m > 150f && nvStep > 0) nvStep--
        }
    }
    LaunchedEffect(camera, mode, nv, nvStep, manual, isoValue, expValue, awb) {
        val cam = camera ?: return@LaunchedEffect
        val ctl = runCatching { Camera2CameraControl.from(cam.cameraControl) }.getOrNull() ?: return@LaunchedEffect
        val b = CaptureRequestOptions.Builder()
        if (mode == Mode.Pro) {
            if (manual && manualOk) {
                b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, isoValue)
                b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, expValue)
            }
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, AWB[awb].second)
        }
        if (nv && manualOk) {
            // Full manual: highest real sensor gain and a long exposure per frame, like a night vision tube.
            val t = nvLevels[nvStep.coerceIn(0, nvLevels.lastIndex)]
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, nvIso)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, t)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, t)
            b.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
        } else if (nv) {
            // Let the camera slow down so each frame gathers more light, and clean the noise as well as it can.
            c2info?.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                ?.minWithOrNull(compareBy({ it.lower }, { it.upper }))
                ?.let { b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
            b.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
        }
        runCatching { ctl.setCaptureRequestOptions(b.build()) }
    }
    LaunchedEffect(camera, ev, mode, nv) {
        val cam = camera ?: return@LaunchedEffect
        val most = cam.cameraInfo.exposureState.exposureCompensationRange.upper
        runCatching { cam.cameraControl.setExposureCompensationIndex(if (nv) most else if (mode == Mode.Pro) ev else 0) }
    }

    LaunchedEffect(status) { if (status != null) { delay(3000); status = null } }
    LaunchedEffect(focusAt) { if (focusAt != null) { delay(1500); focusAt = null } }
    LaunchedEffect(mode) { qrText = null }

    fun takePhoto() {
        val ic = imageCapture ?: return
        busy = true
        ic.takePicture(main, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val buf = image.planes[0].buffer
                val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
                val rotation = image.imageInfo.rotationDegrees
                image.close()
                val look = if (nv) vision else null
                val lift = boost
                scope.launch {
                    val saved = withContext(Dispatchers.IO) {
                        runCatching {
                            val jpeg = if (look != null) VisionProcessor.photo(bytes, look, lift, Prefs.quality) else bytes
                            Saver.savePhoto(context, jpeg, rotation, Saver.lastLocation(context))
                        }
                    }
                    busy = false
                    saved.onSuccess { s ->
                        lastUri = s.uri; lastVideo = false
                        status = if (s.findings.isEmpty()) "Saved · checked: no phone info${if (Prefs.location) "" else ", no location"} ✓" to true
                        else "Saved, but found: ${s.findings.joinToString()}" to false
                    }.onFailure { status = "Couldn't save the photo: ${it.message}" to false }
                }
            }
            override fun onError(e: ImageCaptureException) { busy = false; status = "Couldn't take the photo: ${e.message}" to false }
        })
    }

    fun toggleVideo() {
        recording?.let { it.stop(); return }
        val vc = videoCapture ?: return
        val opts = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(Saver.videoValues()).build()
        var pending = vc.output.prepareRecording(context, opts)
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) pending = pending.withAudioEnabled()
        recSeconds = 0
        recording = pending.start(main) { e ->
            when (e) {
                is VideoRecordEvent.Status -> recSeconds = (e.recordingStats.recordedDurationNanos / 1_000_000_000L).toInt()
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    if (!e.hasError()) {
                        lastUri = e.outputResults.outputUri; lastVideo = true
                        status = "Video saved · no location, no phone info ✓" to true
                    } else status = "Video not saved (error ${e.error})" to false
                }
                else -> Unit
            }
        }
    }

    fun shutter() {
        when {
            mode == Mode.QR -> Unit
            mode == Mode.Video -> toggleVideo()
            busy || countdown > 0 -> Unit
            timer > 0 -> scope.launch {
                for (s in timer downTo 1) { countdown = s; delay(1000) }
                countdown = 0
                takePhoto()
            }
            else -> takePhoto()
        }
    }

    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        if (nv) {
            Box(Modifier.fillMaxSize().background(Color.Black))
            nvFrame?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
            // Tube edge, like a night vision scope.
            if (vision != Vision.Thermal) Box(Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.radialGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
        }

        // Tap to focus, pinch to zoom.
        Box(
            Modifier.fillMaxSize()
                .pointerInput(camera) {
                    detectTapGestures { off ->
                        val cam = camera ?: return@detectTapGestures
                        val pt = previewView.meteringPointFactory.createPoint(off.x, off.y)
                        cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(pt).setAutoCancelDuration(3, TimeUnit.SECONDS).build())
                        focusAt = off
                    }
                }
                .pointerInput(camera) {
                    detectTransformGestures { _, _, z, _ ->
                        val cam = camera ?: return@detectTransformGestures
                        val zs = cam.cameraInfo.zoomState.value ?: return@detectTransformGestures
                        val next = (zoom * z).coerceIn(zs.minZoomRatio, zs.maxZoomRatio)
                        zoom = next
                        cam.cameraControl.setZoomRatio(next)
                    }
                },
        )

        if (showGrid) Canvas(Modifier.fillMaxSize()) {
            val col = Color.White.copy(alpha = 0.35f)
            for (k in 1..2) {
                drawLine(col, Offset(size.width * k / 3, 0f), Offset(size.width * k / 3, size.height), 1.dp.toPx())
                drawLine(col, Offset(0f, size.height * k / 3), Offset(size.width, size.height * k / 3), 1.dp.toPx())
            }
        }
        focusAt?.let { f ->
            val half = with(density) { 36.dp.toPx() }
            Box(Modifier.offset { IntOffset((f.x - half).roundToInt(), (f.y - half).roundToInt()) }.size(72.dp).border(2.dp, Color.White, RoundedCornerShape(12.dp)))
        }

        // Top: floating buttons, privacy badge, recording time.
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (recording == null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Glass(
                        when (flash) { Flash.Off -> Icons.Outlined.FlashOff; Flash.On -> Icons.Outlined.FlashOn; Flash.Auto -> Icons.Outlined.FlashAuto },
                        "Flash ${flash.name}",
                    ) { flash = Flash.entries[(flash.ordinal + 1) % 3]; status = "Flash: ${flash.name}" to true }
                    Glass(if (timer == 0) Icons.Outlined.TimerOff else Icons.Outlined.Timer, "Timer", label = if (timer == 0) null else "${timer}s") {
                        timer = when (timer) { 0 -> 3; 3 -> 10; else -> 0 }
                    }
                    Glass(if (hdr) Icons.Outlined.HdrOn else Icons.Outlined.HdrOff, "HDR", tint = if (hdr) C.Accent else Color.White) {
                        hdr = !hdr; if (mode != Mode.Photo) mode = Mode.Photo
                    }
                    Glass(if (showGrid) Icons.Outlined.GridOn else Icons.Outlined.GridOff, "Grid lines") { showGrid = !showGrid }
                    Glass(Icons.Outlined.Settings, "Settings", onClick = onSettings)
                }
                Text(com.blackout.camera.Version.tag, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 4.dp).clickable { onSettings() })
                if (Prefs.badge) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(C.Glass).padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.VerifiedUser, null, tint = C.Accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            (if (Prefs.location) "Location on" else "No location") + " · No phone info" + if (Prefs.noDate) " · No date" else "",
                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            } else {
                Row(
                    Modifier.padding(top = 20.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(C.Rec))
                    Spacer(Modifier.width(8.dp))
                    Text("%02d:%02d".format(recSeconds / 60, recSeconds % 60), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            extNote?.let { Text(it, color = C.Warn, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(C.Glass).padding(horizontal = 10.dp, vertical = 4.dp)) }
        }

        if (countdown > 0) Text("$countdown", color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))

        // Bottom: messages, Pro panel, QR result, modes, shutter row.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            status?.let { (text, good) ->
                Text(text, color = if (good) Color.White else C.Warn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 6.dp))
            }
            if (mode == Mode.QR) qrText?.let { QrCard(context, it) }
            if (mode == Mode.Night && recording == null) {
                Column(
                    Modifier.padding(horizontal = 14.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Vision.entries.forEach { v ->
                            val on = v == vision
                            Text(v.label, color = if (on) Color.White else C.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) C.Accent else C.Line)
                                    .clickable { vision = v }.padding(vertical = 7.dp))
                        }
                    }
                    if (nv) ProSlider("BOOST", "${(boost * 100).roundToInt()}%", boost) { boost = it }
                    Text(
                        when (vision) {
                            Vision.Night -> "Your phone's own night mode"
                            Vision.White, Vision.Green -> nvInfo(manualOk, nvLevels.getOrNull(nvStep))
                            Vision.Thermal -> "Thermal style · colors show brightness, not heat. Real heat needs a plug-in thermal camera."
                        },
                        color = C.Sub, fontSize = 11.sp,
                    )
                }
            }
            if (mode == Mode.Pro) {
                Column(
                    Modifier.padding(horizontal = 14.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (manualOk) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Manual ISO and shutter", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Switch(checked = manual, onCheckedChange = { manual = it }, colors = SwitchDefaults.colors(checkedTrackColor = C.Accent))
                        }
                        if (manual) {
                            ProSlider("ISO", "$isoValue", isoF) { isoF = it }
                            ProSlider("SHUTTER", shutterLabel(expValue), shutF) { shutF = it }
                        }
                    } else Text("This phone doesn't allow apps manual ISO and shutter.", color = C.Sub, fontSize = 12.sp)
                    val evRange = camera?.cameraInfo?.exposureState?.exposureCompensationRange
                    val step = camera?.cameraInfo?.exposureState?.exposureCompensationStep?.toFloat() ?: 0f
                    if (evRange != null && evRange.upper > evRange.lower && !(manual && manualOk)) {
                        val span = (evRange.upper - evRange.lower).toFloat()
                        ProSlider("EXPOSURE", "%+.1f".format(ev * step), (ev - evRange.lower) / span) {
                            ev = (evRange.lower + it * span).roundToInt()
                        }
                    }
                    Text("WHITE BALANCE", color = C.Sub, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AWB.forEachIndexed { i, (name, _) ->
                            Text(name, color = if (awb == i) Color.White else C.Text, fontSize = 12.sp,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (awb == i) C.Accent else C.Line).clickable { awb = i }.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }
            }
            if (recording == null) {
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Mode.entries.forEach { m ->
                        val on = m == mode
                        Text(m.name, color = if (on) Color.White else Color(0xFFD0D0D6), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) C.Accent else Color.Transparent)
                                .clickable { mode = m; if (m != Mode.Photo) hdr = false }.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
                if (zoom > 1.01f || camera != null) {
                    Text(
                        if (zoom < 1.95f) "1×" else "%.1f×".format(zoom), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape).background(C.Glass).clickable {
                            val cam = camera ?: return@clickable
                            val max = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
                            zoom = when { zoom < 1.95f && max >= 2f -> 2f; zoom < 4.95f && max >= 5f -> 5f; else -> 1f }
                            cam.cameraControl.setZoomRatio(zoom)
                        }.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 45.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                LastShot(lastUri, lastVideo)
                ShutterButton(video = mode == Mode.Video, rec = recording != null, enabled = mode != Mode.QR) { shutter() }
                if (Prefs.front && recording == null) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(C.Glass).clickable { front = !front }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Cameraswitch, if (front) "Switch to back camera" else "Switch to front camera", tint = Color.White)
                    }
                } else Spacer(Modifier.size(48.dp))
            }
        }
    }
}

private fun nvInfo(manualOk: Boolean, ns: Long?): String =
    if (!manualOk || ns == null) "Night vision · this phone doesn't allow long exposures, so it needs some light"
    else "Night vision · exposure ${shutterLabel(ns)}" + if (ns >= 100_000_000L) " · very dark: hold still" else ""

private fun shutterLabel(ns: Long): String =
    if (ns >= 500_000_000L) "%.1fs".format(ns / 1e9) else "1/${(1e9 / ns.coerceAtLeast(1)).roundToInt()}"

@Composable
private fun Glass(icon: ImageVector, desc: String, tint: Color = Color.White, label: String? = null, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(C.Glass).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (label != null) Text(label, color = C.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        else Icon(icon, desc, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ProSlider(label: String, value: String, pos: Float, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(label, color = C.Sub, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(value = pos.coerceIn(0f, 1f), onValueChange = onChange, modifier = Modifier.height(28.dp),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = C.Accent, inactiveTrackColor = C.Line))
    }
}

@Composable
private fun ShutterButton(video: Boolean, rec: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(84.dp).clip(CircleShape).border(4.dp, if (enabled) Color.White else C.Sub, CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val inner = if (rec) 28.dp else 68.dp
        Box(Modifier.size(inner).clip(if (rec) RoundedCornerShape(6.dp) else CircleShape).background(if (video) C.Rec else if (enabled) Color.White else C.Sub))
    }
}

@Composable
private fun LastShot(uri: Uri?, video: Boolean) {
    val context = LocalContext.current
    val thumb by produceState<Bitmap?>(null, uri) {
        value = uri?.let { u -> withContext(Dispatchers.IO) { runCatching { context.contentResolver.loadThumbnail(u, Size(160, 160), null) }.getOrNull() } }
    }
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).border(2.dp, Color.White, RoundedCornerShape(10.dp)).background(C.Card)
            .clickable(enabled = uri != null) { if (uri != null) openShot(context, uri, video) },
    ) {
        thumb?.let { Image(it.asImageBitmap(), "Open last shot", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** Opens the last photo or video in the app picked in Settings → Open photos with. */
fun openShot(context: Context, uri: Uri, video: Boolean) {
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, if (video) "video/*" else "image/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chooser = Intent.createChooser(view, "Open with")
    val ours = Intent(view).setPackage(GALLERY_PACKAGE)
    val hasOurs = ours.resolveActivity(context.packageManager) != null
    val pick = when (Prefs.openWith) {
        "gallery" -> if (hasOurs) ours else chooser
        "choose" -> chooser
        else -> view
    }
    runCatching { context.startActivity(pick) }.onFailure {
        runCatching { context.startActivity(chooser) }.onFailure {
            android.widget.Toast.makeText(context, "No app on this phone can open it", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    if (Prefs.openWith == "gallery" && !hasOurs)
        android.widget.Toast.makeText(context, "Your Gallery app isn't installed (or needs updating) · pick an app", android.widget.Toast.LENGTH_LONG).show()
}

const val GALLERY_PACKAGE = "com.blackout.gallery"

@Composable
private fun QrCard(context: Context, text: String) {
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("QR CODE · READ OFFLINE", color = C.Sub, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 4)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Copy", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(C.Accent).clickable {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("QR", text))
                }.padding(horizontal = 14.dp, vertical = 8.dp))
            Text("Share text", color = Color.White, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, C.Line, RoundedCornerShape(10.dp)).clickable {
                    runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share")) }
                }.padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
}
