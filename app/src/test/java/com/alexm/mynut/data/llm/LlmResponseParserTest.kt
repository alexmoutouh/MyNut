package com.alexm.mynut.data.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmResponseParserTest {

    @Test
    fun `parses strict JSON`() {
        val json = """{"calories": 250.0, "fats": 8.5, "saturatedFats": 2.0, "carbs": 30.0, "sugars": 10.0, "fiber": 3.0, "proteins": 12.0, "sodium": 100.0}"""
        val result = LlmResponseParser.parse(json)
        assertEquals(250.0, result?.calories)
        assertEquals(12.0, result?.proteins)
    }

    @Test
    fun `extracts JSON surrounded by prose`() {
        val response = """Voici le résultat : {"calories": 95.0, "proteins": 4.0} - c'est fini."""
        val result = LlmResponseParser.parse(response)
        assertEquals(95.0, result?.calories)
        assertEquals(4.0, result?.proteins)
    }

    @Test
    fun `missing fields become null`() {
        val json = """{"calories": 95.0}"""
        val result = LlmResponseParser.parse(json)
        assertEquals(95.0, result?.calories)
        assertNull(result?.proteins)
    }

    @Test
    fun `invalid text returns null`() {
        val result = LlmResponseParser.parse("pas du JSON du tout")
        assertNull(result)
    }

    @Test
    fun `empty string returns null`() {
        assertNull(LlmResponseParser.parse(""))
    }
}
