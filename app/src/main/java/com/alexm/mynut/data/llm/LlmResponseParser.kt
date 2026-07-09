package com.alexm.mynut.data.llm

import com.alexm.mynut.data.NutritionalValues
import org.json.JSONObject

object LlmResponseParser {

    fun parse(rawResponse: String): NutritionalValues? {
        val jsonBlock = extractJsonBlock(rawResponse) ?: return null
        val obj = try {
            JSONObject(jsonBlock)
        } catch (e: Exception) {
            return null
        }
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

    // Real small on-device models tend to keep rambling past their first answer (extra
    // "Note:" / repeated example blocks, sometimes with a different, hallucinated schema).
    // Taking the first '{' through the *last* '}' in the whole response would swallow all
    // of that trailing noise into one invalid blob. Instead, walk from the first '{' and
    // track brace depth (ignoring braces inside quoted strings) to find the matching close
    // of just that first object.
    private fun extractJsonBlock(text: String): String? {
        val start = text.indexOf('{')
        if (start == -1) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    // Real small models often emit numeric fields as quoted strings ("250"), or as
    // clearly-non-numeric placeholders ("null " with a stray space, "" empty, "10.0 g" with
    // units) when they have nothing to report. Treat any field that can't be coerced to a
    // number as simply absent rather than failing the whole response.
    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return try {
            getDouble(key)
        } catch (e: Exception) {
            null
        }
    }
}
