package com.example.blackout.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.blackout.data.entities.Category
import com.example.blackout.data.entities.FileType
import com.example.blackout.engine.PlaceholderMapper
import com.example.blackout.navigation.Routes
import com.example.blackout.ui.RedactionUiState
import com.example.blackout.ui.RedactionViewModel
import com.example.blackout.ui.components.RedactedImageView
import com.example.blackout.ui.components.InteractiveRedactionHeatmap
import com.example.blackout.ui.components.RedactionHeatmap
import com.example.blackout.ui.components.RedactionHudBar
import com.example.blackout.ui.theme.BackgroundWarm
import com.example.blackout.ui.theme.CardWhite
import com.example.blackout.ui.theme.NavyPrimary
import com.example.blackout.ui.theme.TealAccent
import com.example.blackout.ui.viewmodel.DocumentViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: RedactionViewModel,
    docViewModel: DocumentViewModel,
    navController: NavController,
) {
    val uiState by viewModel.uiState.collectAsState()

    val pendingBitmap by viewModel.pendingBitmap.collectAsState()

    when (val state = uiState) {
        is RedactionUiState.Loading,
        is RedactionUiState.Initializing -> LoadingResultScreen(navController, pendingBitmap)
        is RedactionUiState.PipelineProgress -> LoadingResultScreen(
            navController, pendingBitmap, state.step, state.totalSteps, state.label, state.round
        )
        is RedactionUiState.Success -> SuccessResultScreen(state, viewModel, docViewModel, navController)
        is RedactionUiState.Error -> ErrorResultScreen(state.message, navController) {
            viewModel.reset()
            navController.navigateUp()
        }
        is RedactionUiState.Idle,
        is RedactionUiState.ModelMissing -> {
            LaunchedEffect(Unit) { navController.navigateUp() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ErrorResultScreen(message: String, navController: NavController, onDismiss: () -> Unit) {
    Scaffold(
        containerColor = BackgroundWarm,
        topBar = {
            TopAppBar(
                title = { Text("Couldn't redact") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWarm),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("❌", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF555555),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = TealAccent)) {
                Text("Back")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadingResultScreen(
    navController: NavController,
    pendingBitmap: android.graphics.Bitmap? = null,
    step: Int = 0,
    totalSteps: Int = 4,
    label: String = "Running on-device redaction…",
    round: Int = 1,
) {
    Scaffold(
        containerColor = BackgroundWarm,
        topBar = {
            TopAppBar(
                title = { Text("Redacting…", fontWeight = FontWeight.SemiBold, color = NavyPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NavyPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWarm),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (pendingBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = pendingBitmap.asImageBitmap(),
                    contentDescription = "Captured photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    alpha = 0.6f,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }

            if (step > 0) {
                Column(
                    modifier = Modifier.padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Step dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp),
                    ) {
                        for (i in 1..totalSteps) {
                            Box(
                                modifier = Modifier
                                    .size(if (i == step) 10.dp else 8.dp)
                                    .background(
                                        when {
                                            i < step -> TealAccent
                                            i == step -> TealAccent.copy(alpha = 0.7f)
                                            else -> Color(0xFFDDDDDD)
                                        },
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                    ),
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = TealAccent, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "Step $step/$totalSteps: $label",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF888888),
                        )
                    }
                    if (round > 1) {
                        Text(
                            "Validation round $round",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFAAAAAA),
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.padding(bottom = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(color = TealAccent, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text(label, style = MaterialTheme.typography.bodySmall, color = Color(0xFF888888))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuccessResultScreen(
    success: RedactionUiState.Success,
    viewModel: RedactionViewModel,
    docViewModel: DocumentViewModel,
    navController: NavController,
) {
    val isImageMode = success.redactedBitmap != null
    var showOriginal by rememberSaveable { mutableStateOf(false) }
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val selectedVariant by viewModel.selectedVariant.collectAsStateWithLifecycle()

    var docName by rememberSaveable { mutableStateOf("") }
    val selectedCategories = rememberSaveable(saver = CategorySetSaver) {
        mutableStateOf(setOf<Category>())
    }
    var saved by rememberSaveable { mutableStateOf(false) }

    val placeholderMap = remember(success.original, success.redacted) {
        PlaceholderMapper.buildMap(success.original, success.redacted)
    }
    var revealedPlaceholders by remember { mutableStateOf(setOf<String>()) }
    var disabledCategoryGroups by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        containerColor = BackgroundWarm,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Redacting",
                        fontWeight = FontWeight.SemiBold,
                        color = NavyPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.reset()
                        navController.navigateUp()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NavyPrimary)
                    }
                },
                actions = {
                    SegmentSwitcher(
                        showOriginal = showOriginal,
                        onChange = { showOriginal = it },
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWarm),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(2.dp))

            // Navy HUD bar
            RedactionHudBar(
                backend = success.backend,
                latencyMs = success.latencyMs,
                tokenCount = success.tokenCount,
                tokensPerSecond = success.decodeTokensPerSec,
                fieldsRedacted = success.fieldsRedacted,
                timeToFirstTokenMs = success.timeToFirstTokenMs,
                modifier = Modifier.fillMaxWidth(),
                selectedVariant = selectedVariant,
                onSelectVariant = { viewModel.selectModelVariant(it) },
                availableVariants = viewModel.availableVariants,
            )

            // Category-toggle bubbles
            CategoryToggleBubbles(
                redacted = success.redacted,
                disabledGroups = disabledCategoryGroups,
                onToggle = { group ->
                    disabledCategoryGroups = if (group in disabledCategoryGroups) {
                        disabledCategoryGroups - group
                    } else {
                        disabledCategoryGroups + group
                    }
                },
            )

            // Document preview card
            Surface(
                color = CardWhite,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    AnimatedContent(
                        targetState = showOriginal,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "before_after",
                    ) { isOriginal ->
                        if (isImageMode) {
                            val displayBitmap = if (isOriginal) success.sourceBitmap else success.redactedBitmap
                            displayBitmap?.let { RedactedImageView(bitmap = it) }
                        } else {
                            if (isOriginal) {
                                Text(
                                    text = success.original,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF333333),
                                )
                            } else {
                                InteractiveRedactionHeatmap(
                                    redactedText = success.redacted,
                                    placeholderMap = placeholderMap,
                                    revealedPlaceholders = revealedPlaceholders,
                                    disabledCategories = disabledCategoryGroups,
                                    onPlaceholderClick = { tag ->
                                        revealedPlaceholders = if (tag in revealedPlaceholders) {
                                            revealedPlaceholders - tag
                                        } else {
                                            revealedPlaceholders + tag
                                        }
                                    },
                                )
                            }
                        }
                    }
                    if (!showOriginal && !isImageMode) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Tap any block to un-redact",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFAAAAAA),
                        )
                    }
                }
            }

            // Color legend
            if (!isImageMode) {
                ColorLegend()
            }

            // Copy button (text mode only)
            if (!isImageMode) {
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(success.redacted))
                        scope.launch { snackbarHostState.showSnackbar("Redacted text copied") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("Copy", style = MaterialTheme.typography.labelMedium)
                }
            }

            // ─── Save section ───
            Surface(
                color = CardWhite,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = docName,
                        onValueChange = { docName = it },
                        placeholder = {
                            Text(
                                if (isImageMode) "Version / document name…" else "Snippet name…",
                                color = Color(0xFFAAAAAA),
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealAccent,
                            unfocusedBorderColor = Color(0xFFE5E5E5),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color(0xFFFAFAFA),
                        ),
                        shape = RoundedCornerShape(10.dp),
                    )
                    CategoryChipRow(
                        selected = selectedCategories.value,
                        onToggle = { cat ->
                            selectedCategories.value = if (cat in selectedCategories.value) {
                                selectedCategories.value - cat
                            } else {
                                selectedCategories.value + cat
                            }
                        },
                    )
                    if (isImageMode) {
                        Button(
                            onClick = {
                                docViewModel.saveDocument(
                                    name = docName,
                                    fileType = FileType.IMAGE,
                                    originalText = success.original,
                                    redactedText = success.redacted,
                                    categories = selectedCategories.value.toList(),
                                    sourceBitmap = success.sourceBitmap,
                                    redactedBitmap = success.redactedBitmap,
                                ) { docId ->
                                    saved = true
                                    viewModel.reset()
                                    navController.navigate(Routes.documentDetail(docId)) {
                                        popUpTo(Routes.HOME)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = !saved,
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.padding(horizontal = 6.dp))
                            Text("Save version", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Button(
                            onClick = {
                                docViewModel.saveSnippet(
                                    title = docName.ifBlank { success.redacted.take(40) },
                                    originalText = success.original,
                                    redactedText = success.redacted,
                                    category = selectedCategories.value.firstOrNull(),
                                ) {
                                    saved = true
                                    viewModel.reset()
                                    navController.navigate("${Routes.HOME}?tab=1") {
                                        popUpTo(Routes.HOME) { inclusive = true }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = !saved,
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.padding(horizontal = 6.dp))
                            Text("Save as text snippet", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SegmentSwitcher(
    showOriginal: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFEEEEEC),
    ) {
        Row(modifier = Modifier.padding(2.dp)) {
            SegmentLabel(text = "Redacted", active = !showOriginal) { onChange(false) }
            SegmentLabel(text = "Original", active = showOriginal) { onChange(true) }
        }
    }
}

@Composable
private fun SegmentLabel(text: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (active) Color.White else Color.Transparent,
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (active) NavyPrimary else Color(0xFF8B8B8B),
            )
        }
    }
}

@Composable
private fun CategoryToggleBubbles(
    redacted: String,
    disabledGroups: Set<String>,
    onToggle: (String) -> Unit,
) {
    val categories = remember(redacted) {
        val pattern = Regex("""\[([A-Z_]+)(?:_\d+)?]""")
        val labels = pattern.findAll(redacted).map { it.groupValues[1] }.toSet()
        listOf(
            Triple("Names", Color(0xFF1B8C5E), labels.any { it in setOf("NAME", "PERSON", "PATIENT", "VICTIM", "WITNESS", "MINOR", "SOURCE", "CUSTOMER") }),
            Triple("Dates", Color(0xFF185FA5), labels.any { it == "DATE" || it == "DOB" }),
            Triple("SSN", Color(0xFFA32D2D), labels.any { it == "SSN" }),
            Triple("Financial", Color(0xFF854F0B), labels.any { it in setOf("ACCOUNT", "CARD", "ROUTING", "TAXID", "TXN", "BROKERAGE") }),
            Triple("Contact", Color(0xFF534AB7), labels.any { it in setOf("PHONE", "EMAIL", "CONTACT") }),
            Triple("Location", Color(0xFF6B4E2A), labels.any { it in setOf("LOCATION", "ADDRESS") }),
        )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        categories.forEach { (label, color, present) ->
            if (present) {
                val active = label !in disabledGroups
                ToggleBubble(
                    label = label,
                    color = color,
                    active = active,
                    onClick = { onToggle(label) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToggleBubble(label: String, color: Color, active: Boolean, onClick: () -> Unit = {}) {
    val borderColor = if (active) color else Color(0xFFDDDDDD)
    val labelColor = if (active) color else Color(0xFFBBBBBB)
    val dotColor = if (active) color else Color(0xFFCCCCCC)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(dotColor, androidx.compose.foundation.shape.CircleShape),
            )
            Spacer(Modifier.size(6.dp))
            Text(label, fontSize = 11.sp, color = labelColor, fontWeight = FontWeight.Medium)
            if (active) {
                Spacer(Modifier.size(4.dp))
                Icon(Icons.Default.Check, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun ColorLegend() {
    val items = listOf(
        "Name" to Color(0xFF0D1B2A),
        "Date" to Color(0xFF185FA5),
        "SSN" to Color(0xFFA32D2D),
        "Contact" to Color(0xFF534AB7),
        "Location" to Color(0xFF6B4E2A),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(color, androidx.compose.foundation.shape.CircleShape))
                Spacer(Modifier.size(4.dp))
                Text(label, fontSize = 10.sp, color = Color(0xFF666666))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChipRow(
    selected: Set<Category>,
    onToggle: (Category) -> Unit,
) {
    val rows = Category.entries.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { cat ->
                    FilterChip(
                        selected = cat in selected,
                        onClick = { onToggle(cat) },
                        label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TealAccent.copy(alpha = 0.15f),
                            selectedLabelColor = TealAccent,
                        ),
                    )
                }
            }
        }
    }
}

private val CategorySetSaver = androidx.compose.runtime.saveable.Saver<
    androidx.compose.runtime.MutableState<Set<Category>>,
    List<String>,
>(
    save = { state -> state.value.map { it.name } },
    restore = { names ->
        androidx.compose.runtime.mutableStateOf(
            names.mapNotNull { runCatching { Category.valueOf(it) }.getOrNull() }.toSet()
        )
    },
)

