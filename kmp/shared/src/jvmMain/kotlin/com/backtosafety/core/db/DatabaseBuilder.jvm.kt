package com.backtosafety.core.db

import androidx.room.Room
import androidx.room.RoomDatabase

/** JVM (tests): the database is a plain file path. */
fun databaseBuilder(path: String): RoomDatabase.Builder<AppDatabase> =
    Room.databaseBuilder<AppDatabase>(name = path)
