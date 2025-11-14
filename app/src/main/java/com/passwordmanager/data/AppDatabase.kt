package com.passwordmanager.data


import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PasswordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun passwordDao(): PasswordDao
}