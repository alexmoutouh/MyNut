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
    private val modelFilename: String,
    private val modelUrl: String,
    private val expectedSha256: String
) {
    private val _state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val state: StateFlow<ModelDownloadState> = _state.asStateFlow()

    val modelFile: File get() = File(modelsDir, modelFilename)

    fun isModelReady(): Boolean = modelFile.exists() && verifyChecksum(modelFile, expectedSha256)

    suspend fun ensureModelReady(): Result<File> = withContext(Dispatchers.IO) {
        if (isModelReady()) {
            _state.value = ModelDownloadState.Ready
            return@withContext Result.success(modelFile)
        }

        _state.value = ModelDownloadState.Downloading(0f)
        modelsDir.mkdirs()
        val tempFile = File(modelsDir, "$modelFilename.tmp")

        try {
            downloader.download(modelUrl, tempFile) { progress ->
                _state.value = ModelDownloadState.Downloading(progress)
            }
            if (!verifyChecksum(tempFile, expectedSha256)) {
                tempFile.delete()
                _state.value = ModelDownloadState.Failed("Fichier téléchargé corrompu")
                return@withContext Result.failure(IllegalStateException("Checksum invalide pour $modelFilename"))
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
}
