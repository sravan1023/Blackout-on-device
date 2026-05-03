package com.example.blackout.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.blackout.data.entities.Document
import com.example.blackout.data.entities.DocumentVersion
import com.example.blackout.data.entities.FileType
import com.example.blackout.navigation.Routes
import com.example.blackout.ui.components.CategoryPill
import com.example.blackout.ui.components.RedactionHeatmap
import com.example.blackout.ui.theme.BackgroundWarm
import com.example.blackout.ui.theme.CardWhite
import com.example.blackout.ui.theme.Hairline
import com.example.blackout.ui.theme.NavyPrimary
import com.example.blackout.ui.theme.TealAccent
import com.example.blackout.ui.viewmodel.DocumentViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    documentId: String,
    docViewModel: DocumentViewModel,
    navController: NavController,
) {
    val context = LocalContext.current
    val document by produceState<Document?>(initialValue = null, documentId) {
        value = docViewModel.getDocumentById(documentId)
    }
    val versions by docViewModel.versionsForDocument(documentId).collectAsState(initial = emptyList())
    val docCategories by docViewModel.categoriesForDocument(documentId).collectAsState(initial = emptyList())

    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var renameValue by remember(document) { mutableStateOf(document?.name ?: "") }
    var viewingPageIndex by remember { mutableStateOf(0) }

    Scaffold(
        containerColor = BackgroundWarm,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        document?.name ?: "Document",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NavyPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NavyPrimary)
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val v = versions.getOrNull(viewingPageIndex) ?: versions.firstOrNull()
                            v?.let { shareContent(context, it.redactedContent, document?.name ?: "Redacted document") }
                        },
                        enabled = versions.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(end = 4.dp).height(36.dp),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Share", style = MaterialTheme.typography.labelMedium)
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = NavyPrimary)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = { menuOpen = false; showRename = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = Color(0xFFB42318)) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFB42318)) },
                                onClick = { menuOpen = false; showDelete = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWarm),
            )
        },
    ) { padding ->
        if (document == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Loading…", color = Color(0xFF888888))
            }
            return@Scaffold
        }

        val doc = document!!
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Meta header
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(doc.createdAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF888888),
                        )
                        Text(
                            text = "  •  ${versions.size} version${if (versions.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF888888),
                        )
                        Text(
                            text = "  •  ${doc.fileType.name.lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF888888),
                        )
                    }
                    if (docCategories.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            docCategories.forEach { cat -> CategoryPill(category = cat.category) }
                        }
                    }
                }
            }

            // Version stack pager
            if (versions.isNotEmpty()) {
                item {
                    val pagerState = rememberPagerState(pageCount = { versions.size })
                    LaunchedEffect(pagerState.currentPage) {
                        viewingPageIndex = pagerState.currentPage
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "VERSIONS",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFAAAAAA),
                            fontWeight = FontWeight.Medium,
                        )
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth(),
                            pageSpacing = 12.dp,
                        ) { page ->
                            val v = versions[page]
                            Surface(
                                color = CardWhite,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            v.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = NavyPrimary,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = TealAccent.copy(alpha = 0.12f),
                                        ) {
                                            Text(
                                                "Viewing",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TealAccent,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                    if (versions.size > 1) {
                                        Text(
                                            "Swipe to switch versions",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFBBBBBB),
                                        )
                                    }
                                    HorizontalDivider(color = Hairline)
                                    if (doc.fileType == FileType.IMAGE) {
                                        val bitmap = remember(v.redactedContent) {
                                            if (v.redactedContent.startsWith("/")) {
                                                BitmapFactory.decodeFile(v.redactedContent)
                                            } else null
                                        }
                                        if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = v.name,
                                                modifier = Modifier.fillMaxWidth(),
                                                contentScale = ContentScale.FillWidth,
                                            )
                                        } else {
                                            Text(
                                                v.redactedContent.take(400),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF555555),
                                            )
                                        }
                                    } else {
                                        RedactionHeatmap(redactedText = v.redactedContent.take(500))
                                    }
                                }
                            }
                        }
                        // Pagination dots
                        if (versions.size > 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                repeat(versions.size) { idx ->
                                    val isActive = pagerState.currentPage == idx
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 3.dp)
                                            .size(if (isActive) 10.dp else 6.dp)
                                            .background(
                                                if (isActive) TealAccent else Color(0xFFCCCCCC),
                                                CircleShape,
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Versions list header
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "ALL VERSIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFAAAAAA),
                    fontWeight = FontWeight.Medium,
                )
            }

            // Versions list
            if (versions.isEmpty()) {
                item {
                    Text(
                        "No versions yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF888888),
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                item {
                    Surface(color = CardWhite, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column {
                            versions.forEachIndexed { idx, v ->
                                VersionRow(
                                    index = idx + 1,
                                    version = v,
                                    onShare = { shareContent(context, v.redactedContent, "${doc.name} — ${v.name}") },
                                )
                                if (idx < versions.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = Hairline)
                                }
                            }
                        }
                    }
                }
            }

            // Create new version button
            item {
                OutlinedButton(
                    onClick = {
                        val originalVersion = versions.firstOrNull { it.isOriginal }
                        if (originalVersion != null && doc.fileType == FileType.TEXT) {
                            navController.navigate(Routes.TEXT_INPUT)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.4f)),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = TealAccent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Create new version", color = TealAccent, fontWeight = FontWeight.Medium)
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // Rename dialog
    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename document") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealAccent,
                        unfocusedBorderColor = Color(0xFFDDDDDD),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    document?.let { d -> docViewModel.renameDocument(d, renameValue.ifBlank { d.name }) }
                    showRename = false
                }) { Text("Save", color = TealAccent) }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } },
        )
    }

    // Delete confirm
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete document?") },
            text = { Text("This removes the document and all of its versions. The original file will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    document?.let { d ->
                        docViewModel.deleteDocument(d)
                    }
                    showDelete = false
                    navController.navigateUp()
                }) { Text("Delete", color = Color(0xFFB42318)) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun VersionRow(
    index: Int,
    version: DocumentVersion,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = if (version.isOriginal) Color(0xFFEEEEEE) else TealAccent.copy(alpha = 0.15f),
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (version.isOriginal) Color(0xFF888888) else TealAccent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                version.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = NavyPrimary,
            )
            val isImage = version.redactedContent.startsWith("/")
            val subtitle = if (isImage) {
                if (version.isOriginal) "Original image" else "Redacted image"
            } else {
                val previewText = version.redactedContent.replace("\n", " ").take(60)
                previewText + if (version.redactedContent.length > 60) "…" else ""
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF888888))
            if (version.activeCategories != "[]" && version.activeCategories.isNotBlank()) {
                Text(
                    version.activeCategories.removeSurrounding("[", "]").replace("\"", ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = TealAccent,
                )
            }
        }
        IconButton(onClick = onShare) {
            Icon(
                Icons.Default.Share,
                contentDescription = "Share version",
                tint = TealAccent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun shareContent(context: android.content.Context, content: String, subject: String) {
    if (content.startsWith("/")) {
        val file = java.io.File(content)
        if (file.exists()) {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, subject)
                clipData = android.content.ClipData.newUri(
                    context.contentResolver, subject, uri
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share redacted image"))
            return
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, content)
    }
    context.startActivity(Intent.createChooser(intent, "Share redacted text"))
}
