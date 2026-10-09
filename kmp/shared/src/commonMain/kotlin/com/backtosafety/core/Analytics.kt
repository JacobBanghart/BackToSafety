package com.backtosafety.core

/**
 * utils/analytics.ts: the app reports [AnalyticsEvent]s and screen views through this. The
 * platform supplies the sink (PostHog on Android); without an API key nothing is sent.
 */
object Analytics {
    /** Receives (event wire name, properties); screens arrive as "\$screen" with the RN route path. */
    var sink: (name: String, properties: Map<String, Any?>) -> Unit = { _, _ -> }

    fun track(event: AnalyticsEvent, properties: Map<String, Any?> = emptyMap()) = sink(event.wireName, properties)

    /**
     * A screen view: by the RN app's route path (e.g. "/emergency") on every navigation, plus
     * the named ones some RN screens report themselves ("home", "settings").
     */
    fun screen(path: String, properties: Map<String, Any?> = emptyMap()) = sink(SCREEN, properties + ("\$screen_name" to path))

    const val SCREEN = "\$screen"
}
