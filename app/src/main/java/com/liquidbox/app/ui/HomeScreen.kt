package com.liquidbox.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liquidbox.app.glass.AppStyle
import com.liquidbox.app.glass.LiquidButton
import com.liquidbox.app.glass.LocalAppStyle
import com.liquidbox.app.glass.LocalGlassPalette
import com.liquidbox.app.shizuku.ShizukuShell
import com.liquidbox.app.shizuku.Toolbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(backdrop: com.kyant.backdrop.Backdrop, onGoTools: () -> Unit, onGoAi: () -> Unit) {
    val palette = LocalGlassPalette.current
    val liquid = LocalAppStyle.current == AppStyle.LIQUID
    val scope = rememberCoroutineScope()
    val state by ShizukuShell.state.collectAsState()
    val uid by ShizukuShell.uid.collectAsState()

    var result by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12f.dp),
    ) {
        GlassCard {
            T("LiquidBox", size = 26, weight = androidx.compose.ui.text.font.FontWeight.Bold)
            T(
                if (liquid) "基于 Shizuku 的全液态玻璃工具箱"
                else "基于 Shizuku 的 Android 工具箱",
                size = 13, color = palette.textSecondary,
            )
            if (liquid) {
                T("UI 由 Kyant0 Backdrop 架构驱动：vibrancy → blur → lens",
                    size = 11, color = palette.accent)
            }
        }

        GlassCard {
            T("连接状态", size = 16, weight = androidx.compose.ui.text.font.FontWeight.Bold)
            KeyValueRow("Shizuku", when (state) {
                is ShizukuShell.State.Ready -> "已就绪"
                is ShizukuShell.State.NeedPermission -> "待授权"
                is ShizukuShell.State.BinderWaiting -> "未连接"
            })
            KeyValueRow("执行身份 UID", if (uid >= 0) uid.toString() else "—")
            if (liquid) {
                KeyValueRow("液态玻璃", if (com.liquidbox.app.glass.GlassCapability.supportsLens)
                    "完整（含折射）" else if (com.liquidbox.app.glass.GlassCapability.supportsRenderEffect)
                    "精简（模糊）" else "基础（降级）")
            } else {
                KeyValueRow("界面风格", LocalAppStyle.current.label)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10f.dp)) {
                LiquidButton(onClick = { ShizukuShell.refresh() }, modifier = Modifier.weight(1f)) {
                    T("刷新", size = 13, align = TextAlign.Center)
                }
                LiquidButton(onClick = onGoTools, modifier = Modifier.weight(1f)) {
                    T("打开工具箱", size = 13, align = TextAlign.Center)
                }
            }
        }

        GlassCard {
            T("快速动作", size = 16, weight = androidx.compose.ui.text.font.FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                listOf("screenshot" to "截图", "screen_off" to "息屏", "recents" to "最近任务").forEach { (id, label) ->
                    LiquidButton(
                        onClick = {
                            val a = Toolbox.byId(id) ?: return@LiquidButton
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { Toolbox.run(a, "", "") }
                                result = r.output
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { T(label, size = 12, align = TextAlign.Center) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                LiquidButton(
                    onClick = {
                        val a = Toolbox.byId("restore_bars") ?: return@LiquidButton
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { Toolbox.run(a, "", "") }
                            result = r.output
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { T("恢复系统栏", size = 12, align = TextAlign.Center) }
                LiquidButton(onClick = onGoAi, modifier = Modifier.weight(1f)) {
                    T("AI 操作", size = 12, align = TextAlign.Center)
                }
            }
        }

        // 与「系统工具」共用同一个动画速度控件
        AnimSpeedCard(backdrop = backdrop, onResult = { result = it })

        if (result.isNotBlank()) {
            GlassCard {
                T("最近结果", size = 13, weight = androidx.compose.ui.text.font.FontWeight.Bold)
                T(result.take(500), size = 12, color = palette.textSecondary)
            }
        }
        Spacer(Modifier.padding(60f.dp))
    }
}
