package com.backtosafety.app.emergency

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.backtosafety.app.AppServices
import com.backtosafety.app.BackToSafetyApp
import com.backtosafety.app.savedLanguage
import com.backtosafety.app.ui.runCatchingCancellable
import com.backtosafety.core.AppClock

/**
 * After the phone restarts (once it's unlocked), picks up an emergency that was under way:
 * nothing scheduled outlives a restart, nor do the notifications shown. See [resumeAfterRestart].
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as BackToSafetyApp
        val result = goAsync()
        app.withServices { services ->
            try {
                resumeAfterRestart(services) { app.language = it }
            } finally {
                result.finish()
            }
        }
    }
}

/**
 * The emergency in [services]' store, if one is under way, after a restart: the alerts still to
 * come are scheduled again and the one that came due meanwhile is posted (shared
 * EmergencyAway.restarted decides both), and the countdown shows again. The text is in the
 * language the app was last set to ([setLanguage] makes it the process's).
 *
 * The countdown's foreground service (type specialUse) may start from BOOT_COMPLETED: Android
 * 15 bars only dataSync, camera, mediaPlayback, phoneCall, mediaProjection and microphone. If
 * it's refused all the same, it shows the next time the app opens, and the alarms stand in.
 */
internal suspend fun resumeAfterRestart(services: AppServices, setLanguage: (String) -> Unit) {
    runCatchingCancellable {
        val emergency = services.store.activeEmergency() ?: return
        setLanguage(services.store.savedLanguage())
        services.away.restarted(AppClock.nowMs())?.let(services.notifications::post)
        services.countdown.sync(emergency.startedAt)
    }.onFailure { Log.e("BackToSafety", "Resuming the emergency after a restart failed", it) }
}
