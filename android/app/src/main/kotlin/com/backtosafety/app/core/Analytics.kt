package com.backtosafety.app.core

import com.backtosafety.core.ffi.AnalyticsEvent
import com.backtosafety.core.ffi.AnalyticsSink
import com.backtosafety.core.ffi.PropValue
import com.backtosafety.core.ffi.analyticsScreen
import com.backtosafety.core.ffi.analyticsScreenEventName
import com.backtosafety.core.ffi.analyticsSetSink
import com.backtosafety.core.ffi.analyticsTrack

/**
 * utils/analytics.ts: the app reports [AnalyticsEvent]s and screen views through this, and the
 * Rust core forwards them to the [sink] (PostHog); with no sink nothing is sent.
 */
object Analytics {
    /** Receives (event wire name, properties); screens arrive as "\$screen" with the RN route path. */
    var sink: ((name: String, properties: Map<String, Any?>) -> Unit)? = null
        set(value) {
            field = value
            analyticsSetSink(value?.let { forward -> Forwarder(forward) })
        }

    private class Forwarder(val forward: (String, Map<String, Any?>) -> Unit) : AnalyticsSink {
        override fun onEvent(name: String, properties: Map<String, PropValue>) = forward(name, properties.toAnys())
    }

    fun track(event: AnalyticsEvent, properties: Map<String, Any?> = emptyMap()) = analyticsTrack(event, properties.toPropValues())

    /**
     * A screen view: by the RN app's route path (e.g. "/emergency") on every navigation, plus
     * the named ones some RN screens report themselves ("home", "settings").
     */
    fun screen(path: String, properties: Map<String, Any?> = emptyMap()) = analyticsScreen(path, properties.toPropValues())

    val SCREEN: String = analyticsScreenEventName()
}
