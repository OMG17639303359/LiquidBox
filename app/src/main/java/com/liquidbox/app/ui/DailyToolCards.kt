package com.liquidbox.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.liquidbox.app.glass.LiquidButton
import com.liquidbox.app.glass.LiquidSlider
import com.liquidbox.app.glass.LocalGlassPalette
import com.liquidbox.app.shizuku.ShizukuShell
import com.liquidbox.app.shizuku.Toolbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 统一的执行入口：切到 IO，把回显送给页面底部的结果浮层 */
private suspend fun execResult(command: String, timeout: Long = 20L): String {
    val r = withContext(Dispatchers.IO) { ShizukuShell.exec(command, timeout) }
    return "[${if (r.ok) "成功" else "失败"}] ${r.output.take(600)}"
}

/** 从 shell 输出里取 key=value */
private fun parsePairs(out: String): Map<String, String> =
    out.lineSequence()
        .map { it.trim() }
        .filter { '=' in it && !it.startsWith("echo ") }
        .mapNotNull {
            val k = it.substringBefore('=').trim()
            val v = it.substringAfter('=').trim()
            if (k.isBlank()) null else k to v
        }
        .toMap()

// =====================================================================
// 1. 快捷开关（带实时状态回读）
// =====================================================================

@Composable
fun DailyQuickTogglesCard(onResult: (String) -> Unit = {}) {
    val palette = LocalGlassPalette.current
    val scope = rememberCoroutineScope()
    var states by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("正在读取系统状态…") }

    suspend fun refresh() {
        val r = withContext(Dispatchers.IO) { ShizukuShell.exec(Toolbox.Daily.queryAll(), 25L) }
        val pairs = parsePairs(r.stdout)
        if (pairs.isEmpty()) {
            loaded = false
            statusText = "状态读取失败：请先启动 Shizuku / Sui 并授权"
        } else {
            states = Toolbox.Daily.toggles.associate { t ->
                t.id to t.isOn(pairs[t.id].orEmpty())
            }
            loaded = true
            statusText = "点击即切换 · 状态从系统实时读取"
        }
    }

    LaunchedEffect(Unit) { refresh() }

    GlassCard {
        T("快捷开关", size = 15, weight = FontWeight.Bold)
        T(statusText, size = 11, color = palette.textSecondary)
        Toolbox.Daily.toggles.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8f.dp),
            ) {
                row.forEach { t ->
                    val on = states[t.id] == true
                    LiquidButton(
                        onClick = {
                            if (busy) return@LiquidButton
                            val target = !on
                            states = states + (t.id to target)
                            busy = true
                            scope.launch {
                                try {
                                    val cmd = if (target) t.enable else t.disable
                                    onResult("[${t.label}] ${execResult(cmd, 20L)}")
                                    delay(400L)
                                    refresh()
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        tint = if (on) palette.accent.copy(alpha = 0.35f) else null,
                    ) {
                        T(
                            t.label + if (loaded) if (on) " · 开" else " · 关" else "",
                            size = 12,
                            align = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
                if (row.size == 1) {
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
            }
        }
        T(
            "提示：WiFi / 移动数据 / 蓝牙在个别 ROM 上需要 root 或厂商支持，执行结果会在底部显示。",
            size = 10,
            color = palette.textSecondary,
        )
    }
}

// =====================================================================
// 2. 屏幕亮度与休眠
// =====================================================================

@Composable
fun DailyDisplayCard(backdrop: Backdrop, onResult: (String) -> Unit = {}) {
    val palette = LocalGlassPalette.current
    val scope = rememberCoroutineScope()
    var level by remember { mutableStateOf(120f) }
    var currentInfo by remember { mutableStateOf("") }

    suspend fun read() {
        val r = withContext(Dispatchers.IO) {
            ShizukuShell.exec(Toolbox.Daily.readDisplay(), 15L)
        }
        val p = parsePairs(r.stdout)
        if (p.isEmpty()) {
            currentInfo = "状态读取失败：请先连接 Shizuku"
            return
        }
        p["BR"]?.toFloatOrNull()?.let { level = it.coerceIn(1f, 255f) }
        val mode = if (p["MODE"] == "1") "自动" else "手动"
        val ms = p["TIMEOUT"]?.toLongOrNull()
        val timeoutLabel = when {
            ms == null -> "未知"
            ms >= 600000 -> "${ms / 60000} 分钟以上"
            ms >= 60000 -> "${ms / 60000} 分钟"
            else -> "${ms / 1000} 秒"
        }
        currentInfo = "当前亮度 ${level.roundToInt()}/255 · $mode · 休眠 $timeoutLabel"
    }

    LaunchedEffect(Unit) { read() }

    fun apply(cmd: String, thenRead: Boolean = true) {
        scope.launch {
            onResult(execResult(cmd, 15L))
            if (thenRead) {
                delay(300L)
                read()
            }
        }
    }

    GlassCard {
        T("屏幕亮度与休眠", size = 15, weight = FontWeight.Bold)
        T(if (currentInfo.isBlank()) "读取中…" else currentInfo, size = 11, color = palette.textSecondary)
        LiquidSlider(
            value = { level },
            onValueChange = { level = it },
            valueRange = 1f..255f,
            backdrop = backdrop,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6f.dp)) {
            listOf(40f, 120f, 200f, 255f).forEach { v ->
                LiquidButton(
                    onClick = { level = v },
                    modifier = Modifier.weight(1f),
                    tint = if (kotlin.math.abs(level - v) < 8f) palette.accent.copy(alpha = 0.35f) else null,
                ) { T(v.roundToInt().toString(), size = 11, align = TextAlign.Center) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
            LiquidButton(
                onClick = { apply(Toolbox.Daily.brightness(level.roundToInt().coerceIn(1, 255))) },
                modifier = Modifier.weight(1f),
            ) { T("应用亮度", size = 12, align = TextAlign.Center) }
            LiquidButton(
                onClick = { apply("settings put system screen_brightness_mode 1; echo AUTO_BRIGHTNESS") },
                modifier = Modifier.weight(1f),
            ) { T("恢复自动亮度", size = 12, align = TextAlign.Center) }
        }

        T("自动休眠", size = 12, weight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
            Toolbox.Daily.timeouts.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6f.dp)) {
                    row.forEach { t ->
                        LiquidButton(
                            onClick = { apply(Toolbox.Daily.timeout(t.command)) },
                            modifier = Modifier.weight(1f),
                        ) { T(t.label, size = 11, align = TextAlign.Center) }
                    }
                    repeat(3 - row.size) {
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// =====================================================================
// 3. 媒体与音量
// =====================================================================

@Composable
fun DailyMediaCard(onResult: (String) -> Unit = {}) {
    val palette = LocalGlassPalette.current
    val scope = rememberCoroutineScope()
    GlassCard {
        T("媒体与音量", size = 15, weight = FontWeight.Bold)
        T("控制当前播放的音乐 / 视频，以及系统媒体音量", size = 11, color = palette.textSecondary)
        Toolbox.Daily.mediaKeys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                row.forEach { k ->
                    LiquidButton(
                        onClick = {
                            scope.launch { onResult("[${k.label}] ${execResult(k.command, 10L)}") }
                        },
                        modifier = Modifier.weight(1f),
                    ) { T(k.label, size = 12, align = TextAlign.Center, maxLines = 1) }
                }
            }
        }
    }
}

// =====================================================================
// 4. 设备信息一览 + 一键复制
// =====================================================================

private val InfoLabels = listOf(
    "model" to "机型",
    "brand" to "品牌",
    "android" to "Android 版本",
    "sdk" to "SDK 版本",
    "rom" to "系统版本",
    "resolution" to "分辨率",
    "density" to "像素密度",
    "battery" to "当前电量",
    "temp" to "电池温度",
    "charging" to "充电状态码",
    "mem" to "总内存",
    "mem_free" to "可用内存",
    "storage" to "存储 总量/可用",
    "uptime" to "已运行",
    "selinux" to "SELinux",
    "uid" to "Shizuku UID",
)

private fun formatInfo(key: String, v: String): String = when (key) {
    "battery" -> "$v %"
    "temp" -> "${(v.toIntOrNull() ?: 0) / 10.0} °C"
    "mem", "mem_free" -> {
        val kb = v.toLongOrNull() ?: 0L
        if (kb >= 1024) "${kb / 1024} MB" else "$v KB"
    }
    "density" -> "$v dpi"
    else -> v
}

@Composable
fun DailyDeviceInfoCard(onResult: (String) -> Unit = {}) {
    val palette = LocalGlassPalette.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }

    suspend fun load() {
        loading = true
        val r = withContext(Dispatchers.IO) { ShizukuShell.exec(Toolbox.Daily.deviceInfo, 25L) }
        info = parsePairs(r.stdout)
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    val text = buildString {
        appendLine("LiquidBox 设备信息")
        InfoLabels.forEach { (k, label) ->
            val v = info[k]
            if (!v.isNullOrBlank() && v != "null") appendLine("$label：${formatInfo(k, v)}")
        }
    }

    GlassCard {
        T("设备信息一览", size = 15, weight = FontWeight.Bold)
        T(
            when {
                loading -> "正在读取…"
                info.isEmpty() -> "读取失败：请先启动 Shizuku / Sui 并授权"
                else -> "共 ${InfoLabels.count { !info[it.first].isNullOrBlank() }} 项"
            },
            size = 11,
            color = palette.textSecondary,
        )
        InfoLabels.forEach { (k, label) ->
            val v = info[k]
            if (!v.isNullOrBlank() && v != "null") {
                KeyValueRow(label, formatInfo(k, v))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
            LiquidButton(
                onClick = { scope.launch { load() } },
                modifier = Modifier.weight(1f),
            ) { T("刷新", size = 12, align = TextAlign.Center) }
            LiquidButton(
                onClick = {
                    clipboard.setText(AnnotatedString(text.trim()))
                    onResult("已复制设备信息到剪贴板")
                },
                modifier = Modifier.weight(1f),
            ) { T("复制信息", size = 12, align = TextAlign.Center) }
        }
    }
}

// =====================================================================
// 5. 一键维护
// =====================================================================

@Composable
fun DailyMaintenanceCard(onResult: (String) -> Unit = {}) {
    val palette = LocalGlassPalette.current
    val scope = rememberCoroutineScope()
    GlassCard {
        T("随手维护", size = 15, weight = FontWeight.Bold)
        T("常用清理命令，点一下就执行；「结束后台进程」会关闭后台应用", size = 11,
            color = palette.textSecondary)
        Toolbox.Daily.maintenance.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                row.forEach { b ->
                    LiquidButton(
                        onClick = {
                            scope.launch { onResult("[${b.label}] ${execResult(b.command, 30L)}") }
                        },
                        modifier = Modifier.weight(1f),
                    ) { T(b.label, size = 12, align = TextAlign.Center, maxLines = 1) }
                }
                if (row.size == 1) {
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// =====================================================================
// 6. 屏幕录制 / 截图
// =====================================================================

@Composable
fun DailyRecordCard(onResult: (String) -> Unit = {}) {
    val palette = LocalGlassPalette.current
    val scope = rememberCoroutineScope()
    var seconds by remember { mutableStateOf(10) }
    var recording by remember { mutableStateOf(false) }

    GlassCard {
        T("屏幕录制与截图", size = 15, weight = FontWeight.Bold)
        T("录制保存到 /sdcard/LB_rec.mp4；部分机型可能不支持录屏", size = 11,
            color = palette.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(6f.dp)) {
            Toolbox.Daily.recordSeconds.forEach { s ->
                LiquidButton(
                    onClick = { seconds = s },
                    modifier = Modifier.weight(1f),
                    tint = if (seconds == s) palette.accent.copy(alpha = 0.35f) else null,
                ) { T("${s}s", size = 11, align = TextAlign.Center) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
            LiquidButton(
                onClick = {
                    if (recording) return@LiquidButton
                    recording = true
                    scope.launch {
                        onResult("开始录制 ${seconds}s…")
                        val r = execResult(Toolbox.Daily.record(seconds), (seconds + 20).toLong())
                        onResult(r)
                        recording = false
                    }
                },
                modifier = Modifier.weight(1f),
            ) { T(if (recording) "录制中…" else "开始录制", size = 12, align = TextAlign.Center) }
            LiquidButton(
                onClick = {
                    scope.launch {
                        val action = Toolbox.byId("screenshot")
                        if (action == null) {
                            onResult("截图动作不可用")
                        } else {
                            val r = execResult(action.command("", ""), 20L)
                            onResult("[截图] $r")
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            ) { T("立即截图", size = 12, align = TextAlign.Center) }
        }
        T("录制期间请把应用留在前台，录完会显示文件路径。", size = 10,
            color = palette.textSecondary)
    }
}
