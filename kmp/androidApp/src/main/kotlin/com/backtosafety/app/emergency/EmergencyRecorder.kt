package com.backtosafety.app.emergency

import com.backtosafety.core.ActiveEmergency
import com.backtosafety.core.data.Store
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The emergency screen's writes to the store: the saved active emergency and the incident row.
 *
 * They run one at a time, so a slow save can't land after the clear that ends the emergency,
 * and two incident updates can't overwrite each other's outcome or end time. Once
 * [end] is called the emergency is never saved again, whatever the screen still has queued.
 * The incident id lives here: the row an emergency without one gets (see [record]) is
 * remembered for the later writes, not made twice.
 */
internal class EmergencyRecorder(private val store: Store) {
    private val lock = Mutex()

    @Volatile
    private var ended = false
    private var incidentId: Long? = null

    /** Takes up the emergency the screen loaded or started, and its incident row if it has one. */
    fun begin(emergency: ActiveEmergency) {
        incidentId = emergency.incidentId
    }

    /** Saves [emergency] as the active emergency (with the incident id held here), unless it has ended. */
    suspend fun save(emergency: ActiveEmergency) = lock.withLock {
        if (!ended) store.saveActiveEmergency(emergency.copy(incidentId = incidentId))
    }

    /**
     * Records what happened in the incident row (Store.recordIncident). An emergency without a
     * row (started before incidents existed, or its row failed to be made) gets one now, and
     * the active emergency is saved to carry its id. Failing to record is not for the user to
     * handle: the emergency goes on without it.
     */
    suspend fun record(
        startedAt: String,
        checkedSteps: List<String>,
        wearing: String,
        outcome: String? = null,
        endedAt: String? = null,
    ) = lock.withLock {
        try {
            val hadRow = incidentId != null
            incidentId = store.recordIncident(incidentId, startedAt, checkedSteps, wearing, outcome, endedAt)
            if (!hadRow && !ended) store.saveActiveEmergency(ActiveEmergency(startedAt, wearing, checkedSteps, incidentId))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    /** Ends the emergency: clears the saved one, after any save already under way. */
    suspend fun end() {
        // Before waiting for the lock, so saves queued behind this one see it.
        ended = true
        lock.withLock { store.clearActiveEmergency() }
    }
}
