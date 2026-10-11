import Foundation

/// The core's Store is synchronous. The app awaits it, as it did the Kotlin one's suspend
/// functions: these overloads run the call on a serial queue (calls apply in the order made,
/// off the main thread) and resume the caller. In an async context Swift prefers them.
private let storeQueue = DispatchQueue(label: "com.backtosafety.store", qos: .userInitiated)

extension Store {
    private func onQueue<T: Sendable>(_ body: @escaping @Sendable (Store) throws -> T) async throws -> T {
        try await withCheckedThrowingContinuation { continuation in
            storeQueue.async {
                continuation.resume(with: Result { try body(self) })
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
