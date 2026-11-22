package com.liquidbox.app.shizuku

import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku 连接与命令执行层。
 *
 * 通过 Shizuku 的 AIDL IShizukuService.newProcess 以 shell(uid 2000) / root 身份
 * 启动进程执行命令，不依赖 hidden API 反射，覆盖 pm / am / cmd / settings / input
 * 等全部工具箱指令。IRemoteProcess 提供的是 ParcelFileDescriptor，这里自行封装为流。
 *
 * 稳健性：所有 Shizuku 调用（含监听器回调）都放在 IO 协程并包 runCatching，
 * 避免 Shizuku 未安装/服务卡死时阻塞主线程或在主线程抛异常导致闪退。
 */
object ShizukuShell {

    const val PERMISSION_REQUEST_CODE = 8421

    sealed interface State {
        data object BinderWaiting : State
        data object NeedPermission : State
        data object Ready : State
    }

    private val _state = MutableStateFlow<State>(State.BinderWaiting)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _uid = MutableStateFlow(-1)
    val uid: StateFlow<Int> = _uid.asStateFlow()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _state.value = State.BinderWaiting
    }
    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    @Volatile
    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        ioScope.launch {
            // 未安装 Shizuku/Sui 时这些调用也可能抛异常，全部吞掉，只影响连接状态
            runCatching {
                Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
                Shizuku.addBinderDeadListener(binderDeadListener)
                Shizuku.addRequestPermissionResultListener(permissionResultListener)
                refresh()
            }
        }
    }

    fun refresh() {
        ioScope.launch {
            val next = runCatching {
                when {
                    !Shizuku.pingBinder() -> State.BinderWaiting
                    Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                        State.NeedPermission
                    else -> {
                        _uid.value = runCatching { Shizuku.getUid() }.getOrDefault(-1)
                        State.Ready
                    }
                }
            }.getOrDefault(State.BinderWaiting)
            _state.value = next
        }
    }

    fun requestPermission() {
        ioScope.launch {
            runCatching {
                if (Shizuku.pingBinder() &&
                    Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
                ) {
                    Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
                }
            }
        }
    }

    val isReady: Boolean get() = _state.value is State.Ready

    private fun service(): IShizukuService? {
        val binder: IBinder = runCatching { Shizuku.getBinder() }.getOrNull() ?: return null
        return runCatching { IShizukuService.Stub.asInterface(binder) }.getOrNull()
    }

    /** 同步执行一条 shell 命令，返回 stdout+stderr 与退出码 */
    fun exec(command: String, timeoutSeconds: Long = 25L): ShellResult {
        if (!isReady) return ShellResult(-1, "", "Shizuku 未就绪（需先启动 Shizuku/Sui 并授权）")
        val svc = service() ?: return ShellResult(-1, "", "IShizukuService 不可用")
        var remote: IRemoteProcess? = null
        return try {
            remote = svc.newProcess(arrayOf("sh", "-c", command), null, null)
            val p = remote
            val stdout = StringBuilder()
            val stderr = StringBuilder()

            val outThread = Thread {
                runCatching {
                    ParcelFileDescriptor.AutoCloseInputStream(p.inputStream).use { stream ->
                        BufferedReader(InputStreamReader(stream)).useLines { lines ->
                            lines.forEach { stdout.appendLine(it) }
                        }
                    }
                }
            }
            val errThread = Thread {
                runCatching {
                    ParcelFileDescriptor.AutoCloseInputStream(p.errorStream).use { stream ->
                        BufferedReader(InputStreamReader(stream)).useLines { lines ->
                            lines.forEach { stderr.appendLine(it) }
                        }
                    }
                }
            }
            outThread.start()
            errThread.start()

            var exitCode = -1
            val waitThread = Thread {
                exitCode = runCatching { p.waitFor() }.getOrDefault(-1)
            }
            waitThread.start()
            waitThread.join(timeoutSeconds * 1000L)
            val finished = !waitThread.isAlive
            if (!finished) runCatching { p.destroy() }
            outThread.join(1200)
            errThread.join(1200)

            ShellResult(
                code = if (finished) exitCode else -1,
                stdout = stdout.toString(),
                stderr = stderr.toString(),
            )
        } catch (e: Exception) {
            ShellResult(-1, "", e.message ?: e.toString())
        } finally {
            runCatching { remote?.destroy() }
        }
    }

    /** 批量执行（一键配置），逐条拼接 */
    fun execBatch(commands: List<String>): ShellResult =
        exec(commands.joinToString(separator = "\n"))

    data class ShellResult(val code: Int, val stdout: String, val stderr: String) {
        val ok: Boolean get() = code == 0
        val output: String get() = (stdout + stderr).trim().ifEmpty { "(无输出)" }
    }

    /** 供 AI 工具链读屏（uiautomator dump） */
    fun dumpUiHierarchy(): String {
        val r = exec("uiautomator dump /sdcard/lb_window.xml >/dev/null 2>&1; cat /sdcard/lb_window.xml")
        return if (r.stdout.isNotBlank()) r.stdout.trim() else r.output
    }

    /** 供 AI 工具链截图 */
    fun screenshot(): String {
        val path = "/sdcard/lb_screen_${System.currentTimeMillis()}.png"
        val r = exec("screencap -p $path && echo OK")
        return if (r.stdout.contains("OK")) path else "截图失败: ${r.output}"
    }

    fun dispose() {
        if (!initialized) return
        runCatching {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        }
        initialized = false
    }
}
