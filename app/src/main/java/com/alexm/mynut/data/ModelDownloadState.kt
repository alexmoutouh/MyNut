package com.alexm.mynut.data

sealed class ModelDownloadState {
    object Idle : ModelDownloadState()
    data class Downloading(val progress: Float) : ModelDownloadState()
    object Ready : ModelDownloadState()
    data class Failed(val message: String) : ModelDownloadState()
}
