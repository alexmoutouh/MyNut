package com.alexm.mynut

import android.app.Application
import com.alexm.mynut.data.AppDatabase
import com.alexm.mynut.data.HttpModelDownloader
import com.alexm.mynut.data.LabelScanApi
import com.alexm.mynut.data.LocalModelManager
import com.alexm.mynut.data.VlmModelManager
import com.alexm.mynut.data.VlmScanEngine
import com.alexm.mynut.data.llm.LlamaVisionEngine
import java.io.File

class MyNutApplication : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.build(this)
    }

    val labelScanApi: LabelScanApi by lazy {
        LabelScanApi(baseUrl = "http://10.0.2.2:8080")
    }

    val localModelManager: VlmModelManager by lazy {
        val modelsDir = File(filesDir, "models")
        VlmModelManager(
            mainModelManager = LocalModelManager(
                modelsDir = modelsDir,
                downloader = HttpModelDownloader(),
                modelFilename = VlmModelManager.MAIN_MODEL_FILENAME,
                modelUrl = VlmModelManager.MAIN_MODEL_URL,
                expectedSha256 = VlmModelManager.MAIN_MODEL_SHA256
            ),
            mmprojModelManager = LocalModelManager(
                modelsDir = modelsDir,
                downloader = HttpModelDownloader(),
                modelFilename = VlmModelManager.MMPROJ_FILENAME,
                modelUrl = VlmModelManager.MMPROJ_URL,
                expectedSha256 = VlmModelManager.MMPROJ_SHA256
            )
        )
    }

    val vlmScanEngine: VlmScanEngine by lazy {
        VlmScanEngine(LlamaVisionEngine(localModelManager))
    }
}
