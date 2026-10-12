package com.backtosafety.core.db

import androidx.room.Room
import androidx.room.RoomDatabase
import com.backtosafety.core.data.Store
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * Where the RN app's expo-sqlite kept nijii.db on iOS: <Documents>/SQLite/ (spec/storage.md).
 * The directory is kept out of iCloud and computer backups ([excludeFromBackup]), as Android's
 * backup rules leave the database out: "Your information never leaves this device". Done on
 * every call, so a directory the RN app already had backed up is excluded too.
 */
@OptIn(ExperimentalForeignApi::class)
fun databasePath(): String {
    val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
    val dir = "$documents/SQLite"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    excludeFromBackup(dir)
    return "$dir/$DATABASE_NAME"
}

/**
 * Marks the file or directory at [path] as excluded from iCloud and computer backups. On a
 * directory it covers everything inside, files created later too (the database's -wal and -shm).
 * Returns whether iOS accepted it.
 */
@OptIn(ExperimentalForeignApi::class)
fun excludeFromBackup(path: String): Boolean =
    NSURL.fileURLWithPath(path).setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)

fun databaseBuilder(path: String): RoomDatabase.Builder<AppDatabase> =
    Room.databaseBuilder<AppDatabase>(name = path).setQueryCoroutineContext(Dispatchers.IO)

/** The SwiftUI app's entry point to the data: the store over the app's database. */
fun openStore(): Store = openStoreAt(databasePath())

/** A store on a database file of your choosing (the snapshot tests use a temporary one). */
fun openStoreAt(path: String): Store = Store(openAppDatabase(path, databaseBuilder(path)))
