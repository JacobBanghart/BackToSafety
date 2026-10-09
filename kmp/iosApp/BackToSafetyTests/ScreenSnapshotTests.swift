@testable import BackToSafety
import Shared
import SnapshotTesting
import SwiftUI
import XCTest

/// Every main screen rendered on a simulator and compared with its reference image in
/// __Snapshots__, in light, dark and large text, as the Android snapshot tests do. CI's macOS
/// runner is the reference environment (simulator rendering differs between Xcode versions):
/// a deliberate change re-records there, see docs/ARCHITECTURE.md.
@MainActor
final class ScreenSnapshotTests: XCTestCase {
    private var model: AppModel!

    override func setUp() async throws {
        NSTimeZone.default = TimeZone(identifier: "UTC")!
        AppClock.shared.testSeamsEnabled = true
        AppClock.shared.freeze(atMs: 1_791_302_400_000) // 2026-10-06 16:00 UTC
        let path = NSTemporaryDirectory() + "snapshots-\(UUID().uuidString).db"
        let store = DatabaseBuilder_iosKt.openStoreAt(path: path)
        try await store.seed()
        try await store.updateProfile(values: [
            "name": "Margaret Smith", "nickname": "Maggie", "dateOfBirth": "03/15/1940",
            "height": "5'6\"", "medicalConditions": "Moderate Alzheimer's", "dominantHand": "left",
        ])
        _ = try await store.addContact(
            contact: ContactEntity(
                id: 0, name: "John Smith", phone: "(555) 123-4567", relationship: nil, role: "primary_caregiver",
                address: nil, notifyOnEmergency: true, shareMedicalInfo: false, notes: nil, sortOrder: 0,
                createdAt: nil, updatedAt: nil
            ),
            sortOrder: nil
        )
        _ = try await store.addDestination(
            d: DestinationEntity(
                id: 0, name: "Riverside Park", address: "100 River Rd", latitude: nil, longitude: nil,
                category: "water", reason: nil, distanceFromHome: nil, riskLevel: "high", notes: nil, sortOrder: 0,
                createdAt: nil, updatedAt: nil
            ),
            sortOrder: nil
        )
        model = AppModel(store: store)
        model.onboarded = true
    }

    /// Hosts the screen in a window, lets its .task loads finish, and compares it.
    private func snapshot(_ view: some View, file: StaticString = #filePath, testName: String = #function, line: UInt = #line) async throws {
        for mode in ["light", "dark", "large-text"] {
            let dark = mode == "dark"
            let root = view
                .environmentObject(model)
                .environment(\.appColors, AppColors(isDark: dark))
                .environment(\.dynamicTypeSize, mode == "large-text" ? .xxxLarge : .large)
            let controller = UIHostingController(rootView: root)
            controller.overrideUserInterfaceStyle = dark ? .dark : .light
            let window = UIWindow(frame: UIScreen.main.bounds)
            window.rootViewController = controller
            window.makeKeyAndVisible()
            try await Task.sleep(for: .seconds(1))
            assertSnapshot(
                of: controller, as: .image(drawHierarchyInKeyWindow: true, perceptualPrecision: 0.98),
                named: mode, file: file, testName: testName, line: line
            )
            window.isHidden = true
        }
    }

    func testWelcome() async throws { try await snapshot(WelcomeView(t: model.t("onboarding"))) }

    func testHome() async throws {
        try await snapshot(HomeView(t: model.t("home"), emergencyNumber: model.t("common")("emergencyNumber")))
    }

    func testSettings() async throws { try await snapshot(SettingsView(t: model.t("settings"), tCommon: model.t("common"))) }

    func testReadout() async throws { try await snapshot(ReadoutView(t: model.t("readout"), tCommon: model.t("common"))) }

    func testContacts() async throws { try await snapshot(ContactsView(t: model.t("contacts"), tCommon: model.t("common"))) }

    func testPlaces() async throws { try await snapshot(DestinationsView(t: model.t("destinations"), tCommon: model.t("common"))) }

    func testProfile() async throws { try await snapshot(ProfileView(t: model.t("profile"), tCommon: model.t("common"))) }

    func testEmergency() async throws { try await snapshot(EmergencyView(t: model.t("emergency"), tCommon: model.t("common"))) }
}
