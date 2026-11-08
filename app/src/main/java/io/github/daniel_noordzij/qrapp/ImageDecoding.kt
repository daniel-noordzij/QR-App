package io.github.daniel_noordzij.qrapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** Longest side of a decoded image. Large enough for small codes, small enough for memory. */
private const val MAX_IMAGE_SIDE = 2048

/**
 * Decodes the image straight into memory, upright and scaled down if needed.
 * Nothing is copied or cached on disk.
 */
@Throws(IOException::class)
fun decodeBitmap(context: Context, uri: Uri): Bitmap {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        decodeWithImageDecoder(context, uri)
    } else {
        decodeWithBitmapFactory(context, uri)
    }
    return if (bitmap.config == Bitmap.Config.ARGB_8888) {
        bitmap
    } else {
        bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: throw IOException("Unsupported image")
    }
}

@RequiresApi(Build.VERSION_CODES.P)
private fun decodeWithImageDecoder(context: Context, uri: Uri): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    // ImageDecoder applies the EXIF orientation itself.
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val width = info.size.width
        val height = info.size.height
        val longest = max(width, height)
        if (longest > MAX_IMAGE_SIDE) {
            val scale = MAX_IMAGE_SIDE.toFloat() / longest
            decoder.setTargetSize(
                max(1, (width * scale).roundToInt()),
                max(1, (height * scale).roundToInt()),
            )
        }
    }
}

private fun decodeWithBitmapFactory(context: Context, uri: Uri): Bitmap {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: throw IOException("Cannot open image")
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image")

    var sampleSize = 1
    while (max(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_IMAGE_SIDE) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw IOException("Cannot decode image")

    val orientation = try {
        resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }
    } catch (e: IOException) {
        null
    } ?: ExifInterface.ORIENTATION_NORMAL

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.apply { setRotate(180f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSPOSE -> matrix.apply { setRotate(90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> matrix.apply { setRotate(-90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
    }
    if (matrix.isIdentity) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
