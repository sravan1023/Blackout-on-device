package com.example.blackout.ui.screens

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.blackout.ui.components.CategoryPill
import com.example.blackout.ui.components.RedactionHeatmap
import com.example.blackout.ui.theme.BackgroundWarm
import com.example.blackout.ui.theme.CardWhite
import com.example.blackout.ui.theme.NavyPrimary
import com.example.blackout.ui.theme.TealAccent
import com.example.blackout.ui.viewmodel.DocumentViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextSnippetsScreen(
    docViewModel: DocumentViewModel,
    navController: NavController,
) {
    val snippets by docViewModel.allSnippets.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<TextSnippet?>(null) }

    Scaffold(
        containerColor = BackgroundWarm,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Text snippets",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = NavyPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NavyPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWarm),
            )
        },
    ) { padding ->
        if (snippets.isEmpty()) {
            EmptySnippets(modifier = Modifier.fillMaxSize().padding(padding)) {
                navController.navigate(Routes.TEXT_INPUT)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(snippets, key = { it.id }) { snippet ->
                    SnippetCard(
                        snippet = snippet,
                        onShare = { shareText(context, snippet.redactedText, snippet.title) },
                        onDelete = { pendingDelete = snippet },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    pendingDelete?.let { snippet ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete snippet?") },
            text = { Text("\"${snippet.title}\" will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    docViewModel.deleteSnippet(snippet)
                    pendingDelete = null
                }) { Text("Delete", color = Color(0xFFB42318)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SnippetCard(
    snippet: TextSnippet,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
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
                Text(
                    snippet.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NavyPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = TealAccent, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFB42318), modifier = Modifier.size(18.dp))
                }
            }

            RedactionHeatmap(redactedText = snippet.redactedText.take(280))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(snippet.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF888888),
                )
                Text(
                    "•",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFCCCCCC),
                )
                Text(
                    "$fieldCount field${if (fieldCount != 1) "s" else ""} redacted",
                    style = MaterialTheme.typography.labelSmall,
                    color = TealAccent,
                )
                if (category != null) {
                    Spacer(Modifier.weight(1f))
                    CategoryPill(category = category)
                }
            }
        }
    }
}

@Composable
private fun EmptySnippets(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✂️", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp))
            Text(
                "No text snippets yet",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF888888),
            )
            Text(
                "Save quick redactions from typed text for fast reuse",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFAAAAAA),
            )
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onAdd) {
                Text("Add a snippet", color = TealAccent, fontWeight = FontWeight.Medium)
            }
        }
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
