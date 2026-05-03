package com.example.blackout

import android.app.Application
import com.example.blackout.engine.OcrProcessor
import com.example.blackout.engine.RedactionMode
import com.example.blackout.ui.ModelVariant
import com.example.blackout.ui.RedactionUiState
import com.example.blackout.ui.RedactionViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class RedactionViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var fakeEngine: FakeLlmEngine
    private lateinit var viewModel: RedactionViewModel
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        fakeEngine = FakeLlmEngine()
        tempDir = createTempDir()

        // Create a fake model file so the ViewModel skips ModelMissing state
        File(tempDir, ModelVariant.CPU.fileName).writeText("fake-model")

        val mockApp = mockk<Application>(relaxed = true)
        every { mockApp.getExternalFilesDir(null) } returns tempDir
        every { mockApp.cacheDir } returns tempDir
        every { mockApp.packageName } returns "com.example.blackout"
        every { mockApp.applicationInfo } returns mockk(relaxed = true)

        val mockOcr = mockk<OcrProcessor>(relaxed = true)

        viewModel = RedactionViewModel(mockApp, fakeEngine, mockOcr)
    }

    @Test
    fun `initial state transitions to Idle after init`() = runTest {
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is RedactionUiState.Idle)
    }

    @Test
    fun `redactText with valid input produces Success state`() = runTest {
        advanceUntilIdle() // let init complete
        fakeEngine.nextResult = "[NAME_1] visited [LOCATION_1]."

        viewModel.redactText("John visited the hospital.")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is RedactionUiState.Success)
        val success = state as RedactionUiState.Success
        assertEquals("[NAME_1] visited [LOCATION_1].", success.redacted)
        assertEquals("John visited the hospital.", success.original)
        assertEquals("CPU-Fake", success.backend)
    }

    @Test
    fun `redactText with blank input does nothing`() = runTest {
        advanceUntilIdle()
        viewModel.redactText("   ")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is RedactionUiState.Idle)
    }

    @Test
    fun `redactText engine failure produces Error state`() = runTest {
        advanceUntilIdle()
        fakeEngine.redactShouldThrow = true

        viewModel.redactText("Some text.")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is RedactionUiState.Error)
    }

    @Test
    fun `selectMode updates selectedMode flow`() = runTest {
        viewModel.selectMode(RedactionMode.TACTICAL)
        assertEquals(RedactionMode.TACTICAL, viewModel.selectedMode.value)
    }

    @Test
    fun `reset returns state to Idle`() = runTest {
        advanceUntilIdle()
        viewModel.redactText("text")
        advanceUntilIdle()
        viewModel.reset()
        assertTrue(viewModel.uiState.value is RedactionUiState.Idle)
    }

    @Test
    fun `non-InferenceEngine bypasses model file check and starts Idle`() = runTest {
        // The fake engine path skips the on-disk model check entirely (only the real
        // InferenceEngine triggers ModelMissing) — verify it lands in Idle without a file.
        File(tempDir, ModelVariant.CPU.fileName).delete()

        val mockApp = mockk<Application>(relaxed = true)
        every { mockApp.getExternalFilesDir(null) } returns tempDir
        every { mockApp.packageName } returns "com.example.blackout"
        every { mockApp.getSharedPreferences(any(), any()) } returns mockk(relaxed = true)

        val vm = RedactionViewModel(mockApp, fakeEngine, mockk(relaxed = true))
        assertEquals(RedactionUiState.Idle, vm.uiState.value)
    }

    @Test
    fun `redact passes selected mode to engine`() = runTest {
        advanceUntilIdle()
        viewModel.selectMode(RedactionMode.FINANCIAL)
        viewModel.redactText("Account 12345")
        advanceUntilIdle()
        assertEquals(RedactionMode.FINANCIAL, fakeEngine.lastMode)
    }
}
