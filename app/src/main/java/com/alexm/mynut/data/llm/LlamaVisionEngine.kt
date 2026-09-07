package com.alexm.mynut.data.llm

import android.graphics.Bitmap
import com.alexm.mynut.data.VlmModelManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class LlamaVisionEngine(
    private val modelManager: VlmModelManager
) {
    private val inferenceDispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    @Volatile
    private var handle: Long = 0L

    suspend fun complete(bitmap: Bitmap, prompt: String, maxTokens: Int = 512): Result<String> =
        withContext(inferenceDispatcher) {
            try {
                if (handle == 0L) {
                    val (modelFile, mmprojFile) = modelManager.ensureModelReady().getOrElse {
                        return@withContext Result.failure(it)
                    }
                    handle = LlamaNative.nativeLoadVisionModel(modelFile.absolutePath, mmprojFile.absolutePath)
                    if (handle == 0L) {
                        return@withContext Result.failure(IllegalStateException("Échec du chargement du modèle"))
                    }
                }

                val width = bitmap.width
                val height = bitmap.height
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                val rgbBytes = ByteArray(width * height * 3)
                for (i in pixels.indices) {
                    val pixel = pixels[i]
                    rgbBytes[i * 3] = ((pixel shr 16) and 0xFF).toByte()
                    rgbBytes[i * 3 + 1] = ((pixel shr 8) and 0xFF).toByte()
                    rgbBytes[i * 3 + 2] = (pixel and 0xFF).toByte()
                }

                Result.success(
                    LlamaNative.nativeCompleteWithImage(handle, prompt, width, height, rgbBytes, maxTokens)
                )
            } catch (t: Throwable) {
                // OutOfMemoryError extends Error, not Exception -- and pixel extraction
                // below is the largest allocation on this path, so it must be caught too.
                Result.failure(t)
            }
        }

    fun unload() {
        if (handle != 0L) {
            LlamaNative.nativeUnloadVision(handle)
            handle = 0L
        }
    }
}
