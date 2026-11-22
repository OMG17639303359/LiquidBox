package com.liquidbox.app

import android.app.Application
import android.content.Context
import com.liquidbox.app.util.CrashHandler

class LiquidBoxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = this
        // 尽早安装，覆盖后续 Compose 组合/绘制阶段的异常
        CrashHandler.install(this)
    }

    companion object {
        @Volatile
        lateinit var appContext: Context
            private set
    }
}
