package com.example.blackout.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "document_versions")
data class DocumentVersion(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val documentId: String,
    val name: String,
    val redactedContent: String,
    val activeCategories: String = "[]",
    val isOriginal: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
