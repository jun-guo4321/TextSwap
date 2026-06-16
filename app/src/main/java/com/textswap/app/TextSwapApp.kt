package com.textswap.app

import android.app.Application
import com.textswap.app.service.FontMatcher
import com.textswap.app.service.InpaintService
import com.textswap.app.service.OcrService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TextSwapApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 后台加载AI模型（不阻塞启动）
        appScope.launch(Dispatchers.IO) {
            InpaintService.init()
            FontMatcher.init()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        OcrService.close()
        FontMatcher.close()
    }

    companion object {
        lateinit var instance: TextSwapApp
            private set
    }
}