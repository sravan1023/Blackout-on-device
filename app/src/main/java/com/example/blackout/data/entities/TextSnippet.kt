package com.example.blackout.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "text_snippets")
data class TextSnippet(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val originalText: String,
    val redactedText: String,
    val activeCategories: String = "[]",
    val documentCategory: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
