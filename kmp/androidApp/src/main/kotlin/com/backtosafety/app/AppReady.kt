package com.backtosafety.app

import android.os.Process
import android.os.SystemClock
import com.backtosafety.core.Analytics
import com.backtosafety.core.AnalyticsEvent

/**
 * app_ready, once per process: how long a cold start took, from the process starting to the
 * first frame of the first screen (home or welcome). The field measure of load time.
 */
object AppReady {
    private var reported = false

    fun report() {
        if (reported) return
        reported = true
        val startupMs = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
        Analytics.track(AnalyticsEvent.APP_READY, mapOf("startup_ms" to startupMs))
    }
}
