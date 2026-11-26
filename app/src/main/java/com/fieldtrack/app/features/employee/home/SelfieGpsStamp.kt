package com.fieldtrack.app.features.employee.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.location.Location
import android.media.ExifInterface
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** The saved JPEG contains both visible GPS evidence and standard GPS EXIF.
 * Run on Dispatchers.IO: decode/rotate/compress never blocks the camera UI. */
object SelfieGpsStamp {
    private val companyZone = ZoneId.of("Asia/Kolkata")
    private val displayTime = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss", Locale.ENGLISH).withZone(companyZone)

    fun create(photo: File, fix: Location, capturedAt: String): File {
        require(fix.latitude in -90.0..90.0 && fix.longitude in -180.0..180.0) { "Invalid selfie GPS location." }
        val captureTime = Instant.parse(capturedAt)
        val fixTime = Instant.ofEpochMilli(fix.time)
        val orientation = ExifInterface(photo.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(photo.absolutePath, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Camera selfie could not be decoded." }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        val original = BitmapFactory.decodeFile(photo.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Camera selfie could not be decoded.")
        val bitmaps = linkedSetOf(original)
        var output: File? = null
        var completed = false
        try {
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> { setRotate(180f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            val oriented = if (matrix.isIdentity) original else
                Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true).also { bitmaps.add(it) }
            val scale = minOf(1.0, 1600.0 / max(oriented.width, oriented.height))
            val scaled = if (scale == 1.0) oriented else Bitmap.createScaledBitmap(oriented,
                (oriented.width * scale).roundToInt(), (oriented.height * scale).roundToInt(), true).also { bitmaps.add(it) }
            val fontSize = max(12, (scaled.width * 0.026).toInt()).toFloat()
            val padding = max(12, (scaled.width * 0.025).toInt()).toFloat()
            val lineHeight = ceil(fontSize * 1.5).toFloat()
            val lines = listOf(
                "FieldTrack | Start work",
                String.format(Locale.US, "GPS: %.6f, %.6f", fix.latitude, fix.longitude),
                "Accuracy: +/- ${ceil(fix.accuracy.toDouble()).toInt()} m",
                "Photo: ${displayTime.format(captureTime)} IST (UTC+05:30)",
                "GPS fix: ${displayTime.format(fixTime)} IST (UTC+05:30)",
            )
            val stamped = Bitmap.createBitmap(scaled.width, scaled.height + (padding * 2 + lineHeight * lines.size).roundToInt(), Bitmap.Config.ARGB_8888)
                .also { bitmaps.add(it) }
            val canvas = Canvas(stamped)
            canvas.drawBitmap(scaled, 0f, 0f, null)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.rgb(17, 24, 39)
            canvas.drawRect(0f, scaled.height.toFloat(), stamped.width.toFloat(), stamped.height.toFloat(), paint)
            paint.color = Color.WHITE; paint.textSize = fontSize
            lines.forEachIndexed { index, line ->
                // Fit long timestamps even on a small/landscape camera image.
                paint.textScaleX = 1f
                paint.textScaleX = minOf(1f, (stamped.width - 2 * padding) / paint.measureText(line))
                canvas.drawText(line, padding, scaled.height + padding - paint.fontMetrics.top + index * lineHeight, paint)
            }
            output = File.createTempFile("attendance_gps_", ".jpg", photo.parentFile)
            output.outputStream().use { check(stamped.compress(Bitmap.CompressFormat.JPEG, 85, it)) { "Could not encode GPS selfie." } }
            ExifInterface(output.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                setAttribute(ExifInterface.TAG_GPS_LATITUDE, degreesMinutesSeconds(fix.latitude))
                setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (fix.latitude < 0) "S" else "N")
                setAttribute(ExifInterface.TAG_GPS_LONGITUDE, degreesMinutesSeconds(fix.longitude))
                setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (fix.longitude < 0) "W" else "E")
                setAttribute(ExifInterface.TAG_GPS_DATESTAMP, DateTimeFormatter.ofPattern("yyyy:MM:dd").withZone(ZoneOffset.UTC).format(fixTime))
                setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC).format(fixTime))
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss").withZone(companyZone).format(captureTime))
                saveAttributes()
            }
            val storedLocation = FloatArray(2)
            check(ExifInterface(output.absolutePath).getLatLong(storedLocation)
                && kotlin.math.abs(storedLocation[0].toDouble() - fix.latitude) < 0.00001
                && kotlin.math.abs(storedLocation[1].toDouble() - fix.longitude) < 0.00001) {
                "Could not embed GPS metadata in the attendance selfie."
            }
            completed = true
            return output
        } finally {
            bitmaps.forEach { it.recycle() }
            if (!completed) output?.delete()
        }
    }

    private fun degreesMinutesSeconds(value: Double): String {
        val microseconds = (kotlin.math.abs(value) * 3600 * 1_000_000).roundToLong()
        val degrees = microseconds / 3_600_000_000L
        val remainder = microseconds % 3_600_000_000L
        return "$degrees/1,${remainder / 60_000_000L}/1,${remainder % 60_000_000L}/1000000"
    }
}
