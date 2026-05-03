package com.example.blackout

import android.app.Application
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class BlackoutApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val nativeLibDir = applicationInfo.nativeLibraryDir
        val paths = listOf(
            nativeLibDir,
            "/vendor/lib64/rfs/dsp/snap",
            "/vendor/lib64/hw/audio",
            "/vendor/dsp/cdsp",
            "/vendor/lib64",
            "/vendor/lib64/snap",
            "/system/lib64",
        ).joinToString(":")
        runCatching {
            android.system.Os.setenv("ADSP_LIBRARY_PATH", paths, true)
            android.system.Os.setenv("LD_LIBRARY_PATH", paths, true)
            Log.i("BlackoutApp", "Pre-init ADSP_LIBRARY_PATH=$paths")
        }.onFailure { Log.w("BlackoutApp", "Failed to seed DSP library paths: ${it.message}") }

        extractBundledModels()
    }

    private fun extractBundledModels() {
        val modelFiles = listOf("gemma4_npu.litertlm")
        val destDir = getExternalFilesDir(null) ?: return

        for (name in modelFiles) {
            val destFile = File(destDir, name)
            if (destFile.exists() && destFile.length() > 0) {
                Log.i("BlackoutApp", "Model already extracted: $name (${destFile.length()} bytes)")
                continue
            }
            Log.i("BlackoutApp", "Extracting bundled model: $name ...")
            try {
                assets.open(name).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output, bufferSize = 8 * 1024 * 1024)
                    }
                }
                Log.i("BlackoutApp", "Extracted $name: ${destFile.length()} bytes")
            } catch (e: Throwable) {
                Log.w("BlackoutApp", "Failed to extract $name: ${e.message}")
            }
        }
    }
}
