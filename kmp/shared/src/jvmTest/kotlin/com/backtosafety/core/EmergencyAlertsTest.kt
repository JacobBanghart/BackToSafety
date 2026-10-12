package com.backtosafety.core

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The events around alerting the circle: emergency_contacts_alerted means the same on both
 * apps because both report through trackSmsResult.
 */
class EmergencyAlertsTest {
    private val tracked = mutableListOf<Pair<String, Map<String, Any?>>>()

    @BeforeTest
    fun captureAnalytics() {
        Analytics.sink = { name, properties -> tracked += name to properties }
    }

    @AfterTest
    fun releaseAnalytics() {
        Analytics.sink = { _, _ -> }
    }

    @Test
    fun aSentOrHandedOffTextCountsAsAlertingTheContacts() {
        for (result in listOf(SmsResult.SENT, SmsResult.HANDED_OFF)) {
            tracked.clear()
            trackSmsResult(result, 2)
            assertEquals(
                listOf(
                    "emergency_contacts_alerted" to mapOf<String, Any?>("recipient_count" to 2),
                    "emergency_sms_result" to mapOf<String, Any?>("result" to result.key, "recipient_count" to 2),
                ),
                tracked,
            )
        }
    }

    @Test
    fun aCancelledFailedOrUnavailableTextDoesNot() {
        for (result in listOf(SmsResult.CANCELLED, SmsResult.FAILED, SmsResult.UNAVAILABLE)) {
            tracked.clear()
            trackSmsResult(result, 3)
            assertEquals(
                listOf("emergency_sms_result" to mapOf<String, Any?>("result" to result.key, "recipient_count" to 3)),
                tracked,
            )
        }
    }
}
