package com.alexm.mynut.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.alexm.mynut.data.llm.LlamaVisionEngine
import com.alexm.mynut.data.llm.LlmResponseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Qwen3-VL caps image input at 4096 vision tokens (~28x28px each), shared with the
// text prompt/completion inside a 4096-token context. A full-resolution camera photo
// (8-50 MP) would consume the entire context on the image alone and fail to decode.
// Capping the longest side keeps well clear of that budget while still being far more
// resolution than a printed nutrition table needs (etiket2.jpg, 800x729, already
// extracted 7/8 fields exactly at this scale).
private const val MAX_IMAGE_DIMENSION = 1024

private val VLM_PROMPT = """
    Voici une photo d'étiquette nutritionnelle. Elle peut contenir un tableau à plusieurs colonnes (par exemple "pour 100g/100ml", "par portion", "% des apports de référence").

    Utilise UNIQUEMENT la colonne "pour 100g" ou "pour 100ml" si le tableau en a plusieurs. Ignore complètement les colonnes "par portion" et "%".

    Réponds UNIQUEMENT avec un objet JSON strict, sans texte autour, avec exactement ces clés (valeurs numériques pour 100g/100ml, null si absente) :
    {"calories": <nombre ou null>, "fats": <nombre ou null>, "saturatedFats": <nombre ou null>, "carbs": <nombre ou null>, "sugars": <nombre ou null>, "fiber": <nombre ou null>, "proteins": <nombre ou null>, "sodium": <nombre ou null>}
""".trimIndent()

class VlmScanEngine(
    private val llamaVisionEngine: LlamaVisionEngine
) : LocalLabelScanEngine {

    override suspend fun scanLabel(imageBytes: ByteArray): Result<NutritionalValues> {
        return try {
            val bitmap = withContext(Dispatchers.Default) {
                decodeScaledBitmap(imageBytes, MAX_IMAGE_DIMENSION)
            } ?: return Result.failure(IllegalArgumentException("Photo illisible"))

            val completion = llamaVisionEngine.complete(bitmap, VLM_PROMPT).getOrElse {
                return Result.failure(it)
            }

            val values = LlmResponseParser.parse(completion)
                ?: return Result.failure(IllegalStateException("Réponse IA illisible"))

            Result.success(values)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}

/**
 * Decodes [imageBytes] downsampled so its longest side is at most [maxDimension],
 * without ever fully materializing the original full-resolution bitmap in memory.
 */
private fun decodeScaledBitmap(imageBytes: ByteArray, maxDimension: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var inSampleSize = 1
    while (bounds.outWidth / (inSampleSize * 2) >= maxDimension &&
        bounds.outHeight / (inSampleSize * 2) >= maxDimension
    ) {
        inSampleSize *= 2
    }

    val decoded = BitmapFactory.decodeByteArray(
        imageBytes, 0, imageBytes.size,
        BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
    ) ?: return null

    val longestSide = maxOf(decoded.width, decoded.height)
    if (longestSide <= maxDimension) return decoded

    val scale = maxDimension.toFloat() / longestSide
    val scaledWidth = (decoded.width * scale).toInt().coerceAtLeast(1)
    val scaledHeight = (decoded.height * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(decoded, scaledWidth, scaledHeight, true)
    if (scaled !== decoded) decoded.recycle()
    return scaled
}
