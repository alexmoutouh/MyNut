package com.alexm.mynut.data

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class VlmModelManager(
    private val mainModelManager: LocalModelManager,
    private val mmprojModelManager: LocalModelManager
) {
    private val _state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val state: StateFlow<ModelDownloadState> = _state.asStateFlow()

    fun isModelReady(): Boolean =
        mainModelManager.isModelReady() && mmprojModelManager.isModelReady()

    suspend fun ensureModelReady(): Result<Pair<File, File>> = coroutineScope {
        val mainCollector = launch {
            mainModelManager.state.collect { s ->
                if (s is ModelDownloadState.Downloading) _state.value = s
            }
        }
        val mainResult = mainModelManager.ensureModelReady()
        mainCollector.cancel()

        val mainFile = mainResult.getOrElse {
            _state.value = ModelDownloadState.Failed(it.message ?: "Échec du modèle principal")
            return@coroutineScope Result.failure<Pair<File, File>>(it)
        }

        val mmprojCollector = launch {
            mmprojModelManager.state.collect { s ->
                if (s is ModelDownloadState.Downloading) _state.value = s
            }
        }
        val mmprojResult = mmprojModelManager.ensureModelReady()
        mmprojCollector.cancel()

        val mmprojFile = mmprojResult.getOrElse {
            _state.value = ModelDownloadState.Failed(it.message ?: "Échec du fichier mmproj")
            return@coroutineScope Result.failure<Pair<File, File>>(it)
        }

        _state.value = ModelDownloadState.Ready
        Result.success(mainFile to mmprojFile)
    }

    companion object {
        const val MAIN_MODEL_FILENAME = "Qwen3VL-4B-Instruct-Q4_K_M.gguf"
        const val MAIN_MODEL_URL = "https://huggingface.co/Qwen/Qwen3-VL-4B-Instruct-GGUF/resolve/main/Qwen3VL-4B-Instruct-Q4_K_M.gguf"
        const val MAIN_MODEL_SHA256 = "66358cb18bb6b3b1b6675aa412c7a88ef01d228f481184d13668e5201c730a0a"

        const val MMPROJ_FILENAME = "mmproj-Qwen3VL-4B-Instruct-Q8_0.gguf"
        const val MMPROJ_URL = "https://huggingface.co/Qwen/Qwen3-VL-4B-Instruct-GGUF/resolve/main/mmproj-Qwen3VL-4B-Instruct-Q8_0.gguf"
        const val MMPROJ_SHA256 = "30ba2c7dd3127a4561b6cba9d13d0f711c91bdb38742e2f56d73c8cb596bd06d"
    }
}
