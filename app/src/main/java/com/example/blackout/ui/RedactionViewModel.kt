package com.example.blackout.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.blackout.engine.InferenceEngine
import com.example.blackout.engine.InferenceMetrics
import com.example.blackout.engine.LlmEngine
import com.example.blackout.engine.pipeline.RedactionPipeline
import com.example.blackout.engine.OcrProcessor
import com.example.blackout.engine.PdfTextExtractor
import com.example.blackout.engine.PreferredBackend
import com.example.blackout.engine.RedactionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class ModelVariant(
    val label: String,
    val fileName: String,
    val description: String,
    val backend: PreferredBackend,
) {
    CPU("CPU", "gemma4.litertlm", "Gemma4 E2B · generic", PreferredBackend.CPU),
    GPU("GPU", "gemma4.litertlm", "Gemma4 E2B · generic", PreferredBackend.GPU),
    NPU("NPU (SM8750)", "gemma4_npu.litertlm", "Gemma4 E2B · SM8750 compiled", PreferredBackend.NPU),
}

class RedactionViewModel(
    application: Application,
    private val engine: LlmEngine,
    private val ocrProcessor: OcrProcessor,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<RedactionUiState>(RedactionUiState.Idle)
    val uiState: StateFlow<RedactionUiState> = _uiState.asStateFlow()

    private val _pendingBitmap = MutableStateFlow<Bitmap?>(null)
    val pendingBitmap: StateFlow<Bitmap?> = _pendingBitmap.asStateFlow()

    private val pipeline = RedactionPipeline(engine).also { p ->
        p.onProgress = { step, total, label, round ->
            _uiState.value = RedactionUiState.PipelineProgress(step, total, label, round)
        }
    }

    private val imagePipeline = com.example.blackout.engine.pipeline.ImageRedactionPipeline(engine).also { p ->
        p.onProgress = { step, total, label, round ->
            _uiState.value = RedactionUiState.PipelineProgress(step, total, label, round)
        }
    }

    val availableVariants: List<ModelVariant> = ModelVariant.entries.filter { variant ->
        val file = java.io.File(application.getExternalFilesDir(null), variant.fileName)
        file.exists() && file.length() > 0
    }

    private val _selectedMode = MutableStateFlow(RedactionMode.HIPAA)
    val selectedMode: StateFlow<RedactionMode> = _selectedMode.asStateFlow()

    // Queued task fired automatically once the engine finishes initializing.
    // Lets the user pick an image / submit text before the model is loaded
    // without showing an "Engine not ready" error.
    private sealed interface PendingTask {
        data class Text(val text: String) : PendingTask
        data class Image(val bitmap: Bitmap) : PendingTask
        data class Pdf(val uri: Uri) : PendingTask
    }
    private var pendingTask: PendingTask? = null

    private val prefs = application.getSharedPreferences("shieldtext_prefs", Context.MODE_PRIVATE)
    private val _selectedVariant = MutableStateFlow(
        prefs.getString("selected_variant", null)
            ?.let { runCatching { ModelVariant.valueOf(it) }.getOrNull() }
            ?: ModelVariant.NPU
    )
    val selectedVariant: StateFlow<ModelVariant> = _selectedVariant.asStateFlow()

    // Tracks the backend that is actually running — updated after each successful engine init.
    // Initialized to the selected variant's label so the live HUD shows the right chip
    // during model loading (before the first inference completes).
    private val _activeBackend = MutableStateFlow(_selectedVariant.value.backend.name)
    val activeBackend: StateFlow<String> = _activeBackend.asStateFlow()

    init {
        checkModelAndInitialize()
    }

    private fun checkModelAndInitialize() {
        // Non-InferenceEngine (e.g. RegexOnlyLlmEngine in debug, FakeLlmEngine in tests)
        // needs no model file — go straight to Idle.
        if (engine !is InferenceEngine) {
            _uiState.value = RedactionUiState.Idle
            return
        }
        val requested = _selectedVariant.value
        // Cascade: try the requested variant; if it's NPU and fails (the dispatch-symbol
        // mismatch on litertlm-android 0.11.0-rc1 is documented in the Qualcomm sample's
        // NPU_ISSUE_REPORT.md), fall through to GPU which uses the generic model file.
        val ladder = when (requested) {
            ModelVariant.NPU -> listOf(ModelVariant.NPU, ModelVariant.GPU, ModelVariant.CPU)
            ModelVariant.GPU -> listOf(ModelVariant.GPU, ModelVariant.CPU)
            ModelVariant.CPU -> listOf(ModelVariant.CPU)
        }
        // Pre-flight: if even the first variant's model file is missing, surface ModelMissing.
        val firstFile = modelFile(requested)
        if (!firstFile.exists() || firstFile.length() == 0L) {
            _uiState.value = RedactionUiState.ModelMissing
            return
        }
        _uiState.value = RedactionUiState.Initializing
        viewModelScope.launch {
            var lastError: Throwable? = null
            for (variant in ladder) {
                val file = modelFile(variant)
                if (!file.exists() || file.length() == 0L) continue
                val result = runCatching { engine.initialize(file.absolutePath, variant.backend) }
                if (result.isSuccess) {
                    _activeBackend.value = engine.activeBackend
                    if (engine.activeBackend != requested.backend.name) {
                        android.util.Log.w("RedactionViewModel",
                            "Requested ${requested.backend.name} but fell back to ${engine.activeBackend}")
                    }
                    _selectedVariant.value = ModelVariant.entries.first {
                        it.backend.name == engine.activeBackend
                    }
                    _uiState.value = RedactionUiState.Idle
                    drainPendingTask()
                    return@launch
                }
                lastError = result.exceptionOrNull()
            }
            _uiState.value = RedactionUiState.Error(lastError?.message ?: "Engine init failed")
        }
    }

    fun selectModelVariant(variant: ModelVariant) {
        if (variant == _selectedVariant.value) return
        _selectedVariant.value = variant
        _activeBackend.value = variant.backend.name
        prefs.edit().putString("selected_variant", variant.name).apply()
        _uiState.value = RedactionUiState.Initializing
        viewModelScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                engine.close()
                val cacheDir = getApplication<Application>().cacheDir
                cacheDir.listFiles()?.forEach { it.delete() }
                android.util.Log.i("RedactionViewModel", "Cleared cache before backend switch")
                Thread.sleep(3000)
            }
            checkModelAndInitialize()
        }
    }

    fun selectMode(mode: RedactionMode) {
        _selectedMode.value = mode
    }

    fun reportCaptureError(message: String) {
        _uiState.value = RedactionUiState.Error(message)
    }

    fun redactText(text: String) {
        if (text.isBlank()) return
        _uiState.value = RedactionUiState.Idle
        if (!engine.isReady) {
            pendingTask = PendingTask.Text(text)
            _uiState.value = RedactionUiState.Loading
            return
        }
        _uiState.value = RedactionUiState.Loading
        viewModelScope.launch {
            runCatching { pipeline.process(text.trim()) }
                .onSuccess { result ->
                    val m = engine.lastMetrics
                    _uiState.value = buildSuccess(
                        text.trim(), result.redactedText, m?.copy(
                            latencyMs = result.totalLatencyMs,
                        )
                    )
                }
                .onFailure { _uiState.value = RedactionUiState.Error(it.message ?: "Redaction failed") }
        }
    }

    fun redactImage(bitmap: Bitmap) {
        _pendingBitmap.value = bitmap
        if (!engine.isReady) {
            pendingTask = PendingTask.Image(bitmap)
            _uiState.value = RedactionUiState.Loading
            return
        }
        _uiState.value = RedactionUiState.Loading
        viewModelScope.launch {
            runCatching {
                val ocrResult = ocrProcessor.processWithBounds(bitmap)
                if (ocrResult.fullText.isBlank()) {
                    throw IllegalStateException("No text detected in image — try a clearer photo.")
                }
                imagePipeline.process(bitmap, ocrResult)
            }
                .onSuccess { result ->
                    _uiState.value = buildSuccess(
                        result.redactedText, result.redactedText, engine.lastMetrics,
                        bitmap, result.redactedBitmap,
                        fieldsOverride = result.redactedElements,
                    )
                }
                .onFailure { _uiState.value = RedactionUiState.Error(it.message ?: "OCR or redaction failed") }
        }
    }

    fun redactPdf(uri: Uri) {
        if (!engine.isReady) {
            pendingTask = PendingTask.Pdf(uri)
            _uiState.value = RedactionUiState.Loading
            return
        }
        _uiState.value = RedactionUiState.Loading
        viewModelScope.launch {
            runCatching {
                val extractedText = PdfTextExtractor.extract(
                    getApplication(), uri, ocrProcessor
                )
                if (extractedText.isBlank()) {
                    throw IllegalStateException("No text detected in PDF — try a different file.")
                }
                val pipelineResult = pipeline.process(extractedText)
                Pair(extractedText, pipelineResult.redactedText)
            }
                .onSuccess { (original, redacted) ->
                    _uiState.value = buildSuccess(original, redacted, engine.lastMetrics)
                }
                .onFailure { _uiState.value = RedactionUiState.Error(it.message ?: "PDF extraction failed") }
        }
    }

    fun reset() {
        _uiState.value = RedactionUiState.Idle
        _pendingBitmap.value = null
    }

    fun retryInit() {
        checkModelAndInitialize()
    }

    fun runBenchmark(category: String, count: Int, backend: String? = null, type: String = "image", difficulty: String? = null) {
        viewModelScope.launch(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.IO) {
            if (backend != null) {
                val variant = ModelVariant.entries.firstOrNull { it.name.equals(backend, ignoreCase = true) }
                if (variant != null && variant != _selectedVariant.value) {
                    android.util.Log.i("RedactoBenchmark", "Switching backend to ${variant.name}...")
                    selectModelVariant(variant)
                    var waited = 0
                    while (!engine.isReady && waited < 30000) {
                        kotlinx.coroutines.delay(500)
                        waited += 500
                    }
                    android.util.Log.i("RedactoBenchmark", "Backend switch complete (${waited}ms), active=${engine.activeBackend}")
                }
            }
            if (type == "text") {
                val runner = com.example.blackout.benchmark.TextBenchmarkRunner(engine, getApplication())
                runner.run(count, difficulty)
            } else {
                val runner = com.example.blackout.benchmark.BenchmarkRunner(engine)
                runner.run(category, count)
            }
        }
    }

    fun modelAdbCommand(): String {
        val pkg = getApplication<Application>().packageName
        val variant = _selectedVariant.value
        return "adb push ${variant.fileName} /sdcard/Android/data/$pkg/files/${variant.fileName}"
    }

    private fun drainPendingTask() {
        val task = pendingTask ?: return
        pendingTask = null
        when (task) {
            is PendingTask.Text -> redactText(task.text)
            is PendingTask.Image -> redactImage(task.bitmap)
            is PendingTask.Pdf -> redactPdf(task.uri)
        }
    }

    private fun modelFile(variant: ModelVariant = _selectedVariant.value): File =
        File(getApplication<Application>().getExternalFilesDir(null), variant.fileName)

    private fun countRedactedFields(redacted: String): Int =
        Regex("""\[[A-Z_]+_\d+\]""").findAll(redacted).count()

    private fun buildSuccess(
        original: String,
        redacted: String,
        m: InferenceMetrics?,
        sourceBitmap: Bitmap? = null,
        redactedBitmap: Bitmap? = null,
        fieldsOverride: Int = -1,
    ) = RedactionUiState.Success(
        original = original,
        redacted = redacted,
        backend = engine.activeBackend,
        latencyMs = m?.latencyMs ?: 0L,
        tokenCount = m?.tokenCount ?: 0,
        tokensPerSecond = m?.decodeTokensPerSec ?: 0f,
        fieldsRedacted = if (fieldsOverride >= 0) fieldsOverride else countRedactedFields(redacted),
        timeToFirstTokenMs = m?.timeToFirstTokenMs ?: 0L,
        decodeTokensPerSec = m?.decodeTokensPerSec ?: 0f,
        peakMemoryMb = m?.peakRssMb ?: 0f,
        sourceBitmap = sourceBitmap,
        redactedBitmap = redactedBitmap,
        metrics = m,
    )

    override fun onCleared() {
        super.onCleared()
        engine.close()
        ocrProcessor.close()
    }

    companion object {
        fun factory(engine: LlmEngine? = null): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T {
                    val application =
                        extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                    val resolvedEngine = engine ?: InferenceEngine(application)
                    return RedactionViewModel(
                        application,
                        resolvedEngine,
                        OcrProcessor(),
                    ) as T
                }
            }
    }
}
