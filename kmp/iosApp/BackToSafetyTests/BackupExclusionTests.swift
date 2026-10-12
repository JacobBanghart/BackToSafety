@testable import BackToSafety
import Shared
import XCTest

/// The database and the profile photos stay out of iCloud and computer backups, as Android's
/// backup rules leave them out (including what the RN app left behind, which was backed up).
final class BackupExclusionTests: XCTestCase {
    private var documents: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }

    private func isExcluded(_ url: URL) -> Bool? {
        try? url.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup
    }

    private func setExcluded(_ excluded: Bool, _ url: URL) throws {
        var url = url
        var values = URLResourceValues()
        values.isExcludedFromBackup = excluded
        try url.setResourceValues(values)
    }

    func testTheDatabaseDirectoryIsExcludedWhenItsPathIsResolved() throws {
        let directory = documents.appendingPathComponent("SQLite", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try setExcluded(false, directory)
        XCTAssertEqual(isExcluded(directory), false)

        let path = DatabaseBuilder_iosKt.databasePath()

        XCTAssertEqual(path, directory.appendingPathComponent("nijii.db").path)
        XCTAssertEqual(isExcluded(directory), true)
    }

    func testPhotosAlreadyInDocumentsAreExcludedAtLaunch() throws {
        let photo = documents.appendingPathComponent("profile_photo_backup-test.jpg")
        let other = documents.appendingPathComponent("not_a_photo_backup-test.txt")
        try Data([1]).write(to: photo)
        try Data([1]).write(to: other)
        defer {
            try? FileManager.default.removeItem(at: photo)
            try? FileManager.default.removeItem(at: other)
        }
        XCTAssertEqual(isExcluded(photo), false)

        excludePhotosFromBackup()

        XCTAssertEqual(isExcluded(photo), true)
        XCTAssertEqual(isExcluded(other), false)
    }
}
