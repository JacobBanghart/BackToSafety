package com.backtosafety.app.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.backtosafety.core.Analytics
import com.backtosafety.core.AnalyticsEvent
import com.backtosafety.core.DialTarget
import com.backtosafety.core.Translate
import com.backtosafety.core.dialFailedProperties
import com.backtosafety.core.formatPhoneNumber
import com.backtosafety.core.invoke
import com.backtosafety.core.saveFailedProperties

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
    Analytics.track(AnalyticsEvent.SAVE_FAILED, saveFailedProperties(screen, action, error::class.simpleName))
