package com.alexm.mynut

import android.app.Application
import com.alexm.mynut.data.AppDatabase
import com.alexm.mynut.data.LabelScanApi

class MyNutApplication : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.build(this)
    }

    val labelScanApi: LabelScanApi by lazy {
        LabelScanApi(baseUrl = "http://10.0.2.2:8080")
    }
}