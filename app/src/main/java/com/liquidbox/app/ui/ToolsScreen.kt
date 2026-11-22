package com.liquidbox.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquidbox.app.glass.LiquidButton
import com.liquidbox.app.glass.LocalGlassBackdrop
import com.liquidbox.app.glass.LocalGlassPalette
import com.liquidbox.app.glass.liquidGlass
import com.liquidbox.app.shizuku.ShizukuShell
import com.liquidbox.app.shizuku.Toolbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ToolsScreen() {
    val palette = LocalGlassPalette.current
    val backdrop = LocalGlassBackdrop.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shizukuState by ShizukuShell.state.collectAsState()
    val uid by ShizukuShell.uid.collectAsState()

    var section by remember { mutableStateOf(Toolbox.Section.SOFTWARE) }
    var apps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    var appsLoading by remember { mutableStateOf(true) }
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }
    var appQuery by remember { mutableStateOf("") }
    var includeSystem by remember { mutableStateOf(false) }

    // 附加参数不是包名：包名一律来自上方选中的应用
    var extra by remember { mutableStateOf("") }
    var scriptText by remember { mutableStateOf("") }
    var scriptResult by remember { mutableStateOf("") }
    var pendingScript by remember { mutableStateOf<String?>(null) }

    var pendingAction by remember { mutableStateOf<Toolbox.Action?>(null) }
    var pendingValue by remember { mutableStateOf("") }
    var lastResult by remember { mutableStateOf("") }
    var lastAction by remember { mutableStateOf<Toolbox.Action?>(null) }
    var lastValue by remember { mutableStateOf("") }
    var localVersion by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.IO) { loadInstalledApps(context) }
        apps = list
        appsLoading = false
    }
    // 自动填入：默认选中第一个第三方应用，进入工具页即可直接使用软件工具
    LaunchedEffect(apps) {
        if (selectedApp == null) selectedApp = apps.firstOrNull { !it.isSystem }
    }

    val pkg = selectedApp?.packageName.orEmpty()

    val runAction: (Toolbox.Action, String) -> Unit = { action, value ->
        if (action.needsPackage && pkg.isBlank()) {
            lastResult = "请先在「目标应用」里选择软件，包名会自动填入"
        } else if (action.destructive || action.rollback != null) {
            pendingAction = action
            pendingValue = value
        } else {
            scope.launch {
                val r = withContext(Dispatchers.IO) { Toolbox.run(action, pkg, value) }
                lastAction = action
                lastValue = value
                lastResult = "[${if (r.ok) "成功" else "失败"}] ${action.title} → ${r.output.take(800)}"
            }
        }
    }

    val executeScript: (String) -> Unit = { script ->
        scope.launch {
            val r = withContext(Dispatchers.IO) { ShizukuShell.exec(script, 60L) }
            scriptResult = "[退出码 ${r.code}] ${r.output.take(2000)}"
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10f.dp),
            contentPadding = PaddingValues(bottom = 130f.dp),
        ) {
            // 本地工具分区不需要 Shizuku，也不选应用，隐藏这些卡片
            if (section != Toolbox.Section.LOCAL) {
                item {
                    GlassCard {
                        T("Shizuku 连接", size = 16, weight = FontWeight.Bold)
                        val statusText = when (shizukuState) {
                            is ShizukuShell.State.Ready -> "已就绪 · UID ${if (uid >= 0) uid.toString() else "—"}"
                            is ShizukuShell.State.NeedPermission -> "等待授权"
                            is ShizukuShell.State.BinderWaiting -> "等待 Shizuku 服务（请先启动 Shizuku / Sui）"
                        }
                        T(statusText, size = 12, color = palette.textSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(10f.dp)) {
                            LiquidButton(onClick = { ShizukuShell.refresh() }, modifier = Modifier.weight(1f)) {
                                T("刷新状态", size = 13, align = TextAlign.Center)
                            }
                            if (shizukuState is ShizukuShell.State.NeedPermission) {
                                LiquidButton(
                                    onClick = { ShizukuShell.requestPermission() },
                                    modifier = Modifier.weight(1f),
                                ) { T("请求授权", size = 13, align = TextAlign.Center) }
                            }
                        }
                    }
                }

                item {
                    TargetAppCard(
                        app = selectedApp,
                        appsCount = apps.size,
                        loading = appsLoading,
                        onPick = { showAppPicker = true },
                        onRefresh = {
                            AppIconStore.clear()
                            scope.launch {
                                apps = withContext(Dispatchers.IO) { loadInstalledApps(context) }
                                appsLoading = false
                            }
                        },
                    )
                }

                item {
                    GlassCard {
                        T("附加参数（不是包名）", size = 14, weight = FontWeight.Bold)
                        T("包名由上方选中的软件自动填入。仅权限名 / 组件名 / APK 路径 / 数值等需要手填。",
                            size = 11, color = palette.textSecondary)
                        GlassTextField(extra, { extra = it }, "可选：权限名、组件、APK 路径、数值…")
                    }
                }
            }

            // 四个分区用 2×2 网格，标签写全名，不再挤成「点」
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Toolbox.Section.entries.chunked(2).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10f.dp),
                        ) {
                            row.forEach { s ->
                                LiquidButton(
                                    onClick = { section = s },
                                    modifier = Modifier.weight(1f),
                                    tint = if (section == s) palette.accent.copy(alpha = 0.35f) else null,
                                ) {
                                    T(s.label, size = 14, weight = FontWeight.Bold, align = TextAlign.Center)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            when (section) {
                Toolbox.Section.SOFTWARE -> {
                    item {
                        SectionTitle(
                            "软件工具 · 作用于 ${selectedApp?.label ?: "（未选择软件）"} · " +
                                "共 ${Toolbox.softwareActions.size} 项"
                        )
                    }
                    items(Toolbox.softwareActions, key = { it.id }) { action ->
                        ActionCard(
                            action = action,
                            pkg = pkg,
                            appLabel = selectedApp?.label,
                            onExecute = { runAction(action, extra) },
                        )
                    }
                }
                Toolbox.Section.SYSTEM -> {
                    item { SectionTitle("系统工具 · 快捷控制（电量 / 速度 / 脚本）") }
                    item { BatteryCard(onResult = { lastResult = it }) }
                    item { AnimSpeedCard(backdrop = backdrop, onResult = { lastResult = it }) }
                    item {
                        ScriptCard(
                            script = scriptText,
                            onScriptChange = { scriptText = it },
                            result = scriptResult,
                            onRun = {
                                if (scriptText.isBlank()) scriptResult = "脚本内容为空"
                                else pendingScript = scriptText
                            },
                        )
                    }
                    item { SectionTitle("系统工具 · 全部动作 · 共 ${Toolbox.systemActions.size} 项") }
                    items(Toolbox.systemActions, key = { it.id }) { action ->
                        ActionCard(
                            action = action,
                            pkg = pkg,
                            appLabel = null,
                            onExecute = { runAction(action, extra) },
                        )
                    }
                }
                Toolbox.Section.DAILY -> {
                    item { SectionTitle("日常工具 · 一键切换与随手维护 · 不需要选应用") }
                    item { DailyQuickTogglesCard(onResult = { lastResult = it }) }
                    item { DailyDisplayCard(backdrop = backdrop, onResult = { lastResult = it }) }
                    item { DailyMediaCard(onResult = { lastResult = it }) }
                    item { DailyDeviceInfoCard(onResult = { lastResult = it }) }
                    item { DailyMaintenanceCard(onResult = { lastResult = it }) }
                    item { DailyRecordCard(onResult = { lastResult = it }) }
                }
                Toolbox.Section.LOCAL -> {
                    item { SectionTitle("本地工具 · 免 Shizuku · 二维码 / ZIP / 文件 / 文本处理") }
                    item {
                        LocalWorkdirCard(
                            onResult = { lastResult = it },
                            onChanged = { localVersion++ },
                        )
                    }
                    item {
                        LocalQrCard(
                            onResult = { lastResult = it },
                            onChanged = { localVersion++ },
                        )
                    }
                    item {
                        LocalZipCard(
                            onResult = { lastResult = it },
                            onChanged = { localVersion++ },
                        )
                    }
                    item {
                        LocalFilesCard(
                            onResult = { lastResult = it },
                            version = localVersion,
                            onChanged = { localVersion++ },
                        )
                    }
                    item { LocalTextCard() }
                }
            }
        }

        // 结果 / 回滚浮层
        if (lastResult.isNotBlank()) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 92f.dp)
                    .fillMaxWidth()
                    .liquidGlass()
                    .padding(16f.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    T("执行结果", size = 14, weight = FontWeight.Bold)
                    T(lastResult.take(900), size = 12, color = palette.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10f.dp)) {
                        if (lastAction?.rollback != null) {
                            LiquidButton(onClick = {
                                val a = lastAction ?: return@LiquidButton
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) {
                                        Toolbox.rollback(a, pkg, lastValue)
                                    }
                                    lastResult = "回滚: ${r.output}"
                                }
                            }, modifier = Modifier.weight(1f)) {
                                T("回滚", size = 13, align = TextAlign.Center)
                            }
                        }
                        LiquidButton(onClick = { lastResult = "" }, modifier = Modifier.weight(1f)) {
                            T("关闭", size = 13, align = TextAlign.Center)
                        }
                    }
                }
            }
        }

        // 危险动作二次确认（页面内浮层，保持 backdrop 的 CompositionLocal）
        pendingAction?.let { action ->
            Box(Modifier.align(Alignment.Center).fillMaxWidth()) {
                ConfirmGlassCard(
                    title = "确认执行「${action.title}」？",
                    detail = buildString {
                        append(action.description)
                        append("\n\n将执行：\n")
                        append(action.command(pkg, pendingValue).take(400))
                        if (action.needsPackage) append("\n\n目标应用：${selectedApp?.label}（$pkg）")
                    },
                    confirmLabel = "确认执行",
                    onRollbackHint = if (action.rollback != null) "执行后可在结果栏点击「回滚」" else null,
                    onConfirm = {
                        pendingAction = null
                        scope.launch {
                            val r = withContext(Dispatchers.IO) {
                                Toolbox.run(action, pkg, pendingValue)
                            }
                            lastAction = action
                            lastValue = pendingValue
                            lastResult = "[${if (r.ok) "成功" else "失败"}] ${action.title} → ${r.output.take(800)}"
                        }
                    },
                    onDismiss = { pendingAction = null },
                )
            }
        }

        // 自定义脚本二次确认
        pendingScript?.let { script ->
            Box(Modifier.align(Alignment.Center).fillMaxWidth()) {
                ConfirmGlassCard(
                    title = "确认执行自定义脚本？",
                    detail = "将以 Shizuku 身份执行：\n\n${script.take(600)}",
                    confirmLabel = "执行脚本",
                    onConfirm = {
                        pendingScript = null
                        executeScript(script)
                    },
                    onDismiss = { pendingScript = null },
                )
            }
        }

        // 应用选择浮层（最上层）
        if (showAppPicker) {
            Box(
                Modifier.align(Alignment.Center).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AppPickerOverlay(
                    apps = apps,
                    query = appQuery,
                    includeSystem = includeSystem,
                    onQueryChange = { appQuery = it },
                    onIncludeSystemChange = { includeSystem = it },
                    onSelect = {
                        selectedApp = it
                        showAppPicker = false
                    },
                    onDismiss = { showAppPicker = false },
                )
            }
        }
    }
}

