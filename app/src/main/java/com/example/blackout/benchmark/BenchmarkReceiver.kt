package com.example.blackout.benchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BenchmarkReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BlackoutBenchmark"
        const val ACTION = "com.example.blackout.BENCHMARK"

        var onBenchmarkRequested: ((String, Int, String?, String, String?) -> Unit)? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        val category = intent.getStringExtra("category") ?: "medical/patient-records"
        val count = intent.getIntExtra("count", 85)
        val backend = intent.getStringExtra("backend")
        val type = intent.getStringExtra("type") ?: "image"
        val difficulty = intent.getStringExtra("difficulty")

        Log.i(TAG, "Benchmark request: type=$type, count=$count, backend=$backend, difficulty=$difficulty")

        val callback = onBenchmarkRequested
        if (callback != null) {
            callback(category, count, backend, type, difficulty)
        } else {
            Log.w(TAG, "No callback registered — launch app first.")
        }
    }
}
