package com.alexm.mynut.data

import android.graphics.BitmapFactory
import com.alexm.mynut.data.llm.LlamaTextEngine
import com.alexm.mynut.data.llm.LlmResponseParser
import com.alexm.mynut.data.llm.PromptBuilder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class OcrTextLlmScanEngine(
    private val llamaTextEngine: LlamaTextEngine
) : LocalLabelScanEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun scanLabel(imageBytes: ByteArray): Result<NutritionalValues> {
        val bitmap = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } ?: return Result.failure(IllegalArgumentException("Photo illisible"))

        val ocrText = try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
        } catch (e: Exception) {
            return Result.failure(e)
        }

        if (ocrText.isBlank()) {
            return Result.failure(IllegalStateException("Aucun texte détecté sur la photo"))
        }

        val completion = llamaTextEngine.complete(PromptBuilder.buildPrompt(ocrText)).getOrElse {
            return Result.failure(it)
        }

        val values = LlmResponseParser.parse(completion)
            ?: return Result.failure(IllegalStateException("Réponse IA illisible"))

        return Result.success(values)
    }
}
