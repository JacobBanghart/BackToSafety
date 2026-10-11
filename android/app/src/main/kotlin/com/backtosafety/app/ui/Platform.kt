package com.backtosafety.app.ui

import android.graphics.BitmapFactory
import android.icu.text.DateFormat
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.Date
import java.util.Locale

/** The saved photo at a file:// URI, decoded once per URI. */
@Composable
fun rememberPhoto(uri: String?): ImageBitmap? = remember(uri) {
    uri?.let { runCatching { BitmapFactory.decodeFile(Uri.parse(it).path)?.asImageBitmap() }.getOrNull() }
}

/**
 * JS `Date.toLocaleString()` / `toLocaleTimeString()` as Hermes formats them on Android:
 * ICU skeletons for numeric date and time, in the device locale.
 */
fun localeDateTime(epochMs: Long): String =
    DateFormat.getInstanceForSkeleton("yMdjms", Locale.getDefault()).format(Date(epochMs))

fun localeTime(epochMs: Long): String =
    DateFormat.getInstanceForSkeleton("jms", Locale.getDefault()).format(Date(epochMs))
