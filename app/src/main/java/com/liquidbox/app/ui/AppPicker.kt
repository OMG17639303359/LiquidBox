package com.liquidbox.app.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import com.liquidbox.app.glass.LiquidButton
import com.liquidbox.app.glass.LocalGlassPalette
import com.liquidbox.app.glass.liquidGlass

/** 本地 PackageManager 读取的已安装应用，不需要 Shizuku 权限即可列出 */
data class InstalledApp(
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
)

fun loadInstalledApps(context: Context): List<InstalledApp> = runCatching {
    val pm = context.packageManager
    pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
        .map { ai ->
            val label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(ai.packageName)
            InstalledApp(
                label = label,
                packageName = ai.packageName,
                isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            )
        }
        .sortedBy { it.label.lowercase() }
}.getOrDefault(emptyList())

/** 目标应用卡片：显示当前选中的软件与自动填入的包名 */
@Composable
fun TargetAppCard(
    app: InstalledApp?,
    appsCount: Int,
    loading: Boolean,
    onPick: () -> Unit,
    onRefresh: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    GlassCard {
        T("目标应用 · 自动填入包名", size = 16, weight = FontWeight.Bold)
        if (app != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12f.dp),
            ) {
                AppIcon(packageName = app.packageName, label = app.label, size = 46f.dp)
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3f.dp),
                ) {
                    T(app.label, size = 17, color = palette.accent, maxLines = 1)
                    T(app.packageName, size = 11, color = palette.textSecondary, maxLines = 1)
                }
            }
        } else {
            T(
                if (loading) "正在读取已安装软件列表…" else "点下方按钮选择软件",
                size = 12, color = palette.textSecondary,
            )
        }
        T("已检测到 $appsCount 个应用，选中后所有软件工具会自动使用它的包名",
            size = 11, color = palette.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
            LiquidButton(onClick = onPick, modifier = Modifier.weight(1f)) {
                T("选择软件", size = 13, align = TextAlign.Center)
            }
            LiquidButton(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                T("刷新列表", size = 13, align = TextAlign.Center)
            }
        }
    }
}

/**
 * 应用选择浮层：页面内玻璃卡片（不用 Dialog，避免丢失 backdrop 的 CompositionLocal）。
 * 支持按名称/包名搜索、仅第三方与全部应用切换。
 */
@Composable
fun AppPickerOverlay(
    apps: List<InstalledApp>,
    query: String,
    includeSystem: Boolean,
    onQueryChange: (String) -> Unit,
    onIncludeSystemChange: (Boolean) -> Unit,
    onSelect: (InstalledApp) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    val filtered = remember(apps, query, includeSystem) {
        val q = query.trim().lowercase()
        apps.filter { app ->
            (includeSystem || !app.isSystem) &&
                (q.isEmpty() || app.label.lowercase().contains(q) ||
                    app.packageName.lowercase().contains(q))
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(14f.dp)
            .liquidGlass(
                shape = { RoundedRectangle(26f.dp) },
                surfaceColor = palette.containerTint,
                blurDp = 16f,
                lensHeightDp = 22f,
                lensAmountDp = 26f,
            )
            .padding(16f.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10f.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("选择目标软件", size = 18, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
                LiquidButton(onClick = onDismiss) {
                    T("关闭", size = 12, align = TextAlign.Center)
                }
            }
            T("点选后自动填入包名，无需手动输入", size = 11, color = palette.textSecondary)
            GlassTextField(query, onQueryChange, "搜索应用名或包名")
            Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                LiquidButton(
                    onClick = { onIncludeSystemChange(false) },
                    modifier = Modifier.weight(1f),
                    tint = if (!includeSystem) palette.accent.copy(alpha = 0.35f) else null,
                ) { T("仅第三方", size = 12, align = TextAlign.Center) }
                LiquidButton(
                    onClick = { onIncludeSystemChange(true) },
                    modifier = Modifier.weight(1f),
                    tint = if (includeSystem) palette.accent.copy(alpha = 0.35f) else null,
                ) { T("全部应用", size = 12, align = TextAlign.Center) }
            }
            T("共 ${filtered.size} 个应用", size = 11, color = palette.textSecondary)

            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .height(340f.dp),
                verticalArrangement = Arrangement.spacedBy(2f.dp),
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(app) }
                            .padding(horizontal = 8f.dp, vertical = 9f.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10f.dp),
                    ) {
                        AppIcon(packageName = app.packageName, label = app.label, size = 36f.dp)
                        Column(Modifier.weight(1f)) {
                            T(app.label, size = 14, maxLines = 1)
                            T(app.packageName, size = 11, color = palette.textSecondary, maxLines = 1)
                        }
                        if (app.isSystem) T("系统", size = 10, color = palette.accent)
                    }
                }
            }
        }
    }
}
