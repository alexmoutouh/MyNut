package com.alexm.mynut.data.llm

internal object LlamaNative {
    init {
        System.loadLibrary("mynut_llm")
    }

    external fun nativePing(): Int

    external fun nativeLoadVisionModel(modelPath: String, mmprojPath: String): Long
    external fun nativeCompleteWithImage(
        handle: Long, prompt: String,
        imageWidth: Int, imageHeight: Int, imagePixels: ByteArray,
        maxTokens: Int
    ): String
    external fun nativeUnloadVision(handle: Long)
}
