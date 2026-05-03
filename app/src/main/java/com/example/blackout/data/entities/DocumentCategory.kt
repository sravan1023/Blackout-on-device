package com.example.blackout.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class Category(val label: String, val emoji: String) {
    MEDICAL("Medical", "🏥"),
    FINANCIAL("Financial", "💰"),
    TAX("Tax", "📄"),
    HOME("Home / Lease", "🏠"),
    INSURANCE("Insurance", "🛡️"),
    ID_DOCUMENTS("ID Documents", "🪪"),
    IMMIGRATION("Immigration", "✈️"),
    AUTO("Auto", "🚗"),
    LEGAL("Legal", "⚖️"),
    OTHER("Other", "📁"),
}

@Entity(tableName = "document_categories")
data class DocumentCategory(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val documentId: String,
    val category: Category,
)
