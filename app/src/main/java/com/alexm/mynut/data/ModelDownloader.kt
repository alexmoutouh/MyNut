package com.alexm.mynut.data

import java.io.File

interface ModelDownloader {
    suspend fun download(url: String, destination: File, onProgress: (Float) -> Unit)
}
