package com.alexm.mynut.data.llm

internal object LlamaNative {
    init {
        System.loadLibrary("mynut_llm")
    }

    external fun nativePing(): Int
    external fun nativeLoadModel(modelPath: String): Long
    external fun nativeComplete(handle: Long, prompt: String, maxTokens: Int): String
    external fun nativeUnload(handle: Long)
}
