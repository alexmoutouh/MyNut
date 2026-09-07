package com.alexm.mynut.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

private fun sha256Of(content: String): String =
    MessageDigest.getInstance("SHA-256").digest(content.toByteArray()).joinToString("") { "%02x".format(it) }

private class FakeDownloaderVlm(
    private val contentToWrite: String,
    private val shouldFail: Exception? = null
) : ModelDownloader {
    override suspend fun download(url: String, destination: File, onProgress: (Float) -> Unit) {
        if (shouldFail != null) throw shouldFail
        onProgress(0.5f)
        destination.writeText(contentToWrite)
        onProgress(1f)
    }
}

class VlmModelManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `ready only when both underlying files are ready`() = runTest {
        val modelsDir = tempFolder.newFolder("models")
        val mainContent = "main model content"
        val mmprojContent = "mmproj content"

        val mainManager = LocalModelManager(
            modelsDir = modelsDir, downloader = FakeDownloaderVlm(mainContent),
            modelFilename = "main.gguf", modelUrl = "https://example.test/main.gguf",
            expectedSha256 = sha256Of(mainContent)
        )
        val mmprojManager = LocalModelManager(
            modelsDir = modelsDir, downloader = FakeDownloaderVlm(mmprojContent),
            modelFilename = "mmproj.gguf", modelUrl = "https://example.test/mmproj.gguf",
            expectedSha256 = sha256Of(mmprojContent)
        )
        val manager = VlmModelManager(mainManager, mmprojManager)

        assertTrue(!manager.isModelReady())
        File(modelsDir, "main.gguf").writeText(mainContent)
        assertTrue(!manager.isModelReady())
        File(modelsDir, "mmproj.gguf").writeText(mmprojContent)
        assertTrue(manager.isModelReady())
    }

    @Test
    fun `ensureModelReady downloads both files`() = runTest {
        val modelsDir = tempFolder.newFolder("models")
        val mainContent = "main model content"
        val mmprojContent = "mmproj content"

        val mainManager = LocalModelManager(
            modelsDir = modelsDir, downloader = FakeDownloaderVlm(mainContent),
            modelFilename = "main.gguf", modelUrl = "https://example.test/main.gguf",
            expectedSha256 = sha256Of(mainContent)
        )
        val mmprojManager = LocalModelManager(
            modelsDir = modelsDir, downloader = FakeDownloaderVlm(mmprojContent),
            modelFilename = "mmproj.gguf", modelUrl = "https://example.test/mmproj.gguf",
            expectedSha256 = sha256Of(mmprojContent)
        )
        val manager = VlmModelManager(mainManager, mmprojManager)

        val result = manager.ensureModelReady()

        assertTrue(result.isSuccess)
        assertTrue(manager.isModelReady())
        val (mainFile, mmprojFile) = result.getOrThrow()
        assertTrue(mainFile.name == "main.gguf")
        assertTrue(mmprojFile.name == "mmproj.gguf")
    }

    @Test
    fun `failure on main model fails the whole operation without touching mmproj`() = runTest {
        val modelsDir = tempFolder.newFolder("models")
        val mmprojContent = "mmproj content"

        val mainManager = LocalModelManager(
            modelsDir = modelsDir,
            downloader = FakeDownloaderVlm("x", shouldFail = java.io.IOException("pas de réseau")),
            modelFilename = "main.gguf", modelUrl = "https://example.test/main.gguf",
            expectedSha256 = sha256Of("whatever")
        )
        val mmprojManager = LocalModelManager(
            modelsDir = modelsDir, downloader = FakeDownloaderVlm(mmprojContent),
            modelFilename = "mmproj.gguf", modelUrl = "https://example.test/mmproj.gguf",
            expectedSha256 = sha256Of(mmprojContent)
        )
        val manager = VlmModelManager(mainManager, mmprojManager)

        val result = manager.ensureModelReady()

        assertTrue(result.isFailure)
        assertTrue(!File(modelsDir, "mmproj.gguf").exists())
    }

    @Test
    fun `failure on mmproj after main succeeds fails the whole operation`() = runTest {
        val modelsDir = tempFolder.newFolder("models")
        val mainContent = "main model content"

        val mainManager = LocalModelManager(
            modelsDir = modelsDir, downloader = FakeDownloaderVlm(mainContent),
            modelFilename = "main.gguf", modelUrl = "https://example.test/main.gguf",
            expectedSha256 = sha256Of(mainContent)
        )
        val mmprojManager = LocalModelManager(
            modelsDir = modelsDir,
            downloader = FakeDownloaderVlm("x", shouldFail = java.io.IOException("pas de réseau")),
            modelFilename = "mmproj.gguf", modelUrl = "https://example.test/mmproj.gguf",
            expectedSha256 = sha256Of("whatever")
        )
        val manager = VlmModelManager(mainManager, mmprojManager)

        val result = manager.ensureModelReady()

        assertTrue(result.isFailure)
        assertTrue(mainManager.isModelReady())
    }
}
