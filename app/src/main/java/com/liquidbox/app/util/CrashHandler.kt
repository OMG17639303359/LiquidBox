package com.liquidbox.app.util

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局崩溃捕获：把完整堆栈写入应用私有目录，供下次启动在 UI 中查看/复制。
 *
 * 说明：RenderThread 的 native 崩溃无法被 Java 层捕获，除此之外主线程、
 * 协程、绘制阶段的未捕获异常都会走到这里。日志只存本地，不上传。
 */
object CrashHandler {

    private const val PREF = "crash_state"
    private const val KEY_COUNT = "count"
    private const val KEY_ACK = "acknowledged"
    private const val FILE_NAME = "crash_last.txt"

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val text = buildString {
                    append("时间: ")
                    append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                    append('\n')
                    append("线程: ").append(thread.name).append('\n')
                    append("系统: Android ").append(Build.VERSION.RELEASE)
                        .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                    append("设备: ").append(Build.MANUFACTURER).append(' ')
                        .append(Build.MODEL).append('\n')
                    append("版本: LiquidBox 1.0.1").append("\n\n")
                    append(throwable.stackTraceToString())
                    var cause = throwable.cause
                    while (cause != null) {
                        append("\n\nCaused by: ").append(cause.stackTraceToString())
                        cause = cause.cause
                    }
                }
                File(app.filesDir, FILE_NAME).writeText(text)
                val prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                // commit()：崩溃进程随时可能被杀，必须同步落盘
                prefs.edit()
                    .putInt(KEY_COUNT, prefs.getInt(KEY_COUNT, 0) + 1)
                    .commit()
            }
            // 交回系统默认处理器，保持正常的崩溃退出行为
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** 是否存在尚未被用户确认过的崩溃 */
    fun hasUnacknowledgedCrash(context: Context): Boolean {
        val p = prefs(context)
        return p.getInt(KEY_COUNT, 0) > p.getInt(KEY_ACK, 0)
    }

    fun crashCount(context: Context): Int = prefs(context).getInt(KEY_COUNT, 0)

    fun acknowledge(context: Context) {
        prefs(context).edit().putInt(KEY_ACK, crashCount(context)).commit()
    }

    fun lastCrash(context: Context): String = runCatching {
        File(context.filesDir, FILE_NAME).readText()
    }.getOrDefault("")

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
        prefs(context).edit().clear().commit()
    }
}
