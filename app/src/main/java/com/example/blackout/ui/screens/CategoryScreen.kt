package com.example.blackout.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.blackout.data.entities.Category
import com.example.blackout.navigation.Routes
import com.example.blackout.ui.components.DocumentRow
import com.example.blackout.ui.components.pillColors
import com.example.blackout.ui.theme.BackgroundWarm
import com.example.blackout.ui.theme.CardWhite
import com.example.blackout.ui.theme.Hairline
import com.example.blackout.ui.theme.NavyPrimary
import com.example.blackout.ui.viewmodel.DocumentViewModel

@Composable
fun CategoryScreen(
    categoryName: String,
    docViewModel: DocumentViewModel,
    navController: NavController,
) {
    val category = runCatching { Category.valueOf(categoryName) }.getOrNull()
        ?: return

    val documents by docViewModel.documentsForCategory(category).collectAsState(initial = emptyList())
    val categoryCounts by docViewModel.categoryCounts.collectAsStateWithLifecycle()

    val (textColor, bgColor) = category.pillColors()

    Scaffold(containerColor = BackgroundWarm) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            // Hero header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor)
                        .statusBarsPadding()
                        .padding(bottom = 24.dp),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        IconButton(
                            onClick = { navController.navigateUp() },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = textColor,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Surface(shape = CircleShape, color = textColor.copy(alpha = 0.15f), modifier = Modifier.size(56.dp)) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(category.emoji, style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            category.label,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor,
                        )
                        val count = categoryCounts[category] ?: 0
                        Text(
                            "${count} document${if (count != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            // Documents list
            if (documents.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(category.emoji, style = MaterialTheme.typography.headlineLarge)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No documents yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF888888),
                            )
                            Text(
                                "Tap + to add a document",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFAAAAAA),
                            )
                        }
                    }
                }
            } else {
                item {
                    Surface(color = CardWhite, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            documents.forEachIndexed { idx, doc ->
                                DocumentRow(
                                    document = doc,
                                    categories = listOf(category),
                                    onClick = { navController.navigate(Routes.documentDetail(doc.id)) },
                                )
                                if (idx < documents.lastIndex) {
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
        }
    }
}
