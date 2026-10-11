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

/// The store over the app's database (adopting an RN database and migrating older schemas on
/// open). If it can't be opened (a database from a newer app, a full disk) the app still
/// starts, on an empty in-memory store: its screens work, and nothing is written to the file.
func openAppStore() -> Store {
    do {
        return try Store.open(path: databasePath())
    } catch {
        Logger(subsystem: "com.backtosafety.app", category: "store").error("store open failed: \(String(describing: error), privacy: .public)")
        return try! Store.openInMemory()
    }
}
