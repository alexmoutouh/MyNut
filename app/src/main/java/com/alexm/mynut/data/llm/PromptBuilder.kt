package com.alexm.mynut.data.llm

object PromptBuilder {

    fun buildPrompt(ocrText: String): String = """
        Voici le texte extrait par OCR d'une photo d'étiquette nutritionnelle. Le texte peut contenir des erreurs de reconnaissance, des sauts de ligne mal placés ou du texte parasite (nom du produit, ingrédients, etc.).

        Réponds UNIQUEMENT avec un objet JSON strict, sans texte autour, avec exactement ces clés (valeurs numériques par portion telle qu'indiquée sur l'étiquette, null si absente) :
        {"calories": <nombre ou null>, "fats": <nombre ou null>, "saturatedFats": <nombre ou null>, "carbs": <nombre ou null>, "sugars": <nombre ou null>, "fiber": <nombre ou null>, "proteins": <nombre ou null>, "sodium": <nombre ou null>}

        Texte OCR :
        ${ocrText.trim()}
    """.trimIndent()
}
