package com.example.blackout.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.blackout.data.dao.CategoryDao
import com.example.blackout.data.dao.DocumentDao
import com.example.blackout.data.dao.TextSnippetDao
import com.example.blackout.data.dao.VersionDao
import com.example.blackout.data.entities.Document
import com.example.blackout.data.entities.DocumentCategory
import com.example.blackout.data.entities.DocumentVersion
import com.example.blackout.data.entities.TextSnippet

@Database(
    entities = [Document::class, DocumentVersion::class, DocumentCategory::class, TextSnippet::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun versionDao(): VersionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun textSnippetDao(): TextSnippetDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "blackout.db",
                ).build().also { INSTANCE = it }
            }
    }
}
