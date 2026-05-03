package com.example.blackout.data.dao

import androidx.room.*
import com.example.blackout.data.entities.Document
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun getAllDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: String): Document?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: Document)

    @Update
    suspend fun updateDocument(document: Document)

    @Delete
    suspend fun deleteDocument(document: Document)

    @Query("""
        SELECT documents.* FROM documents
        INNER JOIN document_categories ON documents.id = document_categories.documentId
        WHERE document_categories.category = :category
        ORDER BY documents.updatedAt DESC
    """)
    fun getDocumentsForCategory(category: String): Flow<List<Document>>
}
