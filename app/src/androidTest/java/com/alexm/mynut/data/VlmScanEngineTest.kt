package com.alexm.mynut.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alexm.mynut.data.llm.LlamaVisionEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class VlmScanEngineTest {

    @Test
    fun scanLabelReturnsAtLeastOneNonNullField() = runTest(timeout = 120.minutes) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val imageBytes = testContext.assets.open("etiket2.jpg").readBytes()

        val modelsDir = File(context.filesDir, "test-models-vlm-scan")
        val mainModelManager = LocalModelManager(
            modelsDir = modelsDir,
            downloader = HttpModelDownloader(),
            modelFilename = VlmModelManager.MAIN_MODEL_FILENAME,
            modelUrl = VlmModelManager.MAIN_MODEL_URL,
            expectedSha256 = VlmModelManager.MAIN_MODEL_SHA256
        )
        val mmprojModelManager = LocalModelManager(
            modelsDir = modelsDir,
            downloader = HttpModelDownloader(),
            modelFilename = VlmModelManager.MMPROJ_FILENAME,
            modelUrl = VlmModelManager.MMPROJ_URL,
            expectedSha256 = VlmModelManager.MMPROJ_SHA256
        )
        val modelManager = VlmModelManager(mainModelManager, mmprojModelManager)

        val visionEngine = LlamaVisionEngine(modelManager)
        try {
            val engine = VlmScanEngine(visionEngine)
            val result = engine.scanLabel(imageBytes)

            assertTrue("le scan a échoué : ${result.exceptionOrNull()}", result.isSuccess)
            val values = result.getOrThrow()
            val nonNullFields = listOfNotNull(
                values.calories, values.fats, values.saturatedFats, values.carbs,
                values.sugars, values.fiber, values.proteins, values.sodium
            )
            assertTrue("aucun champ non-null retourné", nonNullFields.isNotEmpty())
        } finally {
            // AndroidJUnitRunner runs every test method in this class in the same process.
            // Without freeing the ~2.95GB native model here, the next test's independent
            // engine instance would try to load a second full copy alongside this one and
            // exhaust the AVD's RAM (this is exactly what happened before this fix was added).
            visionEngine.unload()
        }
    }

    /**
     * Regression test for two bugs found in final review: (1) the native context/sampler
     * were cached and reused across calls without clearing the KV cache, so any scan after
     * the first failed outright; (2) nothing downscaled the decoded bitmap, so a real
     * (multi-megapixel) camera photo would overflow Qwen3-VL's image-token budget within
     * the 4096-token context. This runs two scans on the SAME engine instance (so the
     * native handle/model is reused, exactly like real app usage) -- first a normal-sized
     * label photo, then a synthetic ~14.6MP one (etiket2.jpg upscaled to 4000x3645, the
     * order of magnitude of an actual phone camera photo) -- and requires both to succeed.
     */
    @Test
    fun secondScanOnSameEngineWithLargeImageStillSucceeds() = runTest(timeout = 240.minutes) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val smallImageBytes = testContext.assets.open("etiket2.jpg").readBytes()
        val largeImageBytes = testContext.assets.open("etiket2_large.jpg").readBytes()

        val modelsDir = File(context.filesDir, "test-models-vlm-scan")
        val mainModelManager = LocalModelManager(
            modelsDir = modelsDir,
            downloader = HttpModelDownloader(),
            modelFilename = VlmModelManager.MAIN_MODEL_FILENAME,
            modelUrl = VlmModelManager.MAIN_MODEL_URL,
            expectedSha256 = VlmModelManager.MAIN_MODEL_SHA256
        )
        val mmprojModelManager = LocalModelManager(
            modelsDir = modelsDir,
            downloader = HttpModelDownloader(),
            modelFilename = VlmModelManager.MMPROJ_FILENAME,
            modelUrl = VlmModelManager.MMPROJ_URL,
            expectedSha256 = VlmModelManager.MMPROJ_SHA256
        )
        val modelManager = VlmModelManager(mainModelManager, mmprojModelManager)
        val visionEngine = LlamaVisionEngine(modelManager)
        try {
            val engine = VlmScanEngine(visionEngine)

            val firstResult = engine.scanLabel(smallImageBytes)
            assertTrue("le 1er scan a échoué : ${firstResult.exceptionOrNull()}", firstResult.isSuccess)

            val secondResult = engine.scanLabel(largeImageBytes)
            assertTrue(
                "le 2e scan (image large, même moteur) a échoué : ${secondResult.exceptionOrNull()}",
                secondResult.isSuccess
            )
            val secondValues = secondResult.getOrThrow()
            val nonNullFields = listOfNotNull(
                secondValues.calories, secondValues.fats, secondValues.saturatedFats, secondValues.carbs,
                secondValues.sugars, secondValues.fiber, secondValues.proteins, secondValues.sodium
            )
            assertTrue("aucun champ non-null retourné sur le 2e scan", nonNullFields.isNotEmpty())
        } finally {
            // Same reasoning as the other test: free the native model before the next
            // test method in this process tries to load its own independent copy.
            visionEngine.unload()
        }
    }
}
