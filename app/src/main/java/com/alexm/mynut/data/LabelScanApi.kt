package com.alexm.mynut.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class NutritionalValues(
    val calories: Double? = null,
    val fats: Double? = null,
    val saturatedFats: Double? = null,
    val carbs: Double? = null,
    val sugars: Double? = null,
    val fiber: Double? = null,
    val proteins: Double? = null,
    val sodium: Double? = null
)

class LabelScanApi(private val baseUrl: String) {

    suspend fun scanLabel(imageBytes: ByteArray): Result<NutritionalValues> =
        withContext(Dispatchers.IO) {
            try {
                val boundary = "----FormBoundary${System.currentTimeMillis()}"
                val url = URL("$baseUrl/scan")
                val connection = url.openConnection() as HttpURLConnection

                connection.apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 30_000
                    readTimeout = 60_000
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                }

                DataOutputStream(connection.outputStream).use { output ->
                    output.writeBytes("--$boundary\r\n")
                    output.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"label.jpg\"\r\n")
                    output.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                    output.write(imageBytes)
                    output.writeBytes("\r\n--$boundary--\r\n")
                    output.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext Result.failure(
                        Exception("API error: HTTP $responseCode")
                    )
                }

                val responseBody = connection.inputStream.use { input ->
                    ByteArrayOutputStream().also { input.copyTo(it) }.toString("UTF-8")
                }

                connection.disconnect()
                Result.success(parseResponse(responseBody))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun parseResponse(json: String): NutritionalValues {
        val obj = JSONObject(json)
        return NutritionalValues(
            calories = obj.optDoubleOrNull("calories"),
            fats = obj.optDoubleOrNull("fats"),
            saturatedFats = obj.optDoubleOrNull("saturatedFats"),
            carbs = obj.optDoubleOrNull("carbs"),
            sugars = obj.optDoubleOrNull("sugars"),
            fiber = obj.optDoubleOrNull("fiber"),
            proteins = obj.optDoubleOrNull("proteins"),
            sodium = obj.optDoubleOrNull("sodium")
        )
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        return if (has(key) && !isNull(key)) getDouble(key) else null
    }
}