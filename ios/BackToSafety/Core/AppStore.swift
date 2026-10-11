import Foundation
import os

/// Where the RN app's expo-sqlite kept nijii.db on iOS, and where the KMP app kept it:
/// <Documents>/SQLite/ (spec/storage.md). The core opens the same file, so an update keeps data.
func databasePath() -> String {
    let documents = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true).first!
    let dir = documents + "/SQLite"
    try? FileManager.default.createDirectory(atPath: dir, withIntermediateDirectories: true)
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
    func updateProfile(values: [String: String?]) async throws { try await onQueue { try $0.updateProfile(values: values) } }
}
