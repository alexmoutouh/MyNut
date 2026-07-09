package com.alexm.mynut.data.llm

import com.alexm.mynut.data.LocalModelManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class LlamaTextEngine(
    private val modelManager: LocalModelManager
) {
    private val inferenceDispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    @Volatile
    private var handle: Long = 0L

    suspend fun complete(prompt: String, maxTokens: Int = 512): Result<String> =
        withContext(inferenceDispatcher) {
            try {
                if (handle == 0L) {
                    val modelFile = modelManager.ensureModelReady().getOrElse {
                        return@withContext Result.failure(it)
                    }
                    handle = LlamaNative.nativeLoadModel(modelFile.absolutePath)
                    if (handle == 0L) {
                        return@withContext Result.failure(IllegalStateException("Échec du chargement du modèle"))
                    }
                }
                Result.success(LlamaNative.nativeComplete(handle, prompt, maxTokens))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun unload() {
        if (handle != 0L) {
            LlamaNative.nativeUnload(handle)
            handle = 0L
        }
    }
}
