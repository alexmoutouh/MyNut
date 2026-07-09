package com.alexm.mynut

import android.app.Application
import com.alexm.mynut.data.AppDatabase
import com.alexm.mynut.data.HttpModelDownloader
import com.alexm.mynut.data.LabelScanApi
import com.alexm.mynut.data.LocalModelManager
import com.alexm.mynut.data.OcrTextLlmScanEngine
import com.alexm.mynut.data.llm.LlamaTextEngine
import java.io.File

class MyNutApplication : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.build(this)
    }

    val labelScanApi: LabelScanApi by lazy {
        LabelScanApi(baseUrl = "http://10.0.2.2:8080")
    }

    val localModelManager: LocalModelManager by lazy {
        LocalModelManager(
            modelsDir = File(filesDir, "models"),
            downloader = HttpModelDownloader()
        )
    }

    val ocrTextLlmScanEngine: OcrTextLlmScanEngine by lazy {
        OcrTextLlmScanEngine(LlamaTextEngine(localModelManager))
    }
}
