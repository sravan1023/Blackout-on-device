package com.example.blackout.data.dao

import androidx.room.*
import com.example.blackout.data.entities.TextSnippet
import kotlinx.coroutines.flow.Flow

@Dao
interface TextSnippetDao {
    @Query("SELECT * FROM text_snippets ORDER BY createdAt DESC")
    fun getAllSnippets(): Flow<List<TextSnippet>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippet(snippet: TextSnippet)

    @Delete
    suspend fun deleteSnippet(snippet: TextSnippet)
}
