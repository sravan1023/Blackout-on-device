package com.example.blackout.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.blackout.data.AppDatabase
import com.example.blackout.data.entities.Category
import com.example.blackout.data.entities.Document
import com.example.blackout.data.entities.DocumentVersion
import com.example.blackout.data.entities.FileType
import com.example.blackout.data.entities.TextSnippet
import com.example.blackout.data.repository.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class DocumentViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = DocumentRepository(AppDatabase.getInstance(application))

    val allDocuments: StateFlow<List<Document>> = repo.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentDocuments: StateFlow<List<Document>> = repo.allDocuments
        .map { it.take(5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allSnippets: StateFlow<List<TextSnippet>> = repo.allSnippets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categoryCounts: StateFlow<Map<Category, Int>> = repo.allCategories
        .map { list -> list.groupBy { it.category }.mapValues { it.value.size } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun documentsForCategory(category: Category): Flow<List<Document>> =
        repo.getDocumentsForCategoryFlow(category)

    fun versionsForDocument(documentId: String): Flow<List<DocumentVersion>> =
        repo.getVersionsForDocument(documentId)

    fun categoriesForDocument(documentId: String): Flow<List<com.example.blackout.data.entities.DocumentCategory>> =
        repo.getCategoriesForDocument(documentId)

    suspend fun getDocumentById(id: String): Document? = repo.getDocumentById(id)

    fun deleteDocument(document: Document) {
        viewModelScope.launch { repo.deleteDocument(document) }
    }

    fun deleteSnippet(snippet: TextSnippet) {
        viewModelScope.launch { repo.deleteSnippet(snippet) }
    }

    fun renameDocument(document: Document, newName: String) {
        viewModelScope.launch { repo.updateDocument(document.copy(name = newName, updatedAt = System.currentTimeMillis())) }
    }

    /**
     * Persist a redacted text or image document. For images, the original bitmap is
     * written to filesDir/originals/<docId>.png. For text, no source file is stored —
     * the original text lives in the "Original" version row. Returns the new document id.
     */
    fun saveDocument(
        name: String,
        fileType: FileType,
        originalText: String,
        redactedText: String,
        categories: List<Category>,
        sourceBitmap: Bitmap? = null,
        redactedBitmap: Bitmap? = null,
        onSaved: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            val docId = UUID.randomUUID().toString()
            val originalPath = if (fileType == FileType.IMAGE && sourceBitmap != null) {
                writeBitmap(docId, "original", sourceBitmap)
            } else {
                ""
            }
            val redactedPath = if (fileType == FileType.IMAGE && redactedBitmap != null) {
                writeBitmap(docId, "redacted", redactedBitmap)
            } else {
                ""
            }
            val document = Document(
                id = docId,
                name = name.ifBlank { "Untitled" },
                originalFilePath = originalPath,
                fileType = fileType,
            )
            val effectiveOriginal = if (originalPath.isNotEmpty()) originalPath else originalText
            val effectiveRedacted = if (redactedPath.isNotEmpty()) redactedPath else redactedText
            repo.saveDocument(document, effectiveOriginal, effectiveRedacted, categories)
            onSaved(docId)
        }
    }

    fun saveAdditionalVersion(
        documentId: String,
        versionName: String,
        redactedContent: String,
        activeCategoriesJson: String = "[]",
    ) {
        viewModelScope.launch {
            repo.saveVersion(
                DocumentVersion(
                    documentId = documentId,
                    name = versionName.ifBlank { "Version" },
                    redactedContent = redactedContent,
                    activeCategories = activeCategoriesJson,
                )
            )
        }
    }

    fun saveSnippet(
        title: String,
        originalText: String,
        redactedText: String,
        category: Category? = null,
        onSaved: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            val snippet = TextSnippet(
                title = title.ifBlank { "Untitled snippet" },
                originalText = originalText,
                redactedText = redactedText,
                documentCategory = category?.name,
            )
            repo.saveSnippet(snippet)
            onSaved(snippet.id)
        }
    }

    private suspend fun writeBitmap(docId: String, tag: String, bitmap: Bitmap): String =
        withContext(Dispatchers.IO) {
            val dir = File(getApplication<Application>().filesDir, "originals").apply { mkdirs() }
            val file = File(dir, "${docId}_$tag.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                return DocumentViewModel(application) as T
            }
        }
    }
}
