package com.alexm.mynut.data.llm

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alexm.mynut.data.LocalModelManager
import com.alexm.mynut.data.ModelDownloader
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class LlamaTextEngineTest {

    private class AssetCopyDownloader(private val context: android.content.Context) : ModelDownloader {
        override suspend fun download(url: String, destination: File, onProgress: (Float) -> Unit) {
            context.assets.open("stories260K.gguf").use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            onProgress(1f)
        }
    }

    @Test
    fun completesAPromptWithoutCrashing() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // androidTest/assets are bundled into the test APK (package com.alexm.mynut.test),
        // not the app-under-test APK returned by ApplicationProvider — use the instrumentation
        // context to read them.
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val modelsDir = File(context.filesDir, "test-models")

        val bytes = testContext.assets.open("stories260K.gguf").readBytes()
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        val modelManager = LocalModelManager(
            modelsDir = modelsDir,
            downloader = AssetCopyDownloader(testContext),
            modelUrl = "asset://stories260K.gguf",
            expectedSha256 = sha256
        )

        val engine = LlamaTextEngine(modelManager)
        val result = engine.complete("Hello", maxTokens = 16)

        assertTrue("complete() a échoué: ${result.exceptionOrNull()}", result.isSuccess)
        assertTrue(result.getOrThrow().isNotEmpty())

        engine.unload()
    }
}
