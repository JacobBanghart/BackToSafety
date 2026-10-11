package com.backtosafety.app.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.backtosafety.app.core.Analytics
import com.backtosafety.core.ffi.AnalyticsEvent
import com.backtosafety.core.ffi.DialTarget
import com.backtosafety.app.core.Translate
import com.backtosafety.core.ffi.dialFailedProperties
import com.backtosafety.core.ffi.formatPhoneNumber
import com.backtosafety.app.core.analyticsTypeName
import com.backtosafety.app.core.invoke
import com.backtosafety.core.ffi.saveFailedProperties

/**
 * Opens the phone app at [number]. A device with no phone app (most tablets) can't: then it
 * says so, with the number to call from a phone, and reports dial_failed. Returns whether the
 * phone app opened.
 */
fun dial(context: Context, number: String, target: DialTarget, screen: String, tCommon: Translate): Boolean {
    val opened = runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }.isSuccess
    if (!opened) {
        Analytics.track(AnalyticsEvent.DIAL_FAILED, dialFailedProperties(target, screen))
        AlertDialog.Builder(context)
            .setTitle(tCommon("callUnavailable.title"))
            .setMessage(tCommon("callUnavailable.message", mapOf("number" to formatPhoneNumber(number))))
            .setPositiveButton(tCommon("ok"), null)
            .show()
    }
    return opened
}

/** save_failed: a write to the store failed and the user saw an error. */
fun reportSaveFailed(screen: String, action: String, error: Throwable) =
    Analytics.track(AnalyticsEvent.SAVE_FAILED, saveFailedProperties(screen, action, error.analyticsTypeName()))
