package com.liquidbox.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.liquidbox.app.glass.LiquidButton
import com.liquidbox.app.glass.LiquidSlider
import com.liquidbox.app.glass.LocalGlassPalette
import com.liquidbox.app.glass.liquidGlass
import com.liquidbox.app.shizuku.Toolbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * 动画速度缩放：主页与「系统工具」共用同一个组件。
 * 主页的速度控件保留，系统工具里也是这一份，避免两处行为不一致。
 */
@Composable
fun AnimSpeedCard(
    backdrop: Backdrop,
    onResult: (String) -> Unit = {},
) {
    val palette = LocalGlassPalette.current
    val scope = rememberCoroutineScope()
    var scale by remember { mutableStateOf(1f) }

    GlassCard {
        T("动画速度缩放", size = 15, weight = FontWeight.Bold)
        T("当前 ${"%.1f".format(scale)}x · 0 = 关闭动画，0.5 = 加速，2 = 慢放",
            size = 11, color = palette.textSecondary)
        LiquidSlider(
            value = { scale },
            onValueChange = { scale = it },
            valueRange = 0f..2f,
            backdrop = backdrop,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6f.dp)) {
            listOf(0f, 0.5f, 1f, 2f).forEach { v ->
                LiquidButton(
                    onClick = { scale = v },
                    modifier = Modifier.weight(1f),
                    tint = if (abs(scale - v) < 0.05f) palette.accent.copy(alpha = 0.35f) else null,
                ) { T("${"%.1f".format(v)}x", size = 11, align = TextAlign.Center) }
            }
        }
        LiquidButton(
            onClick = {
                val action = Toolbox.byId("anim_scale") ?: return@LiquidButton
                scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        Toolbox.run(action, "", "%.1f".format(scale))
                    }
                    onResult("[${if (r.ok) "成功" else "失败"}] 动画缩放 → ${r.output.take(300)}")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { T("应用动画缩放", size = 13, align = TextAlign.Center) }
    }
}

/** 电量修改：伪装系统显示的电量百分比，可一键恢复真实电量 */
@Composable
fun BatteryCard(onResult: (String) -> Unit = {}) {
    val palette = LocalGlassPalette.current
    val scope = rememberCoroutineScope()
    var level by remember { mutableStateOf("80") }

    fun runBattery(actionId: String, value: String = "") {
        val action = Toolbox.byId(actionId) ?: return
        scope.launch {
            val r = withContext(Dispatchers.IO) { Toolbox.run(action, "", value) }
            onResult("[${if (r.ok) "成功" else "失败"}] ${action.title} → ${r.output.take(500)}")
        }
    }

    GlassCard {
        T("电量修改", size = 15, weight = FontWeight.Bold)
        T("修改系统显示的电量百分比（只改显示，重启或点「恢复」后还原）",
            size = 11, color = palette.textSecondary)
        GlassTextField(
            value = level,
            onValueChange = { input -> level = input.filter { it.isDigit() }.take(3) },
            hint = "电量百分比 0-100",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6f.dp)) {
            listOf("20", "50", "80", "100").forEach { v ->
                LiquidButton(
                    onClick = { level = v },
                    modifier = Modifier.weight(1f),
                    tint = if (level == v) palette.accent.copy(alpha = 0.35f) else null,
                ) { T("$v%", size = 11, align = TextAlign.Center) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
            LiquidButton(
                onClick = { runBattery("battery_level", level) },
                modifier = Modifier.weight(1f),
            ) { T("设置电量", size = 12, align = TextAlign.Center) }
            LiquidButton(
                onClick = { runBattery("battery_reset") },
                modifier = Modifier.weight(1f),
            ) { T("恢复真实电量", size = 12, align = TextAlign.Center) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
            LiquidButton(
                onClick = { runBattery("battery_info") },
                modifier = Modifier.weight(1f),
            ) { T("查看电池状态", size = 12, align = TextAlign.Center) }
            LiquidButton(
                onClick = { runBattery("battery_charging", "2") },
                modifier = Modifier.weight(1f),
            ) { T("模拟充电中", size = 12, align = TextAlign.Center) }
        }
    }
}

/** 自定义脚本：多行 shell 编辑 + 模板填入；执行前由页面统一二次确认 */
@Composable
fun ScriptCard(
    script: String,
    onScriptChange: (String) -> Unit,
    result: String,
    onRun: () -> Unit,
) {
    val palette = LocalGlassPalette.current

    GlassCard {
        T("自定义脚本", size = 15, weight = FontWeight.Bold)
        T("多行 shell 脚本，以 Shizuku 身份执行；执行前会二次确认内容",
            size = 11, color = palette.textSecondary)
        Box(
            Modifier
                .fillMaxWidth()
                .height(150f.dp)
                .liquidGlass(blurDp = 4f, lensHeightDp = 8f, lensAmountDp = 10f)
                .padding(12f.dp),
        ) {
            if (script.isEmpty()) {
                T("例如：\ndumpsys battery\nsettings get system peak_refresh_rate",
                    size = 11, color = palette.textSecondary)
            }
            BasicTextField(
                value = script,
                onValueChange = onScriptChange,
                modifier = Modifier.fillMaxSize(),
                textStyle = TextStyle(color = palette.textPrimary, fontSize = 12.sp),
                cursorBrush = SolidColor(palette.accent),
            )
        }
        T("脚本模板（点击填入）", size = 11, color = palette.textSecondary)
        Column(verticalArrangement = Arrangement.spacedBy(6f.dp)) {
            Toolbox.scriptTemplates.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6f.dp)) {
                    row.forEach { template ->
                        LiquidButton(
                            onClick = { onScriptChange(template.command) },
                            modifier = Modifier.weight(1f),
                        ) { T(template.label, size = 11, align = TextAlign.Center, maxLines = 1) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        LiquidButton(onClick = onRun, modifier = Modifier.fillMaxWidth()) {
            T("执行脚本", size = 13, align = TextAlign.Center)
        }
        if (result.isNotBlank()) {
            T("脚本输出", size = 12, weight = FontWeight.Bold)
            T(result.take(1500), size = 11, color = palette.textSecondary)
        }
    }
}
