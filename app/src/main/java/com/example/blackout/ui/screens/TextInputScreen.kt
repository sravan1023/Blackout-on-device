package com.example.blackout.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.blackout.engine.RedactionMode
import com.example.blackout.navigation.Routes
import com.example.blackout.ui.RedactionUiState
import com.example.blackout.ui.RedactionViewModel
import com.example.blackout.ui.theme.BackgroundWarm
import com.example.blackout.ui.theme.NavyPrimary
import com.example.blackout.ui.theme.TealAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextInputScreen(
    redactionViewModel: RedactionViewModel,
    navController: NavController,
) {
    val uiState by redactionViewModel.uiState.collectAsState()
    var inputText by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    val isLoading = uiState is RedactionUiState.Loading ||
        uiState is RedactionUiState.Initializing ||
        uiState is RedactionUiState.PipelineProgress

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is RedactionUiState.Loading,
            is RedactionUiState.PipelineProgress -> navController.navigate(Routes.RESULT) {
                launchSingleTop = true
            }
            is RedactionUiState.Success -> navController.navigate(Routes.RESULT) {
                launchSingleTop = true
            }
            is RedactionUiState.Error -> snackbarHostState.showSnackbar(state.message)
            else -> {}
        }
    }

    Scaffold(
        containerColor = BackgroundWarm,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Type or paste text",
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
                actions = {
                    TextButton(onClick = {
                        clipboard.getText()?.text?.let { inputText = it }
                    }) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = null, tint = TealAccent, modifier = Modifier.size(18.dp))
                        Text(" Paste", color = TealAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWarm),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            var sampleExpanded by remember { mutableStateOf(false) }
            var selectedSample by rememberSaveable { mutableStateOf("") }

            ExposedDropdownMenuBox(
                expanded = sampleExpanded,
                onExpandedChange = { sampleExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedSample.ifEmpty { "Choose a sample…" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Pre-fill with sample text") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sampleExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealAccent,
                        unfocusedBorderColor = Color(0xFFDDDDDD),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                    ),
                )
                ExposedDropdownMenu(
                    expanded = sampleExpanded,
                    onDismissRequest = { sampleExpanded = false },
                ) {
                    RedactionMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(mode.label, fontWeight = FontWeight.Medium)
                                    Text(
                                        mode.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF888888),
                                    )
                                }
                            },
                            onClick = {
                                selectedSample = mode.label
                                inputText = mode.sampleText
                                sampleExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Paste or type sensitive text here…", color = Color(0xFFAAAAAA)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                enabled = !isLoading,
                maxLines = Int.MAX_VALUE,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TealAccent,
                    unfocusedBorderColor = Color(0xFFDDDDDD),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                ),
            )
            Spacer(Modifier.height(16.dp))
            if (isLoading) {
                Row(
                    modifier = Modifier.padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = TealAccent)
                    Text(
                        "  Redacting…",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF888888),
                    )
                }
            }
            Button(
                onClick = { redactionViewModel.redactText(inputText) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !isLoading && inputText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            ) {
                Text("Redact", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
