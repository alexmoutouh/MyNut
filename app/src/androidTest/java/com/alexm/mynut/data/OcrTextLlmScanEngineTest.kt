package com.alexm.mynut.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alexm.mynut.data.llm.LlamaTextEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class OcrTextLlmScanEngineTest {

    // runTest's default dispatch timeout is 60s, meant for virtual-time coroutine tests.
    // This test does real, non-virtual I/O: a ~1GB network download on first run, then real
    // llama.cpp inference on the emulator's CPU. Measured on-device throughput for the real
    // qwen2.5-1.5b-instruct-q4_k_m model is ~860ms/token with no GPU offload, and the model
    // does not always stop at EOS early — so a full maxTokens=512 completion can take up to
    // ~7-8 minutes of generation alone, on top of ~8-10 minutes for the download. 35 minutes
    // gives a safe margin over that measured worst case.
    @Test
    fun scanLabelReturnsAtLeastOneNonNullField() = runTest(timeout = 35.minutes) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // androidTest/assets are bundled into the test APK, not the app-under-test APK —
        // use the instrumentation context to read them (see LlamaTextEngineTest / TextRecognitionSmokeTest).
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val imageBytes = testContext.assets.open("label_sample.jpg").readBytes()

        val modelManager = LocalModelManager(
            modelsDir = File(context.filesDir, "test-models-ocr"),
            downloader = HttpModelDownloader()
        )

        val engine = OcrTextLlmScanEngine(LlamaTextEngine(modelManager))
        val result = engine.scanLabel(imageBytes)

        assertTrue("le scan a échoué : ${result.exceptionOrNull()}", result.isSuccess)
        val values = result.getOrThrow()
        val nonNullFields = listOfNotNull(
            values.calories, values.fats, values.saturatedFats, values.carbs,
            values.sugars, values.fiber, values.proteins, values.sodium
        )
        assertTrue("aucun champ non-null retourné", nonNullFields.isNotEmpty())
    }
}