/** 单个工具卡片：写清工具名称、所属分类、说明、参数与目标应用 */
@Composable
private fun ActionCard(
    action: Toolbox.Action,
    pkg: String,
    appLabel: String?,
    onExecute: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(
                    action.title,
                    size = 15,
                    weight = FontWeight.Bold,
                    color = if (action.destructive) palette.danger else palette.textPrimary,
                )
                T("${action.group.label} · ${action.description}",
                    size = 11, color = palette.textSecondary)
                if (action.needsPackage) {
                    T("目标：${appLabel ?: "未选择"}（$pkg）",
                        size = 11, color = palette.accent, maxLines = 1)
                }
                if (action.valueKey != null) {
                    T("参数：${action.valueKey}", size = 11, color = palette.accent)
                }
                if (action.rollback != null) {
                    T("支持回滚", size = 10, color = palette.success)
                }
            }
            Spacer(Modifier.padding(6f.dp))
            LiquidButton(onClick = onExecute) {
                T("执行", size = 13, align = TextAlign.Center)
            }
        }
    }
}

@Composable
fun GlassTextField(value: String, onValueChange: (String) -> Unit, hint: String) {
    val palette = LocalGlassPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .liquidGlass(blurDp = 4f, lensHeightDp = 8f, lensAmountDp = 10f)
            .padding(horizontal = 14f.dp, vertical = 12f.dp),
    ) {
        if (value.isEmpty()) T(hint, size = 12, color = palette.textSecondary)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = palette.textPrimary, fontSize = 13.sp),
            cursorBrush = SolidColor(palette.accent),
            singleLine = true,
        )
    }
}
