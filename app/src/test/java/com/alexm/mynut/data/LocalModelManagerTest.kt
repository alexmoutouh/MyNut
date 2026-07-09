package com.alexm.mynut.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

private const val KNOWN_CONTENT = "contenu de test connu"
private val KNOWN_SHA256 = MessageDigest.getInstance("SHA-256")
    .digest(KNOWN_CONTENT.toByteArray())
    .joinToString("") { "%02x".format(it) }

private class FakeDownloader(
    private val contentToWrite: String = KNOWN_CONTENT,
    private val shouldFail: Exception? = null
) : ModelDownloader {
    override suspend fun download(url: String, destination: File, onProgress: (Float) -> Unit) {
        if (shouldFail != null) throw shouldFail
        onProgress(0.5f)
        destination.writeText(contentToWrite)
        onProgress(1f)
    }
}

class LocalModelManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `model already present and valid is reported ready without downloading`() = runTest {
        val modelsDir = tempFolder.newFolder("models")
        val manager = LocalModelManager(
            modelsDir = modelsDir,
            downloader = FakeDownloader(),
            modelUrl = "https://example.test/model.gguf",
            expectedSha256 = KNOWN_SHA256
        )
        File(modelsDir, LocalModelManager.MODEL_FILENAME).writeText(KNOWN_CONTENT)

        assertTrue(manager.isModelReady())
        val result = manager.ensureModelReady()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `missing model is downloaded and validated`() = runTest {
        val modelsDir = tempFolder.newFolder("models")
        val manager = LocalModelManager(
            modelsDir = modelsDir,
            downloader = FakeDownloader(),
            modelUrl = "https://example.test/model.gguf",
            expectedSha256 = KNOWN_SHA256
        )

        assertTrue(!manager.isModelReady())
        val result = manager.ensureModelReady()
        assertTrue(result.isSuccess)
        assertTrue(manager.isModelReady())
    }

    @Test
    fun `checksum mismatch fails and removes temp file`() = runTest {
        val modelsDir = tempFolder.newFolder("models")
        val manager = LocalModelManager(
            modelsDir = modelsDir,
            downloader = FakeDownloader(contentToWrite = "contenu different"),
            modelUrl = "https://example.test/model.gguf",
            expectedSha256 = KNOWN_SHA256
        )

        val result = manager.ensureModelReady()
        assertTrue(result.isFailure)
        assertTrue(!manager.isModelReady())
        assertTrue(modelsDir.listFiles()?.none { it.name.endsWith(".tmp") } ?: true)
    }

    @Test
    fun `download failure surfaces as Failed state`() = runTest {
        val modelsDir = tempFolder.newFolder("models")
        val manager = LocalModelManager(
            modelsDir = modelsDir,
            downloader = FakeDownloader(shouldFail = java.io.IOException("pas de réseau")),
            modelUrl = "https://example.test/model.gguf",
            expectedSha256 = KNOWN_SHA256
        )

        val result = manager.ensureModelReady()
        assertTrue(result.isFailure)
        assertEquals(ModelDownloadState.Failed::class, manager.state.value::class)
    }
}
