package com.example.blackout.data.dao

import androidx.room.*
import com.example.blackout.data.entities.DocumentCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM document_categories WHERE documentId = :documentId")
    fun getCategoriesForDocument(documentId: String): Flow<List<DocumentCategory>>

    @Query("SELECT * FROM document_categories WHERE documentId = :documentId")
    suspend fun getCategoriesForDocumentOnce(documentId: String): List<DocumentCategory>

    @Query("SELECT documentId FROM document_categories WHERE category = :category")
    suspend fun getDocumentIdsForCategory(category: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: DocumentCategory)

    @Query("DELETE FROM document_categories WHERE documentId = :documentId")
    suspend fun deleteCategoriesForDocument(documentId: String)

    @Query("SELECT * FROM document_categories")
    fun getAllCategories(): Flow<List<DocumentCategory>>
}
