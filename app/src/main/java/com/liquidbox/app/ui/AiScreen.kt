package com.liquidbox.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liquidbox.app.ai.AgentEngine
import com.liquidbox.app.ai.ImageUtils
import com.liquidbox.app.data.AiConfig
import com.liquidbox.app.data.LogEntry
import com.liquidbox.app.data.LogStore
import com.liquidbox.app.data.ToolPolicies
import com.liquidbox.app.data.ToolPolicy
import com.liquidbox.app.glass.LiquidButton
import com.liquidbox.app.glass.LocalGlassBackdrop
import com.liquidbox.app.glass.LocalGlassPalette
import com.liquidbox.app.glass.LocalOverlayBackdrop
import com.liquidbox.app.glass.liquidGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AiScreen() {
    val palette = LocalGlassPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val agentState by AgentEngine.state.collectAsState()

    var config by remember { mutableStateOf(AiConfig.load()) }
    var instruction by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf(LogStore.all()) }
    var policyVersion by remember { mutableStateOf(0) }
    var attachedUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris -> attachedUris = (attachedUris + uris).distinct().take(4) }

    LaunchedEffect(agentState.finished, agentState.status) { logs = LogStore.all() }
    BackHandler(enabled = showSettings) { showSettings = false }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T(if (showSettings) "AI 设置" else "AI 操作手机", size = 17,
                            weight = androidx.compose.ui.text.font.FontWeight.Bold)
                        T("${config.provider.label} · ${config.model}", size = 12, color = palette.textSecondary)
                    }
                    LiquidButton(onClick = { showSettings = !showSettings }) {
                        T(if (showSettings) "返回" else "设置", size = 13, align = TextAlign.Center)
                    }
                }
                T(agentState.status.ifBlank { "填写指令后，AI 将通过工具链驱动手机" },
                    size = 12, color = if (agentState.pending != null) palette.danger else palette.textSecondary)
                if (agentState.running) {
                    T("运行中…（危险动作需确认）", size = 12, color = palette.accent)
                }
            }

            Spacer(Modifier.height(10f.dp))

            if (showSettings) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10f.dp),
                ) {
                    AiSettingsCard(config) { config = it }
                    ToolPolicyCard(policyVersion) { policyVersion++ }
                    Spacer(Modifier.height(110f.dp))
                }
            } else {
                GlassCard {
                    T("自然语言指令", size = 15, weight = androidx.compose.ui.text.font.FontWeight.Bold)
                    GlassTextField(instruction, { instruction = it }, "例如：打开设置，找到电池并告诉我电量")

                    // 图片附件：缩略图 + 添加 / 清空
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8f.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        attachedUris.forEach { uri ->
                            val thumb = remember(uri) { ImageUtils.thumbnail(context, uri) }
                            Box(contentAlignment = Alignment.TopEnd) {
                                if (thumb != null) {
                                    Image(
                                        bitmap = thumb,
                                        contentDescription = "已附加图片",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(54f.dp)
                                            .clip(RoundedCornerShape(10f.dp))
                                            .border(1f.dp, palette.accent.copy(alpha = 0.5f), RoundedCornerShape(10f.dp)),
                                    )
                                } else {
                                    Box(
                                        Modifier
                                            .size(54f.dp)
                                            .clip(RoundedCornerShape(10f.dp))
                                            .border(1f.dp, palette.textSecondary, RoundedCornerShape(10f.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) { T("图", size = 12, color = palette.textSecondary) }
                                }
                                // 点右上角移除
                                Box(
                                    Modifier
                                        .size(18f.dp)
                                        .clip(RoundedCornerShape(9f.dp))
                                        .clickable { attachedUris = attachedUris - uri },
                                    contentAlignment = Alignment.Center,
                                ) { T("×", size = 13, color = palette.danger, align = TextAlign.Center) }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10f.dp)) {
                        LiquidButton(
                            onClick = { pickImages.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            enabled = !agentState.running && attachedUris.size < 4,
                        ) { T("添加图片${if (attachedUris.isEmpty()) "" else " (${attachedUris.size}/4)"}",
                            size = 12, align = TextAlign.Center) }
                        if (attachedUris.isNotEmpty()) {
                            LiquidButton(
                                onClick = { attachedUris = emptyList() },
                                modifier = Modifier.weight(1f),
                            ) { T("清空图片", size = 12, align = TextAlign.Center) }
                        }
                    }
                    if (attachedUris.isNotEmpty()) {
                        T(
                            if (config.provider.supportsVision)
                                "图片会随指令一起发给模型（自动压缩到 1024px）"
                            else
                                "注意：${config.provider.label} 当前模型可能不支持看图，若报错请换用视觉模型",
                            size = 10,
                            color = if (config.provider.supportsVision) palette.textSecondary else palette.danger,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10f.dp)) {
                        LiquidButton(
                            onClick = {
                                if ((instruction.isBlank() && attachedUris.isEmpty()) || agentState.running) return@LiquidButton
                                AiConfig.save(config)
                                val text = instruction.ifBlank { "请识别并描述这些图片" }
                                val uris = attachedUris
                                instruction = ""
                                attachedUris = emptyList()
                                logs = LogStore.all()
                                scope.launch {
                                    val images = withContext(Dispatchers.IO) {
                                        uris.mapNotNull { ImageUtils.encode(context, it) }
                                    }
                                    AgentEngine.run(text, config, images)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !agentState.running,
                        ) { T("发送指令", size = 14, align = TextAlign.Center) }
                        LiquidButton(
                            onClick = { AgentEngine.cancel() },
                            modifier = Modifier.weight(1f),
                            enabled = agentState.running,
                        ) { T("停止", size = 14, align = TextAlign.Center) }
                    }
                }

                Spacer(Modifier.height(10f.dp))

                GlassCard(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("执行日志", Modifier.weight(1f), size = 14,
                            weight = androidx.compose.ui.text.font.FontWeight.Bold)
                        LiquidButton(onClick = { LogStore.clear(); logs = emptyList() }) {
                            T("清空", size = 12, align = TextAlign.Center)
                        }
                    }
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6f.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110f.dp),
                    ) {
                        items(logs, key = { "${it.time}_${it.role}_${it.hashCode()}" }) { e ->
                            LogRow(e)
                        }
                    }
                }
            }
        }

        // Ask 模式：液态玻璃确认卡片（危险动作执行前）
        agentState.pending?.let { pending ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 96f.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .liquidGlass(
                            // 浮层盖在整页之上：采样当前这一帧的界面，实时背面模糊/折射
                            backdrop = LocalOverlayBackdrop.current ?: LocalGlassBackdrop.current,
                            surfaceColor = palette.containerTint,
                            blurDp = 18f,
                            lensHeightDp = 22f,
                            lensAmountDp = 26f,
                        )
                        .padding(18f.dp),
                    verticalArrangement = Arrangement.spacedBy(10f.dp),
                ) {
                    T("确认工具调用", size = 16, weight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = palette.danger)
                    T(pending.description, size = 13, color = palette.textPrimary)
                    T("工具：${pending.tool}", size = 11, color = palette.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10f.dp)) {
                        LiquidButton(onClick = { AgentEngine.deny() }, modifier = Modifier.weight(1f)) {
                            T("拒绝", size = 13, align = TextAlign.Center)
                        }
                        LiquidButton(onClick = { AgentEngine.approve() }, modifier = Modifier.weight(1f),
                            tint = palette.danger.copy(alpha = 0.4f)) {
                            T("允许执行", size = 13, weight = androidx.compose.ui.text.font.FontWeight.Bold,
                                align = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(e: LogEntry) {
    val palette = LocalGlassPalette.current
    val color = when (e.role) {
        "user" -> palette.accent
        "assistant" -> palette.success
        "error" -> palette.danger
        "tool_call" -> palette.accentWarm
        else -> palette.textSecondary
    }
    val time = remember(e.time) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(e.time))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlass(blurDp = 3f, lensHeightDp = 6f, lensAmountDp = 8f)
            .padding(horizontal = 12f.dp, vertical = 8f.dp),
    ) {
        T("$time  ${e.role}", size = 11, color = color,
            weight = androidx.compose.ui.text.font.FontWeight.Bold)
        T(e.text.take(1200), size = 12, color = palette.textPrimary)
    }
}

@Composable
private fun AiSettingsCard(config: AiConfig, onChange: (AiConfig) -> Unit) {
    val palette = LocalGlassPalette.current
    GlassCard {
        T("模型配置", size = 16, weight = androidx.compose.ui.text.font.FontWeight.Bold)
        T("Provider（点击切换）", size = 12, color = palette.textSecondary)
        AiConfig.Provider.entries.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8f.dp),
            ) {
                row.forEach { p ->
                    LiquidButton(
                        onClick = {
                            onChange(config.copy(provider = p, baseUrl = p.defaultBase, model = p.defaultModel))
                        },
                        modifier = Modifier.weight(1f),
                        tint = if (config.provider == p) palette.accent.copy(alpha = 0.35f) else null,
                    ) { T(p.label.split(" ").first(), size = 12, align = TextAlign.Center, maxLines = 1) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        T("DeepSeek 用自家 API 地址与 Key；也可用 OpenAI-compatible 指向任意中转/本地模型。",
            size = 10, color = palette.textSecondary)
        GlassTextField(config.baseUrl, { onChange(config.copy(baseUrl = it)) }, "Base URL（可自定义，兼容 OpenRouter / Ollama）")
        GlassTextField(config.model, { onChange(config.copy(model = it)) }, "模型名称")
        GlassTextField(config.apiKey, { onChange(config.copy(apiKey = it)) }, "API Key（Android Keystore 加密存储）")
        GlassTextField(config.extraHeaders, { onChange(config.copy(extraHeaders = it)) }, "额外 headers，每行一条：Key: Value")
        T("保存后生效。所有请求直连你配置的 Base URL，无后端、无遥测。",
            size = 11, color = palette.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(10f.dp)) {
            LiquidButton(onClick = { AiConfig.save(config) }, modifier = Modifier.weight(1f)) {
                T("保存配置", size = 13, align = TextAlign.Center)
            }
            LiquidButton(onClick = { AiConfig.clearKey(); onChange(config.copy(apiKey = "")) },
                modifier = Modifier.weight(1f)) {
                T("清除 Key", size = 13, align = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ToolPolicyCard(version: Int, onChanged: () -> Unit) {
    val palette = LocalGlassPalette.current
    GlassCard {
        T("工具权限（Off / Ask / Auto）", size = 16, weight = androidx.compose.ui.text.font.FontWeight.Bold)
        T("危险工具默认 Ask，执行前弹出液态玻璃确认卡片。", size = 12, color = palette.textSecondary)
        ToolPolicies.allTools().forEach { tool ->
            val current = remember(version, tool) { ToolPolicies.get(tool) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(tool, Modifier.weight(1f), size = 12)
                ToolPolicy.entries.forEach { policy ->
                    LiquidButton(
                        onClick = { ToolPolicies.set(tool, policy); onChanged() },
                        tint = if (current == policy) palette.accent.copy(alpha = 0.4f) else null,
                    ) { T(policy.label, size = 11, align = TextAlign.Center) }
                }
            }
        }
    }
}
