package com.example.blackout.data.dao

import androidx.room.*
import com.example.blackout.data.entities.DocumentVersion
import kotlinx.coroutines.flow.Flow

@Dao
interface VersionDao {
    @Query("SELECT * FROM document_versions WHERE documentId = :documentId ORDER BY createdAt ASC")
    fun getVersionsForDocument(documentId: String): Flow<List<DocumentVersion>>

    @Query("SELECT * FROM document_versions WHERE documentId = :documentId ORDER BY createdAt ASC")
    suspend fun getVersionsForDocumentOnce(documentId: String): List<DocumentVersion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: DocumentVersion)

    @Delete
    suspend fun deleteVersion(version: DocumentVersion)

    @Query("DELETE FROM document_versions WHERE documentId = :documentId")
    suspend fun deleteVersionsForDocument(documentId: String)
}
