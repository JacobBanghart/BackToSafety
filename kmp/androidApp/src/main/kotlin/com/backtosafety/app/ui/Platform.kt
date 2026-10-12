package com.backtosafety.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.icu.text.DateFormat
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The saved photo at a file:// URI, for showing [sizeDp] square: decoded off the main thread,
 * once per URI, no larger than that needs. Null while it loads, and for a file that is missing
 * or can't be decoded (the screens show their placeholder then).
 */
@Composable
fun rememberPhoto(uri: String?, sizeDp: Int = 200): ImageBitmap? {
    val targetPx = (sizeDp * LocalDensity.current.density).roundToInt()
    return produceState<ImageBitmap?>(null, uri, targetPx) {
        value = uri?.let { withContext(Dispatchers.IO) { decodePhoto(Uri.parse(it).path, targetPx)?.asImageBitmap() } }
    }.value
}

/**
 * Decodes the image at [path] to at least [targetPx] on its short side, and at most twice that
 * (a power-of-two inSampleSize, chosen from the bounds read first), so a 50 MP file never
 * becomes a 200 MB bitmap. Null for anything that fails, running out of memory included.
 */
fun decodePhoto(path: String?, targetPx: Int): Bitmap? = if (path == null || !File(path).isFile) null else runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = photoSampleSize(bounds.outWidth, bounds.outHeight, targetPx) })
}.getOrNull()

/** The largest power of two that keeps the short side of a [width] x [height] image at [targetPx] or more. */
fun photoSampleSize(width: Int, height: Int, targetPx: Int): Int {
    val shortSide = minOf(width, height)
    var sample = 1
    while (shortSide / (sample * 2) >= targetPx) sample *= 2
    return sample
}

/**
 * JS `Date.toLocaleString()` / `toLocaleTimeString()` as Hermes formats them on Android:
 * ICU skeletons for numeric date and time, in the device locale.
 */
fun localeDateTime(epochMs: Long): String =
    DateFormat.getInstanceForSkeleton("yMdjms", Locale.getDefault()).format(Date(epochMs))

fun localeTime(epochMs: Long): String =
    DateFormat.getInstanceForSkeleton("jms", Locale.getDefault()).format(Date(epochMs))
