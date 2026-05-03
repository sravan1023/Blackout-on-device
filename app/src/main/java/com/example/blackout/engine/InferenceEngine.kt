package com.example.blackout.engine

import android.content.Context
import android.os.Build
import android.os.Debug
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class InferenceEngine(private val context: Context) : LlmEngine {

    private var engine: Engine? = null
    private var isNpu = false
    private var nativeLoaded = false

    override var isReady: Boolean = false
        private set

    override var activeBackend: String = "CPU"
        private set

    override var lastMetrics: InferenceMetrics? = null
        private set

    override suspend fun initialize(modelPath: String, preferredBackend: PreferredBackend): Unit = withContext(Dispatchers.IO) {
        // ORDER MATTERS: env vars (ADSP_LIBRARY_PATH especially) must be set before any
        // native lib loads, since QnnHtp captures the path once at dlopen time. Application
        // onCreate already seeds it; this is a re-application in case the value was lost.
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        configureNativeRuntime(nativeLibDir)
        ensureNativeLoaded()
        // The viewmodel handles cross-variant fallback (NPU → GPU → CPU) because each
        // backend pairs with a different model file. The engine itself initializes only
        // the requested backend with the supplied model; if it can't, it throws.
        engine?.runCatching { close() }
        engine = null
        isReady = false
        tryInitBackend(modelPath, preferredBackend, nativeLibDir)
        isReady = true
    }

    private fun tryInitBackend(modelPath: String, target: PreferredBackend, nativeLibDir: String) {
        val config = when (target) {
            PreferredBackend.NPU -> {
                Log.i("InferenceEngine", "Attempting NPU backend (Android ${Build.VERSION.SDK_INT})")
                // Vision sub-backend: CPU matches the model file's section_backend_constraint=cpu
                // on the NPU-compiled model. The OpenGL fallback path (gpu_backend_opengl.cc:169)
                // is no longer a concern since libvndksupport.so is now declared in the manifest,
                // allowing LiteRT to take the OpenCL path for GPU/vision work.
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.NPU(nativeLibraryDir = nativeLibDir),
                    visionBackend = Backend.CPU(),
                    audioBackend = Backend.CPU(),
                    maxNumTokens = 4000,
                    maxNumImages = 1,
                    cacheDir = context.cacheDir.absolutePath,
                )
            }
            PreferredBackend.GPU -> {
                Log.i("InferenceEngine", "Attempting GPU backend (Android ${Build.VERSION.SDK_INT})")
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.GPU(),
                    visionBackend = Backend.CPU(),
                    audioBackend = Backend.CPU(),
                    maxNumTokens = 4000,
                    maxNumImages = 1,
                    cacheDir = context.cacheDir.absolutePath,
                )
            }
            PreferredBackend.CPU -> {
                Log.i("InferenceEngine", "Using CPU backend")
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    visionBackend = Backend.CPU(),
                    audioBackend = Backend.CPU(),
                    maxNumTokens = 4000,
                    maxNumImages = 1,
                    cacheDir = context.cacheDir.absolutePath,
                )
            }
        }

        // Construct the Engine then initialize. If initialize() throws, close the Engine
        // object to avoid a native resource leak (the Engine allocates JNI/native state
        // in its constructor before initialize() is called).
        val candidate = Engine(config)
        try {
            candidate.initialize()
        } catch (e: Throwable) {
            candidate.close()
            throw e
        }
        engine = candidate
        activeBackend = when (target) {
            PreferredBackend.NPU -> { isNpu = true; "NPU" }
            PreferredBackend.GPU -> { isNpu = false; "GPU" }
            PreferredBackend.CPU -> { isNpu = false; "CPU" }
        }
        Log.i("InferenceEngine", "$activeBackend init succeeded")
    }

    @OptIn(ExperimentalApi::class)
    override suspend fun redact(text: String, mode: RedactionMode): String =
        withContext(Dispatchers.IO) {
            val eng = requireNotNull(engine) { "Engine not initialized — call initialize() first" }
            ExperimentalFlags.enableConversationConstrainedDecoding = false
            val conversation = eng.createConversation(
                ConversationConfig(
                    samplerConfig = if (isNpu) null else SamplerConfig(
                        topK = 64,
                        topP = 0.95,
                        temperature = 1.0,
                    )
                )
            )
            val heapBeforeMb = Debug.getNativeHeapAllocatedSize() / (1024f * 1024f)
            val rssBeforeMb = readRssMb()
            val startTime = System.currentTimeMillis()
            var tokenCount = 0
            var firstTokenTimeMs = 0L
            var lastTokenTimeMs = 0L

            try {
                coroutineScope {
                    val llmDeferred = async {
                        val contents = Contents.of(listOf(Content.Text(SystemPrompts.build(mode, text))))
                        val sb = StringBuilder()
                        kotlinx.coroutines.withTimeout(60_000L) {
                            suspendCancellableCoroutine<Unit> { cont ->
                                conversation.sendMessageAsync(
                                    contents,
                                    object : MessageCallback {
                                        override fun onMessage(message: Message) {
                                            val now = System.currentTimeMillis()
                                            if (tokenCount == 0) firstTokenTimeMs = now
                                            lastTokenTimeMs = now
                                            sb.append(message.toString())
                                            tokenCount++
                                        }
                                        override fun onDone() { cont.resume(Unit) }
                                        override fun onError(throwable: Throwable) { cont.resumeWithException(throwable) }
                                    },
                                    emptyMap(),
                                )
                                cont.invokeOnCancellation { runCatching { conversation.cancelProcess() } }
                            }
                        }
                        sb.toString().trim()
                    }
                    val llmResult = llmDeferred.await()

                    val endTime = System.currentTimeMillis()
                    val latencyMs = endTime - startTime
                    val ttftMs = if (firstTokenTimeMs > 0) firstTokenTimeMs - startTime else latencyMs
                    val decodeSpanMs = if (tokenCount > 1) lastTokenTimeMs - firstTokenTimeMs else 0L
                    val decodeTokensPerSec = if (decodeSpanMs > 0) (tokenCount - 1) * 1000f / decodeSpanMs else 0f
                    val heapAfterMb = Debug.getNativeHeapAllocatedSize() / (1024f * 1024f)
                    val rssAfterMb = readRssMb()

                    lastMetrics = InferenceMetrics(
                        backend = activeBackend,
                        latencyMs = latencyMs,
                        timeToFirstTokenMs = ttftMs,
                        tokenCount = tokenCount,
                        tokensPerSec = if (latencyMs > 0) tokenCount * 1000f / latencyMs else 0f,
                        decodeTokensPerSec = decodeTokensPerSec,
                        peakNativeHeapMb = maxOf(heapBeforeMb, heapAfterMb),
                        peakRssMb = maxOf(rssBeforeMb, rssAfterMb),
                    )
                    Log.i("InferenceEngine", "Metrics: latency=${latencyMs}ms, TTFT=${ttftMs}ms, " +
                        "tokens=$tokenCount, avg=${lastMetrics!!.tokensPerSec} tok/s, " +
                        "decode=${decodeTokensPerSec} tok/s, " +
                        "heap=${heapAfterMb}MB, rss=${rssAfterMb}MB")

                    llmResult
                }
            } finally {
                conversation.close()
            }
        }

    @OptIn(ExperimentalApi::class)
    override suspend fun infer(prompt: String): String =
        withContext(Dispatchers.IO) {
            val eng = requireNotNull(engine) { "Engine not initialized" }
            ExperimentalFlags.enableConversationConstrainedDecoding = false
            val conversation = eng.createConversation(
                ConversationConfig(
                    samplerConfig = if (isNpu) null else SamplerConfig(
                        topK = 64,
                        topP = 0.95,
                        temperature = 1.0,
                    )
                )
            )
            val heapBeforeMb = Debug.getNativeHeapAllocatedSize() / (1024f * 1024f)
            val rssBeforeMb = readRssMb()
            val startTime = System.currentTimeMillis()
            var tokenCount = 0
            var firstTokenTimeMs = 0L
            var lastTokenTimeMs = 0L

            try {
                val contents = Contents.of(listOf(Content.Text(prompt)))
                val sb = StringBuilder()
                kotlinx.coroutines.withTimeout(60_000L) {
                    suspendCancellableCoroutine<Unit> { cont ->
                        conversation.sendMessageAsync(
                            contents,
                            object : MessageCallback {
                                override fun onMessage(message: Message) {
                                    val now = System.currentTimeMillis()
                                    if (tokenCount == 0) firstTokenTimeMs = now
                                    lastTokenTimeMs = now
                                    sb.append(message.toString())
                                    tokenCount++
                                }
                                override fun onDone() { cont.resume(Unit) }
                                override fun onError(throwable: Throwable) { cont.resumeWithException(throwable) }
                            },
                            emptyMap(),
                        )
                        cont.invokeOnCancellation { runCatching { conversation.cancelProcess() } }
                    }
                }

                val endTime = System.currentTimeMillis()
                val latencyMs = endTime - startTime
                val ttftMs = if (firstTokenTimeMs > 0) firstTokenTimeMs - startTime else latencyMs
                val decodeSpanMs = if (tokenCount > 1) lastTokenTimeMs - firstTokenTimeMs else 0L
                val decodeTokensPerSec = if (decodeSpanMs > 0) (tokenCount - 1) * 1000f / decodeSpanMs else 0f
                val heapAfterMb = Debug.getNativeHeapAllocatedSize() / (1024f * 1024f)
                val rssAfterMb = readRssMb()

                lastMetrics = InferenceMetrics(
                    backend = activeBackend,
                    latencyMs = latencyMs,
                    timeToFirstTokenMs = ttftMs,
                    tokenCount = tokenCount,
                    tokensPerSec = if (latencyMs > 0) tokenCount * 1000f / latencyMs else 0f,
                    decodeTokensPerSec = decodeTokensPerSec,
                    peakNativeHeapMb = maxOf(heapBeforeMb, heapAfterMb),
                    peakRssMb = maxOf(rssBeforeMb, rssAfterMb),
                )
                Log.i("InferenceEngine", "infer(): latency=${latencyMs}ms, TTFT=${ttftMs}ms, " +
                    "tokens=$tokenCount, decode=${decodeTokensPerSec} tok/s")

                sb.toString().trim()
            } finally {
                conversation.close()
            }
        }

    private fun configureNativeRuntime(nativeLibraryDir: String) {
        // ADSP_LIBRARY_PATH tells the Hexagon DSP where to find libQnnHtpV79Skel.so.
        // LD_LIBRARY_PATH lets dlopen resolve QNN stubs bundled in jniLibs as well as
        // the device's own /vendor copies. nativeLibraryDir comes first so our bundled
        // QAIRT 2.42 versions take precedence over any device QNN runtime.
        val vendorDirs = listOf(
            nativeLibraryDir,
            "/vendor/lib64",
            "/vendor/lib64/snap",
            "/vendor/lib64/rfs/dsp/snap",
            "/vendor/lib64/hw/audio",
            "/vendor/dsp/cdsp",
            "/system/lib64",
        )
        val mergedPath = vendorDirs.joinToString(":")
        runCatching {
            android.system.Os.setenv("LD_LIBRARY_PATH", mergedPath, true)
            android.system.Os.setenv("ADSP_LIBRARY_PATH", mergedPath, true)
            Log.i("InferenceEngine", "Set native library paths: $mergedPath")
        }.onFailure { Log.w("InferenceEngine", "Failed to set native lib paths: ${it.message}") }
    }

    private fun ensureNativeLoaded() {
        if (nativeLoaded) return
        // Pre-load in dependency order so the first JNI call doesn't abort in nativeCheckLoaded().
        runCatching { System.loadLibrary("LiteRt") }
            .onFailure { Log.w("InferenceEngine", "Unable to pre-load LiteRt: ${it.message}") }
        runCatching { System.loadLibrary("litertlm_jni") }
            .onFailure { throw IllegalStateException("Failed to load LiteRT-LM native library", it) }
        // Force NPU dispatch + constraint provider dlopen up front so any symbol mismatch
        // throws an UnsatisfiedLinkError we can surface to UI instead of leaving DISPATCH_OP
        // silently unregistered with the tflite op resolver.
        runCatching { System.loadLibrary("LiteRtDispatch_Qualcomm") }
            .onFailure { Log.w("InferenceEngine", "Dispatch lib preload failed: ${it.message}") }
        runCatching { System.loadLibrary("GemmaModelConstraintProvider") }
            .onFailure { Log.w("InferenceEngine", "Constraint provider preload failed: ${it.message}") }
        nativeLoaded = true
    }

    private fun readRssMb(): Float = runCatching {
        val status = File("/proc/self/status").readText()
        val vmRssLine = status.lineSequence().firstOrNull { it.startsWith("VmRSS:") }
        val kb = vmRssLine?.trim()?.split("\\s+".toRegex())?.getOrNull(1)?.toLongOrNull() ?: 0L
        kb / 1024f
    }.getOrElse { 0f }

    override fun close() {
        val e = engine
        engine = null
        isReady = false
        isNpu = false
        lastMetrics = null
        if (e != null) {
            Log.i("InferenceEngine", "Closing engine, releasing native resources...")
            e.close()
            System.gc()
            Runtime.getRuntime().gc()
            Log.i("InferenceEngine", "Engine closed, GC requested")
        }
    }
}
