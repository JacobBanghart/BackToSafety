package com.backtosafety.app

import android.os.Process
import android.os.SystemClock
import com.backtosafety.core.Analytics
import com.backtosafety.core.AnalyticsEvent

/**
 * app_ready, once per process: how long a cold start took, from the process starting to the
 * first frame of the first screen (home or welcome), the field measure of load time. With it,
 * how ready the app is for an emergency ([readiness], gathered after the time is taken).
 */
object AppReady {
    private var reported = false

    suspend fun report(readiness: suspend () -> Map<String, Any?>) {
        if (reported) return
        reported = true
        val startupMs = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
        val properties = runCatching { readiness() }.getOrDefault(emptyMap())
        Analytics.track(AnalyticsEvent.APP_READY, mapOf("startup_ms" to startupMs) + properties)
    }
}
