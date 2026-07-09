package com.alexm.mynut.data

interface LocalLabelScanEngine {
    suspend fun scanLabel(imageBytes: ByteArray): Result<NutritionalValues>
}
