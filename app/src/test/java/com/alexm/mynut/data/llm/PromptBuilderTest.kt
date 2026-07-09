package com.alexm.mynut.data.llm

import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    @Test
    fun `prompt contains the OCR text`() {
        val prompt = PromptBuilder.buildPrompt("Valeurs nutritionnelles pour 100g : 250 kcal")
        assertTrue(prompt.contains("250 kcal"))
    }

    @Test
    fun `prompt lists all eight expected JSON keys`() {
        val prompt = PromptBuilder.buildPrompt("texte quelconque")
        listOf(
            "calories", "fats", "saturatedFats", "carbs",
            "sugars", "fiber", "proteins", "sodium"
        ).forEach { key ->
            assertTrue("clé manquante: $key", prompt.contains(key))
        }
    }

    @Test
    fun `prompt trims surrounding whitespace from OCR text`() {
        val prompt = PromptBuilder.buildPrompt("  texte avec espaces  \n")
        assertTrue(prompt.endsWith("texte avec espaces"))
    }
}
