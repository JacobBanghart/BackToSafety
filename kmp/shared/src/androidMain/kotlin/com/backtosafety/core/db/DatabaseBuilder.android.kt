package com.backtosafety.core.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

/** Where the RN app's expo-sqlite kept nijii.db: <filesDir>/SQLite/ (spec/storage.md). */
fun databasePath(context: Context): String =
    File(context.filesDir, "SQLite/$DATABASE_NAME").also { it.parentFile?.mkdirs() }.absolutePath

fun databaseBuilder(context: Context, path: String): RoomDatabase.Builder<AppDatabase> =
    Room.databaseBuilder<AppDatabase>(context.applicationContext, name = path)
