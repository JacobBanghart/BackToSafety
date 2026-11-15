import Foundation
import os

/// Where the RN app's expo-sqlite kept nijii.db on iOS, and where the KMP app kept it:
/// <Documents>/SQLite/ (spec/storage.md). The core opens the same file, so an update keeps data.
/// The directory is kept out of iCloud and computer backups, as Android's backup rules leave the
/// database out ("Your information never leaves this device"). Done on every call, so a
/// directory the RN app already had backed up is excluded too; on a directory it covers what's
/// created inside later (the database's -wal and -shm).
func databasePath() -> String {
    let documents = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true).first!
    let dir = documents + "/SQLite"
    try? FileManager.default.createDirectory(atPath: dir, withIntermediateDirectories: true)
    var url = URL(fileURLWithPath: dir, isDirectory: true)
    var values = URLResourceValues()
    values.isExcludedFromBackup = true
    try? url.setResourceValues(values)
    return dir + "/nijii.db"
}

private let storeQueue = DispatchQueue(label: "com.backtosafety.store", qos: .userInitiated)

/// The app's data, over the Rust core's `Store`, awaited as the Kotlin one's suspend functions
/// were: each call runs on a serial queue (calls apply in the order made, off the main thread).
///
/// If the database can't be opened (a file from a newer app, a corrupt or unreadable one) the
/// app still starts, but this store is *failed*: every call throws the open error, so each
/// save surfaces the screens' existing save-failed error (and `save_failed`), as the KMP app
/// did when Room failed on every query. Nothing is ever accepted and then lost.
final class AppStore: Sendable {
    private let core: Result<Store, Error>

    init(_ store: Store) { core = .success(store) }
    init(failed error: Error) { core = .failure(error) }

    /// Opens the database at `path` (adopting an RN database, migrating older schemas).
    static func open(path: String = databasePath()) -> AppStore {
        do {
            return try AppStore(Store.open(path: path))
        } catch {
            Logger(subsystem: "com.backtosafety.app", category: "store").error("store open failed: \(String(describing: error), privacy: .public)")
            return AppStore(failed: error)
        }
    }

    /// The error opening failed with, if it did.
    var openError: Error? {
        if case let .failure(error) = core { return error }
        return nil
    }

    /// The store EmergencyAway keeps its settings in. When opening failed it's a scratch
    /// in-memory one: the countdown alerts then have nothing to schedule (the emergency can't
    /// be saved, see above), and nothing the user entered reaches it.
    private(set) lazy var awayCore: Store = (try? core.get()) ?? (try! Store.openInMemory())

    /// Seeds, and reports data_migrated if opening migrated an older database.
    func seedAndReportMigration() async throws {
        try await onQueue { try BackToSafety.seedAndReportMigration(store: $0) }
    }

    /// app_ready: the core's AppReady with this store's readiness, or, with no store, the
    /// time and device properties alone.
    func reportAppReady(_ ready: AppReady, startupMs: Int64, device: [String: PropValue]) {
        switch core {
        case let .success(store): _ = ready.report(startupMs: startupMs, store: store, device: device)
        case .failure: analyticsTrack(event: .appReady, properties: device.merging(["startup_ms": .integer(value: startupMs)]) { a, _ in a })
        }
    }

    private func onQueue<T: Sendable>(_ body: @escaping @Sendable (Store) throws -> T) async throws -> T {
        let store = try core.get()
        return try await withCheckedThrowingContinuation { continuation in
            storeQueue.async {
                continuation.resume(with: Result { try body(store) })
            }
        }
    }

