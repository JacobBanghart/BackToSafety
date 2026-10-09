package com.backtosafety.core.db

import androidx.room.Room
import androidx.room.RoomDatabase
import com.backtosafety.core.data.Store
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/** Where the RN app's expo-sqlite kept nijii.db on iOS: <Documents>/SQLite/ (spec/storage.md). */
@OptIn(ExperimentalForeignApi::class)
fun databasePath(): String {
    val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
    val dir = "$documents/SQLite"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    return "$dir/$DATABASE_NAME"
}

fun databaseBuilder(path: String): RoomDatabase.Builder<AppDatabase> =
    Room.databaseBuilder<AppDatabase>(name = path).setQueryCoroutineContext(Dispatchers.IO)

/** The SwiftUI app's entry point to the data: the store over the app's database. */
fun openStore(): Store = openStoreAt(databasePath())

/** A store on a database file of your choosing (the snapshot tests use a temporary one). */
fun openStoreAt(path: String): Store = Store(openAppDatabase(path, databaseBuilder(path)))
