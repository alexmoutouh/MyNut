package com.alexm.mynut.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class LocalModelManager(
    private val modelsDir: File,
    private val downloader: ModelDownloader,
    private val modelUrl: String = MODEL_URL,
    private val expectedSha256: String = MODEL_SHA256
) {
    private val _state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val state: StateFlow<ModelDownloadState> = _state.asStateFlow()

    val modelFile: File get() = File(modelsDir, MODEL_FILENAME)

    fun isModelReady(): Boolean = modelFile.exists() && verifyChecksum(modelFile, expectedSha256)

    suspend fun ensureModelReady(): Result<File> = withContext(Dispatchers.IO) {
        if (isModelReady()) {
            _state.value = ModelDownloadState.Ready
            return@withContext Result.success(modelFile)
        }

        _state.value = ModelDownloadState.Downloading(0f)
        modelsDir.mkdirs()
        val tempFile = File(modelsDir, "$MODEL_FILENAME.tmp")

        try {
            downloader.download(modelUrl, tempFile) { progress ->
                _state.value = ModelDownloadState.Downloading(progress)
            }
            if (!verifyChecksum(tempFile, expectedSha256)) {
                tempFile.delete()
                _state.value = ModelDownloadState.Failed("Fichier téléchargé corrompu")
                return@withContext Result.failure(IllegalStateException("Checksum invalide pour $MODEL_FILENAME"))
            }
            tempFile.renameTo(modelFile)
            _state.value = ModelDownloadState.Ready
            Result.success(modelFile)
        } catch (e: Exception) {
            tempFile.delete()
            _state.value = ModelDownloadState.Failed(e.message ?: "Téléchargement échoué")
            Result.failure(e)
        }
    }

    private fun verifyChecksum(file: File, expected: String): Boolean {
        if (!file.exists()) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        return hash.equals(expected, ignoreCase = true)
    }

    companion object {
        const val MODEL_FILENAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        const val MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
        const val MODEL_SHA256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"
    }
}
