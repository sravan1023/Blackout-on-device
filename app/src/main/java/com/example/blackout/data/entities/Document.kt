package com.example.blackout.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class FileType { IMAGE, PDF, TEXT }

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val originalFilePath: String,
    val fileType: FileType,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
