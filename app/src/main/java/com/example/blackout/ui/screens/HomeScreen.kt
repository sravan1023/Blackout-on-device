package com.example.blackout.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.blackout.data.entities.Category
import com.example.blackout.data.entities.TextSnippet
import com.example.blackout.navigation.Routes
import com.example.blackout.ui.RedactionViewModel
import com.example.blackout.ui.components.AddCategoryCard
import com.example.blackout.ui.components.CategoryCard
import com.example.blackout.ui.components.CategoryPill
import com.example.blackout.ui.components.DocumentRow
import com.example.blackout.ui.components.HudBox
import com.example.blackout.ui.components.RedactionHeatmap
import com.example.blackout.ui.components.BlackoutFab
import com.example.blackout.ui.components.BlackoutTopBar
import com.example.blackout.ui.theme.BackgroundWarm
import com.example.blackout.ui.theme.CardWhite
import com.example.blackout.ui.theme.Hairline
import com.example.blackout.ui.theme.NavyPrimary
import com.example.blackout.ui.theme.TealAccent
import com.example.blackout.ui.viewmodel.DocumentViewModel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    docViewModel: DocumentViewModel,
    redactionViewModel: RedactionViewModel,
    navController: NavController,
    initialTab: Int = 0,
) {
    val recentDocuments by docViewModel.recentDocuments.collectAsStateWithLifecycle()
    val allSnippets by docViewModel.allSnippets.collectAsStateWithLifecycle()
    val categoryCounts by docViewModel.categoryCounts.collectAsStateWithLifecycle()
    val activeBackend by redactionViewModel.activeBackend.collectAsStateWithLifecycle()
    val selectedVariant by redactionViewModel.selectedVariant.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    var showUploadSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            @Suppress("DEPRECATION")
            val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(context.contentResolver, uri)
                ) { decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
            } else {
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            redactionViewModel.redactImage(bitmap)
            navController.navigate(Routes.RESULT)
        }
    }

    if (showUploadSheet) {
        UploadSheet(
            sheetState = sheetState,
            onDismiss = { showUploadSheet = false },
            onCamera = { navController.navigate(Routes.CAMERA) },
            onGallery = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPdf = {},
            onText = { navController.navigate(Routes.TEXT_INPUT) },
        )
    }

    Scaffold(
        containerColor = BackgroundWarm,
        topBar = {
            BlackoutTopBar(
                trailing = {
                    HudBox(
                        metrics = null,
                        isLive = false,
                        backend = activeBackend,
                        selectedVariant = selectedVariant,
                        onSelectVariant = { redactionViewModel.selectModelVariant(it) },
                        availableVariants = redactionViewModel.availableVariants,
                    )
                },
            )
        },
        floatingActionButton = {
            BlackoutFab(
                onClick = {
                    if (selectedTab == 1) {
                        navController.navigate(Routes.TEXT_INPUT)
                    } else {
                        scope.launch { sheetState.show() }
                        showUploadSheet = true
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = BackgroundWarm,
                contentColor = TealAccent,
                divider = { HorizontalDivider(color = Hairline) },
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "Documents",
                            fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    selectedContentColor = TealAccent,
                    unselectedContentColor = Color(0xFF888888),
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "Text Snippets",
                            fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    selectedContentColor = TealAccent,
                    unselectedContentColor = Color(0xFF888888),
                )
            }

            if (selectedTab == 0) {
                DocumentsTab(
                    recentDocuments = recentDocuments,
                    categoryCounts = categoryCounts,
                    navController = navController,
                    docViewModel = docViewModel,
                )
            } else {
                TextSnippetsTab(
                    allSnippets = allSnippets,
                    navController = navController,
                    docViewModel = docViewModel,
                )
            }
        }
    }
}

@Composable
private fun DocumentsTab(
    recentDocuments: List<com.example.blackout.data.entities.Document>,
    categoryCounts: Map<Category, Int>,
    navController: NavController,
    docViewModel: DocumentViewModel,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // Categories header
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader(title = "Categories")
        }

        // Category cards — tiles with no documents are grayed out
        items(Category.entries.toList(), key = { it.name }) { category ->
            CategoryCard(
                category = category,
                docCount = categoryCounts[category] ?: 0,
                onClick = { navController.navigate(Routes.category(category.name)) },
            )
        }

        // Add / manage tile
        item {
            AddCategoryCard(onClick = { navController.navigate(Routes.MANAGE_CATEGORIES) })
        }

        // Recent documents header
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader(
                title = "Recent",
                action = if (recentDocuments.isNotEmpty()) "Show all" else null,
                onAction = { /* Phase 5: navigate to all docs */ },
            )
        }

        // Recent documents list (full width)
        if (recentDocuments.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyDocumentsHint()
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    color = CardWhite,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) {
                    Column {
                        recentDocuments.forEachIndexed { idx, doc ->
                            DocumentRow(
                                document = doc,
                                categories = emptyList(),
                                onClick = { navController.navigate(Routes.documentDetail(doc.id)) },
                            )
                            if (idx < recentDocuments.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 76.dp),
                                    color = Hairline,
                                )
                            }
                        }
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TextSnippetsTab(
    allSnippets: List<TextSnippet>,
    navController: NavController,
    docViewModel: DocumentViewModel,
) {
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<TextSnippet?>(null) }

    if (allSnippets.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No text snippets yet", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF888888))
                Text("Tap + to add a quick text redaction", style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAAAAA))
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { navController.navigate(Routes.TEXT_INPUT) }) {
                    Text("Add a snippet", color = TealAccent, fontWeight = FontWeight.Medium)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(allSnippets, key = { it.id }) { snippet ->
                val category = snippet.documentCategory
                    ?.let { runCatching { Category.valueOf(it) }.getOrNull() }
                val fieldCount = remember(snippet.redactedText) {
                    Regex("""\[[A-Z_]+(?:_\d+)?]""").findAll(snippet.redactedText).count()
                }
                Surface(
                    color = CardWhite,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(snippet.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = NavyPrimary, modifier = Modifier.weight(1f))
                            IconButton(onClick = { shareText(context, snippet.redactedText, snippet.title) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = TealAccent, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { pendingDelete = snippet }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFB42318), modifier = Modifier.size(18.dp))
                            }
                        }
                        RedactionHeatmap(redactedText = snippet.redactedText.take(280))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(snippet.createdAt)), style = MaterialTheme.typography.labelSmall, color = Color(0xFF888888))
                            Text("$fieldCount redacted", style = MaterialTheme.typography.labelSmall, color = TealAccent)
                            if (category != null) { Spacer(Modifier.weight(1f)); CategoryPill(category = category) }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { snippet ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete snippet?") },
            text = { Text("\"${snippet.title}\" will be removed.") },
            confirmButton = { TextButton(onClick = { docViewModel.deleteSnippet(snippet); pendingDelete = null }) { Text("Delete", color = Color(0xFFB42318)) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

private fun shareText(context: Context, text: String, subject: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share redacted snippet"))
}

@Composable
private fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = NavyPrimary,
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) {
                Text(action, style = MaterialTheme.typography.labelMedium, color = TealAccent)
            }
        }
    }
}

@Composable
private fun EmptyDocumentsHint() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📄", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "No documents yet",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF888888),
            )
            Text(
                "Tap + to add your first document",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAAAAAA),
            )
        }
    }
}
