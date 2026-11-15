@testable import BackToSafety
import SQLite3
import XCTest

/// A database the core can't use (not a database, or from a newer app) must not turn into a
/// store that accepts saves and forgets them. Opening goes through (so the app starts, as on
/// Android) and every call throws, so the screens show their save-failed errors, as the KMP
/// app's Room did when it failed on every query.
final class FailedStoreTests: XCTestCase {
    private func tempPath() -> String { NSTemporaryDirectory() + "failed-store-\(UUID().uuidString).db" }

    private func assertEverySaveThrows(_ store: AppStore, file: StaticString = #filePath, line: UInt = #line) async {
        do {
            try await store.updateProfile(values: [.fullName: "Margaret"])
            XCTFail("updateProfile succeeded on a store that failed to open", file: file, line: line)
        } catch {}
        do {
            _ = try await store.addContact(contact: ContactEntity(
                id: 0, name: "John", phone: "5551234567", relationship: nil, role: nil, address: nil,
                notifyOnEmergency: true, shareMedicalInfo: false, notes: nil, sortOrder: 0, createdAt: nil, updatedAt: nil
            ), sortOrder: nil)
            XCTFail("addContact succeeded on a store that failed to open", file: file, line: line)
        } catch {}
        do {
            try await store.putSetting(key: "theme_preference", value: "dark")
            XCTFail("putSetting succeeded on a store that failed to open", file: file, line: line)
        } catch {}
        do {
            _ = try await store.profile()
            XCTFail("profile read succeeded on a store that failed to open", file: file, line: line)
        } catch {}
        do {
            try await store.seedAndReportMigration()
            XCTFail("seed succeeded on a store that failed to open", file: file, line: line)
        } catch {}
    }

    func testAFileThatIsNotADatabaseFailsEverySave() async throws {
        let path = tempPath()
        try Data("this is not an sqlite database, just text".utf8).write(to: URL(fileURLWithPath: path))
        await assertEverySaveThrows(AppStore.open(path: path))
        // The unreadable file is left as it was.
        XCTAssertEqual(try String(contentsOfFile: path, encoding: .utf8), "this is not an sqlite database, just text")
    }

    func testADatabaseFromANewerAppFailsEverySave() async {
        let path = tempPath()
        var db: OpaquePointer?
        XCTAssertEqual(sqlite3_open(path, &db), SQLITE_OK)
        XCTAssertEqual(sqlite3_exec(db, "CREATE TABLE t (x INTEGER); PRAGMA user_version = 999;", nil, nil, nil), SQLITE_OK)
        sqlite3_close(db)
        let store = AppStore.open(path: path)
        do {
            _ = try await store.profile()
            XCTFail("a database from a newer app was read")
        } catch {
            guard case .NewerDatabase = error as? StoreFailure else { return XCTFail("expected NewerDatabase, got \(error)") }
        }
        await assertEverySaveThrows(store)
    }

    func testAnOpenableDatabaseStillSaves() async throws {
        let store = AppStore.open(path: tempPath())
        XCTAssertNil(store.openError)
        try await store.seed()
        try await store.updateProfile(values: [.fullName: "Margaret"])
        let profile = try await store.profile()
        XCTAssertEqual(profile?.name, "Margaret")
    }
}
