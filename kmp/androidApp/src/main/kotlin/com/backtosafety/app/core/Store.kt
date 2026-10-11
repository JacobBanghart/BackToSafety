package com.backtosafety.app.core

import com.backtosafety.core.ffi.ActiveEmergency
import com.backtosafety.core.ffi.ContactEntity
import com.backtosafety.core.ffi.DestinationEntity
import com.backtosafety.core.ffi.IncidentEntity
import com.backtosafety.core.ffi.Profile
import com.backtosafety.core.ffi.ProfileEntity
import com.backtosafety.core.ffi.seedAndReportMigration
import com.backtosafety.core.ffi.settingKeys
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.backtosafety.core.ffi.Store as CoreStore

/**
 * The app's data (spec/storage.md), in the Rust core: suspend calls over its synchronous Store,
 * run off the main thread.
 */
class Store(internal val core: CoreStore) {
    private suspend fun <T> io(block: () -> T): T = withContext(dispatcher) { block() }

    /** Seeds the rows the RN app inserts on every launch, and reports data_migrated if opening migrated a database. */
    suspend fun seed() = io { seedAndReportMigration(core) }

    suspend fun isOnboarded(): Boolean = io { core.isOnboarded() }

    suspend fun completeStep(step: String) = io { core.completeStep(step) }

    suspend fun profile(): Profile? = io { core.profile() }

    /** Saves a profile change: the first save creates the single profile row, later saves update it. */
    suspend fun saveProfile(change: (ProfileEntity) -> ProfileEntity) = io {
        core.saveProfileEntity(change(core.profileEntity() ?: ProfileEntity(name = "")))
    }

    /** A partial profile save by field name (the Profile property names): given fields are set (null clears), the rest kept. */
    suspend fun updateProfile(values: Map<String, String?>) = io { core.updateProfile(values) }

    suspend fun addContact(contact: ContactEntity, sortOrder: Int? = null): Long = io { core.addContact(contact, sortOrder) }

    suspend fun updateContact(c: ContactEntity) = io { core.updateContact(c) }

    suspend fun deleteContact(id: Long) = io { core.deleteContact(id) }

    suspend fun reorderContacts(ordered: List<ContactEntity>) = io { core.reorderContacts(ordered) }

    suspend fun addDestination(d: DestinationEntity, sortOrder: Int? = null): Long = io { core.addDestination(d, sortOrder) }

    suspend fun updateDestination(d: DestinationEntity) = io { core.updateDestination(d) }

    suspend fun deleteDestination(id: Long) = io { core.deleteDestination(id) }

    suspend fun reorderDestinations(ordered: List<DestinationEntity>) = io { core.reorderDestinations(ordered) }

    suspend fun contacts(): List<ContactEntity> = io { core.contacts() }

    /** Contacts the emergency alert texts. */
    suspend fun emergencyContacts(): List<ContactEntity> = io { core.emergencyContacts() }

    suspend fun destinations(): List<DestinationEntity> = io { core.destinations() }

    suspend fun activeEmergency(): ActiveEmergency? = io { core.activeEmergency() }

    suspend fun saveActiveEmergency(e: ActiveEmergency) = io { core.saveActiveEmergency(e) }

    suspend fun clearActiveEmergency() = io { core.clearActiveEmergency() }

    suspend fun incidents(): List<IncidentEntity> = io { core.incidents() }

    suspend fun createIncident(startedAt: String): Long = io { core.createIncident(startedAt) }

    suspend fun recordIncident(incidentId: Long?, startedAt: String, checkedSteps: List<String>, wearing: String, outcome: String? = null, endedAt: String? = null): Long =
        io { core.recordIncident(incidentId, startedAt, checkedSteps, wearing, outcome, endedAt) }

    /** Settings > Delete Account: empties every table, then re-creates the onboarding steps. */
    suspend fun clearAllData() = io { core.clearAllData() }

    suspend fun readinessProperties(): Map<String, Any?> = io { core.readinessProperties().toAnys() }

    /** The anonymous per-install ID. */
    suspend fun deviceId(): String = io { core.deviceId() }

    suspend fun setting(key: String): String? = io { core.setting(key) }

    suspend fun putSetting(key: String, value: String) = io { core.putSetting(key, value) }

    companion object {
        /** Where Store calls run. Snapshot tests set Dispatchers.Unconfined, so a screen has its data when the test looks. */
        var dispatcher: CoroutineDispatcher = Dispatchers.IO

        private val keys = settingKeys()
        val ACTIVE_EMERGENCY = keys.activeEmergency
        val EMERGENCY_AWAY_FROM = keys.emergencyAwayFrom
        val NOTIFICATIONS_PROMPTED = keys.notificationsPrompted
        val DEVICE_ID = keys.deviceId
        val THEME_PREFERENCE = keys.themePreference
        val LANGUAGE_PREFERENCE = keys.languagePreference

        /** nijii.db at [path], the file the KMP and RN apps kept; adopts and migrates what they left. */
        fun open(path: String): Store = Store(CoreStore.open(path))

        /** A private database that lives as long as the Store (previews and tests). */
        fun inMemory(): Store = Store(CoreStore.openInMemory())
    }
}
