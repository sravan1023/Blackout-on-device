package com.example.blackout

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.blackout.benchmark.BenchmarkReceiver
import com.example.blackout.navigation.BlackoutNavGraph
import com.example.blackout.ui.RedactionViewModel
import com.example.blackout.ui.theme.BlackoutTheme
import com.example.blackout.ui.viewmodel.DocumentViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlackoutTheme {
                val vm: RedactionViewModel = viewModel(
                    factory = RedactionViewModel.factory()
                )
                val docViewModel: DocumentViewModel = viewModel(
                    factory = DocumentViewModel.Factory
                )

                BenchmarkReceiver.onBenchmarkRequested = { category, count, backend, type, difficulty ->
                    Log.i("BlackoutBenchmark", "Callback: type=$type, count=$count, backend=$backend, difficulty=$difficulty")
                    vm.runBenchmark(category, count, backend, type, difficulty)
                }

                BlackoutNavGraph(
                    redactionViewModel = vm,
                    docViewModel = docViewModel,
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        BenchmarkReceiver.onBenchmarkRequested = null
    }
}
