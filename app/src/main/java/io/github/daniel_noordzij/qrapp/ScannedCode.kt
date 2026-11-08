package io.github.daniel_noordzij.qrapp

import android.graphics.PointF
import android.graphics.Rect
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.net.toUri
import com.google.mlkit.vision.barcode.common.Barcode
import kotlin.math.max

/** A decoded code. Kept in memory only. */
class ScannedCode(
    val text: String,
    @param:StringRes val formatName: Int,
    /** Set only when the code is an http(s) link. */
    val link: Uri?,
    /** Outline of the code, in the coordinates of the frame or image it was found in. */
    val outline: List<PointF>,
)

/** Picks the code closest to the given point (normally the centre of the frame). */
fun List<Barcode>.closestTo(x: Float, y: Float): ScannedCode? =
    mapNotNull { barcode -> barcode.toScannedCode()?.let { it to barcode.boundingBox } }
        .minWithOrNull(
            compareBy<Pair<ScannedCode, Rect?>>(
                { (_, box) -> box?.distanceSquaredTo(x, y) ?: Float.MAX_VALUE },
                { (_, box) -> box?.centerDistanceSquaredTo(x, y) ?: Float.MAX_VALUE },
            ),
        )
        ?.first

private fun Barcode.toScannedCode(): ScannedCode? {
    val text = rawValue?.takeIf { it.isNotEmpty() }
        ?: displayValue?.takeIf { it.isNotEmpty() }
        ?: return null
    val outline = cornerPoints?.map { PointF(it.x.toFloat(), it.y.toFloat()) }
        ?: boundingBox?.let {
            listOf(
                PointF(it.left.toFloat(), it.top.toFloat()),
                PointF(it.right.toFloat(), it.top.toFloat()),
                PointF(it.right.toFloat(), it.bottom.toFloat()),
                PointF(it.left.toFloat(), it.bottom.toFloat()),
            )
        }
        ?: emptyList()
    return ScannedCode(
        text = text,
        formatName = formatName(format),
        link = text.asWebLink() ?: url?.url?.asWebLink(),
        outline = outline,
    )
}

/** Returns the text as a URI if it is a single http or https link, otherwise null. */
fun String.asWebLink(): Uri? {
    val candidate = trim()
    if (candidate.isEmpty() || candidate.any { it.isWhitespace() }) return null
    val uri = candidate.toUri().normalizeScheme()
    val isWeb = uri.scheme == "http" || uri.scheme == "https"
    return uri.takeIf { isWeb && !it.host.isNullOrEmpty() }
}

/** Distance from the point to the rectangle; zero when the point is inside it. */
private fun Rect.distanceSquaredTo(x: Float, y: Float): Float {
    val dx = max(max(left - x, 0f), x - right)
    val dy = max(max(top - y, 0f), y - bottom)
    return dx * dx + dy * dy
}

private fun Rect.centerDistanceSquaredTo(x: Float, y: Float): Float {
    val dx = exactCenterX() - x
    val dy = exactCenterY() - y
    return dx * dx + dy * dy
}

@StringRes
private fun formatName(format: Int): Int = when (format) {
    Barcode.FORMAT_QR_CODE -> R.string.format_qr_code
    Barcode.FORMAT_AZTEC -> R.string.format_aztec
    Barcode.FORMAT_CODABAR -> R.string.format_codabar
    Barcode.FORMAT_CODE_39 -> R.string.format_code_39
    Barcode.FORMAT_CODE_93 -> R.string.format_code_93
    Barcode.FORMAT_CODE_128 -> R.string.format_code_128
    Barcode.FORMAT_DATA_MATRIX -> R.string.format_data_matrix
    Barcode.FORMAT_EAN_8 -> R.string.format_ean_8
    Barcode.FORMAT_EAN_13 -> R.string.format_ean_13
    Barcode.FORMAT_ITF -> R.string.format_itf
    Barcode.FORMAT_PDF417 -> R.string.format_pdf417
    Barcode.FORMAT_UPC_A -> R.string.format_upc_a
    Barcode.FORMAT_UPC_E -> R.string.format_upc_e
    else -> R.string.format_unknown
}
