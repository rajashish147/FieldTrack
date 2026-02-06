package com.fieldtrack.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object ImageUtils {

    private const val MAX_WIDTH = 1280
    private const val MAX_HEIGHT = 1280
    private const val COMPRESS_QUALITY = 75

    fun compressImage(context: Context, uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            val (width, height) = options.outWidth to options.outHeight
            var sampleSize = 1
            while (width / sampleSize > MAX_WIDTH || height / sampleSize > MAX_HEIGHT) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val decodeStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
            decodeStream.close()

            bitmap ?: return null

            val outputFile = File(context.cacheDir, "report_image_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outputFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESS_QUALITY, fos)
            }
            bitmap.recycle()

            outputFile
        } catch (e: Exception) {
            null
        }
    }
}
