package com.alexm.mynut.data.llm

import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alexm.mynut.data.HttpModelDownloader
import com.alexm.mynut.data.LocalModelManager
import com.alexm.mynut.data.VlmModelManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class LlamaVisionEngineTest {

    @Test
    fun completesAPromptWithImageWithoutCrashing() = runTest(timeout = 90.minutes) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val imageBytes = testContext.assets.open("test-1.jpeg").readBytes()
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        val modelsDir = File(context.filesDir, "test-models-vision")
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

        val engine = LlamaVisionEngine(modelManager)
        val result = engine.complete(bitmap, "Describe this image in one sentence.", maxTokens = 16)

        assertTrue("complete() a échoué: ${result.exceptionOrNull()}", result.isSuccess)
        assertTrue(result.getOrThrow().isNotEmpty())

        engine.unload()
    }
}
