package com.blackout.camera.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves new photos with nothing inside that points to you:
 * no phone maker or model, serial numbers, software, owner or notes, and no location unless you turn it on.
 */
object Saver {
    private val REMOVE = listOf(
        "Make", "Model", "BodySerialNumber", "LensMake", "LensModel", "LensSerialNumber", "LensSpecification",
        "ImageUniqueID", "MakerNote", "Software", "Artist", "CameraOwnerName", "Copyright", "UserComment", "ImageDescription", "Xmp",
        "GPSVersionID", "GPSLatitudeRef", "GPSLatitude", "GPSLongitudeRef", "GPSLongitude", "GPSAltitudeRef", "GPSAltitude",
        "GPSTimeStamp", "GPSSatellites", "GPSStatus", "GPSMeasureMode", "GPSDOP", "GPSSpeedRef", "GPSSpeed", "GPSTrackRef", "GPSTrack",
        "GPSImgDirectionRef", "GPSImgDirection", "GPSMapDatum", "GPSProcessingMethod", "GPSAreaInformation", "GPSDateStamp",
        "GPSDifferential", "GPSHPositioningError",
    )
    private val DATES = listOf(
        "DateTime", "DateTimeOriginal", "DateTimeDigitized", "OffsetTime", "OffsetTimeOriginal", "OffsetTimeDigitized",
        "SubSecTime", "SubSecTimeOriginal", "SubSecTimeDigitized",
    )

    /** File name: the date and time, or random letters when "No date either" is on. */
    fun fileName(prefix: String, ext: String): String =
        if (Prefs.noDate) {
            val abc = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            val rnd = java.security.SecureRandom()
            prefix + "_" + (1..8).map { abc[rnd.nextInt(abc.length)] }.joinToString("") + "." + ext
        } else {
            prefix + "_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + "." + ext
        }

    fun lastLocation(context: Context): Location? {
        if (!Prefs.location) return null
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        return runCatching {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull()
    }

    data class Saved(val uri: Uri, val findings: List<String>)

    /** Cleans the camera's JPEG, saves it to DCIM/Camera, then checks the saved file. */
    fun savePhoto(context: Context, jpeg: ByteArray, rotation: Int, location: Location?): Saved {
        val tmp = File(context.cacheDir, "shot_${System.nanoTime()}.jpg")
        try {
            // Drop every extra block the camera added (maker data, XMP, hidden extra pictures, data after the picture).
            tmp.writeBytes(Deep.strip(jpeg) ?: jpeg)
            val exif = ExifInterface(tmp.absolutePath)
            REMOVE.forEach { exif.setAttribute(it, null) }
            if (Prefs.noDate) DATES.forEach { exif.setAttribute(it, null) }
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, when ((rotation % 360 + 360) % 360) {
                90 -> ExifInterface.ORIENTATION_ROTATE_90; 180 -> ExifInterface.ORIENTATION_ROTATE_180
                270 -> ExifInterface.ORIENTATION_ROTATE_270; else -> ExifInterface.ORIENTATION_NORMAL
            }.toString())
            if (location != null) exif.setGpsInfo(location)
            exif.saveAttributes()
            val bytes = tmp.readBytes()

            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName("IMG", "jpg"))
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera/")
                put(MediaStore.MediaColumns.DATE_TAKEN, System.currentTimeMillis())
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
            resolver.openOutputStream(uri)!!.use { it.write(bytes) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)

            // Check the saved file, the same way Gallery does.
            val saved = resolver.openInputStream(uri)!!.use { it.readBytes() }
            val found = mutableListOf<String>()
            if (!saved.contentEquals(bytes)) found += "saved file differs"
            val check = runCatching { ExifInterface(saved.inputStream()) }.getOrNull()
            if (check != null) {
                REMOVE.filter { !it.startsWith("GPS") }.filter { check.getAttribute(it) != null }.forEach { found += it }
                if (location == null && check.latLong != null) found += "location"
                if (Prefs.noDate && DATES.any { check.getAttribute(it) != null }) found += "date"
            }
            Deep.scan(saved, "image/jpeg").forEach { found += it.title }
            return Saved(uri, found)
        } finally {
            tmp.delete()
        }
    }

    /** Where a new video goes. */
    fun videoValues(): ContentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName("VID", "mp4"))
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera/")
    }
}