    func activeEmergency() async throws -> ActiveEmergency? { try await onQueue { try $0.activeEmergency() } }
    func addContact(contact: ContactEntity, sortOrder: Int32?) async throws -> Int64 { try await onQueue { try $0.addContact(contact: contact, sortOrder: sortOrder) } }
    func addDestination(destination: DestinationEntity, sortOrder: Int32?) async throws -> Int64 { try await onQueue { try $0.addDestination(destination: destination, sortOrder: sortOrder) } }
    func clearActiveEmergency() async throws { try await onQueue { try $0.clearActiveEmergency() } }
    func clearAllData() async throws { try await onQueue { try $0.clearAllData() } }
    func completeStep(step: String) async throws { try await onQueue { try $0.completeStep(step: step) } }
    func contacts() async throws -> [ContactEntity] { try await onQueue { try $0.contacts() } }
    func createIncident(startedAt: String) async throws -> Int64 { try await onQueue { try $0.createIncident(startedAt: startedAt) } }
    func dataMigratedProperties(fromVersion: Int32, durationMs: Int64) async throws -> [String: PropValue] { try await onQueue { try $0.dataMigratedProperties(fromVersion: fromVersion, durationMs: durationMs) } }
    func deleteContact(id: Int64) async throws { try await onQueue { try $0.deleteContact(id: id) } }
    func deleteDestination(id: Int64) async throws { try await onQueue { try $0.deleteDestination(id: id) } }
    func destinations() async throws -> [DestinationEntity] { try await onQueue { try $0.destinations() } }
    func deviceId() async throws -> String { try await onQueue { try $0.deviceId() } }
    func emergencyContacts() async throws -> [ContactEntity] { try await onQueue { try $0.emergencyContacts() } }
    func incidents() async throws -> [IncidentEntity] { try await onQueue { try $0.incidents() } }
    func isOnboarded() async throws -> Bool { try await onQueue { try $0.isOnboarded() } }
    func onboardingSteps() async throws -> [OnboardingStepEntity] { try await onQueue { try $0.onboardingSteps() } }
    func profile() async throws -> Profile? { try await onQueue { try $0.profile() } }
    func profileEntity() async throws -> ProfileEntity? { try await onQueue { try $0.profileEntity() } }
    func putSetting(key: String, value: String) async throws { try await onQueue { try $0.putSetting(key: key, value: value) } }
    func readinessProperties() async throws -> [String: PropValue] { try await onQueue { try $0.readinessProperties() } }
    func recordIncident(incidentId: Int64?, startedAt: String, checkedSteps: [String], wearing: String, outcome: String?, endedAt: String?) async throws -> Int64 { try await onQueue { try $0.recordIncident(incidentId: incidentId, startedAt: startedAt, checkedSteps: checkedSteps, wearing: wearing, outcome: outcome, endedAt: endedAt) } }
    func reorderContacts(ordered: [ContactEntity]) async throws { try await onQueue { try $0.reorderContacts(ordered: ordered) } }
    func reorderDestinations(ordered: [DestinationEntity]) async throws { try await onQueue { try $0.reorderDestinations(ordered: ordered) } }
    func safetyChecks() async throws -> [SafetyCheckEntity] { try await onQueue { try $0.safetyChecks() } }
    func saveActiveEmergency(emergency: ActiveEmergency) async throws { try await onQueue { try $0.saveActiveEmergency(emergency: emergency) } }
    func saveProfileEntity(entity: ProfileEntity) async throws { try await onQueue { try $0.saveProfileEntity(entity: entity) } }
    func seed() async throws { try await onQueue { try $0.seed() } }
    func setting(key: String) async throws -> String? { try await onQueue { try $0.setting(key: key) } }
    func updateContact(contact: ContactEntity) async throws { try await onQueue { try $0.updateContact(contact: contact) } }
    func updateDestination(destination: DestinationEntity) async throws { try await onQueue { try $0.updateDestination(destination: destination) } }
    /// A partial profile save: given fields are set (nil clears), the rest kept; in one transaction.
    func updateProfile(values: [ProfileField: String?]) async throws { try await onQueue { try $0.updateProfile(values: values) } }

    /// Removes the photo files other than the one kept: the core calls it after a save that
    /// changed the photo, and after Delete Account (keeping none).
    func setPhotoCleaner(_ clean: @escaping @Sendable (String?) -> Void) {
        try? core.get().setPhotoCleaner(cleaner: ClosureCleaner(clean))
    }

    /// Test seam: every later call fails, as on a database that won't open.
    func closeDatabase() { try? core.get().closeDatabase() }

    /// Test seam: raw SQL, to break one table (and mend it) under a screen.
    func executeSql(_ sql: String) async throws { try await onQueue { try $0.executeSql(sql: sql) } }
}

private final class ClosureCleaner: PhotoCleaner {
    private let body: @Sendable (String?) -> Void
    init(_ body: @escaping @Sendable (String?) -> Void) { self.body = body }
    func clean(keeping: String?) throws { body(keeping) }
}
