package com.example.blackout.data.repository

import com.example.blackout.data.AppDatabase
import com.example.blackout.data.entities.Category
import com.example.blackout.data.entities.Document
import com.example.blackout.data.entities.DocumentCategory
import com.example.blackout.data.entities.DocumentVersion
import com.example.blackout.data.entities.TextSnippet
import kotlinx.coroutines.flow.Flow

class DocumentRepository(db: AppDatabase) {

    private val docDao = db.documentDao()
    private val versionDao = db.versionDao()
    private val categoryDao = db.categoryDao()
    private val snippetDao = db.textSnippetDao()

    val allDocuments: Flow<List<Document>> = docDao.getAllDocuments()
    val allSnippets: Flow<List<TextSnippet>> = snippetDao.getAllSnippets()
    val allCategories: Flow<List<DocumentCategory>> = categoryDao.getAllCategories()

    fun getDocumentsForCategoryFlow(category: Category): Flow<List<Document>> =
        docDao.getDocumentsForCategory(category.name)

    suspend fun saveDocument(
        document: Document,
        originalText: String,
        redactedText: String,
        categories: List<Category>,
    ): String {
        val categoriesJson = categories.joinToString(", ") { it.label }
        docDao.insertDocument(document)
        versionDao.insertVersion(
            DocumentVersion(
                documentId = document.id,
                name = "Original",
                redactedContent = originalText,
                activeCategories = "",
                isOriginal = true,
            )
        )
        versionDao.insertVersion(
            DocumentVersion(
                documentId = document.id,
                name = "Fully redacted",
                redactedContent = redactedText,
                activeCategories = categoriesJson,
            )
        )
        categories.forEach { cat ->
            categoryDao.insertCategory(DocumentCategory(documentId = document.id, category = cat))
        }
        return document.id
    }

    suspend fun saveVersion(version: DocumentVersion) = versionDao.insertVersion(version)

    fun getVersionsForDocument(documentId: String): Flow<List<DocumentVersion>> =
        versionDao.getVersionsForDocument(documentId)

    fun getCategoriesForDocument(documentId: String): Flow<List<DocumentCategory>> =
        categoryDao.getCategoriesForDocument(documentId)

    suspend fun getDocumentsForCategory(category: Category): List<Document> {
        val ids = categoryDao.getDocumentIdsForCategory(category.name)
        return ids.mapNotNull { docDao.getDocumentById(it) }
    }

    suspend fun deleteDocument(document: Document) {
        categoryDao.deleteCategoriesForDocument(document.id)
        versionDao.deleteVersionsForDocument(document.id)
        docDao.deleteDocument(document)
    }

    suspend fun updateDocument(document: Document) = docDao.updateDocument(document)

    suspend fun getDocumentById(id: String): Document? = docDao.getDocumentById(id)

    suspend fun saveSnippet(snippet: TextSnippet) = snippetDao.insertSnippet(snippet)
    suspend fun deleteSnippet(snippet: TextSnippet) = snippetDao.deleteSnippet(snippet)
}
