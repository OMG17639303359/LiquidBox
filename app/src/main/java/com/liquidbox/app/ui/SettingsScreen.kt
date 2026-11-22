package com.liquidbox.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liquidbox.app.data.SecureStore
import com.liquidbox.app.glass.AppStyle
import com.liquidbox.app.glass.GlassCapability
import com.liquidbox.app.glass.GlassLevel
import com.liquidbox.app.glass.LiquidButton
import com.liquidbox.app.glass.LiquidToggle
import com.liquidbox.app.glass.LocalGlassPalette
import com.liquidbox.app.shizuku.ShizukuShell
import com.liquidbox.app.util.CrashHandler

@Composable
fun SettingsScreen(
    currentLevel: GlassLevel,
    onLevelChange: (GlassLevel) -> Unit,
    wallpaper: WallpaperStyle,
    onWallpaperChange: (WallpaperStyle) -> Unit,
    onCustomImageChanged: () -> Unit,
    onViewCrashLog: () -> Unit,
    appStyle: AppStyle,
    onStyleChange: (AppStyle) -> Unit,
) {
    val palette = LocalGlassPalette.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val liquid = appStyle == AppStyle.LIQUID
    // 「低端机降级」不再单独存状态，直接由当前档位派生：选基础档即视为开启，
    // 选其它档位自动关闭。避免开关与档位互相覆盖、关掉开关又被拉回基础。
    val lowEnd = currentLevel == GlassLevel.BASIC
    var splashOn by remember {
        mutableStateOf(SecureStore.getBoolean("show_splash", true))
    }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && WallpaperStore.saveCustom(context, uri)) {
            onWallpaperChange(WallpaperStyle.CUSTOM)
            onCustomImageChanged()
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12f.dp)) {
        GlassCard {
            T("风格设置", size = 17, weight = androidx.compose.ui.text.font.FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                AppStyle.entries.forEach { style ->
                    LiquidButton(
                        onClick = { onStyleChange(style) },
                        modifier = Modifier.weight(1f),
                        tint = if (appStyle == style) palette.accent.copy(alpha = 0.4f) else null,
                    ) { T(style.label, size = 12, align = TextAlign.Center) }
                }
            }
            T("切换后立即生效。", size = 11, color = palette.textSecondary)
        }

        // 把「现在到底有没有实时玻璃」直接写在最前面，不再静默降级
        if (liquid) {
            val blurOk = GlassCapability.supportsRenderEffect
            val lensOk = GlassCapability.supportsLens
            GlassCard {
                T("实时玻璃状态", size = 17,
                    weight = androidx.compose.ui.text.font.FontWeight.Bold)
                T(
                    when {
                        !blurOk -> "本机不支持 RenderEffect，无法做实时背面模糊"
                        !lensOk -> "实时背面模糊：已开启 · 实时折射：本机系统不支持"
                        currentLevel == GlassLevel.FULL -> "实时背面模糊 + 实时折射：已开启"
                        currentLevel == GlassLevel.REDUCED -> "实时背面模糊：已开启 · 折射已关闭"
                        else -> "实时背面模糊：已开启（省电）· 折射已关闭"
                    },
                    size = 13,
                    color = if (blurOk && lensOk) palette.success else palette.accentWarm,
                )
                T("玻璃每一帧都重新采样它背后的画面，不是预先做好的贴图。",
                    size = 11, color = palette.textSecondary)
                if (!lensOk) {
                    T("本机低于 Android 13，缺少运行时折射（RuntimeShader）；" +
                        "实时背面模糊仍可用，换 Android 13+ 设备可看到边缘折射。",
                        size = 11, color = palette.danger)
                }
            }
        }

        // 只有液态玻璃风格用壁纸；纯色风格不显示背景图，也不显示折射相关说明
        if (liquid) {
            GlassCard {
                T("自定义背景", size = 17, weight = androidx.compose.ui.text.font.FontWeight.Bold)
                T("当前：${wallpaper.label}", size = 13, color = palette.accent)
                T("切换背景后，玻璃内的模糊与高光会一起变化。",
                    size = 11, color = palette.textSecondary)
                WallpaperStyle.entries.filter { it != WallpaperStyle.CUSTOM }
                    .chunked(2).forEach { rowStyles ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                            rowStyles.forEach { style ->
                                LiquidButton(
                                    onClick = { onWallpaperChange(style) },
                                    modifier = Modifier.weight(1f),
                                    tint = if (wallpaper == style) palette.accent.copy(alpha = 0.4f) else null,
                                ) { T(style.label, size = 12, align = TextAlign.Center) }
                            }
                        }
                    }
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    LiquidButton(
                        onClick = {
                            pickMedia.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        tint = if (wallpaper == WallpaperStyle.CUSTOM) palette.accent.copy(alpha = 0.4f) else null,
                    ) { T("从相册选图", size = 12, align = TextAlign.Center) }
                    LiquidButton(
                        onClick = { onWallpaperChange(WallpaperStyle.PHOTO) },
                        modifier = Modifier.weight(1f),
                    ) { T("恢复默认", size = 12, align = TextAlign.Center) }
                }
                if (wallpaper == WallpaperStyle.PHOTO) {
                    T("当前使用内置背景图", size = 11, color = palette.success)
                }
                if (wallpaper == WallpaperStyle.CUSTOM && WallpaperStore.hasCustom(context)) {
                    T("已使用自定义图片（保存在应用私有目录，重启后仍生效）",
                        size = 11, color = palette.success)
                }
            }
        }

        GlassCard {
            T("外观与性能", size = 17, weight = androidx.compose.ui.text.font.FontWeight.Bold)

            if (liquid) {
                T("液态玻璃质量档位", size = 13, color = palette.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6f.dp)) {
                    GlassLevel.entries.forEach { level ->
                        LiquidButton(
                            onClick = { onLevelChange(level) },
                            modifier = Modifier.weight(1f),
                            tint = if (currentLevel == level) palette.accent.copy(alpha = 0.4f) else null,
                        ) {
                            T(
                                when (level) {
                                    GlassLevel.FULL -> "完整"
                                    GlassLevel.REDUCED -> "精简"
                                    GlassLevel.BASIC -> "基础"
                                },
                                size = 12, align = TextAlign.Center,
                            )
                        }
                    }
                }
                T(
                    "完整：实时模糊 + 实时折射；精简：实时模糊；基础：小半径实时模糊。\n" +
                        "三档都逐帧采样背面，只是叠加效果不同，不会再变成静态玻璃。",
                    size = 11, color = palette.textSecondary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T("低端机降级", size = 14,
                            weight = androidx.compose.ui.text.font.FontWeight.Bold)
                        T("强制省电档 · 仍保留实时背面模糊", size = 11, color = palette.textSecondary)
                    }
                    LiquidToggle(
                        checked = lowEnd,
                        // 关闭时固定回到完整档，不再用设备能力判定（autoLevel）
                        // 把用户刚选的档位重新压回降级档。
                        onCheckedChange = {
                            onLevelChange(if (it) GlassLevel.BASIC else GlassLevel.FULL)
                        },
                    )
                }
            } else {
                T("当前为纯色界面，不使用玻璃效果与背景图。",
                    size = 12, color = palette.textSecondary)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("启动开屏动画", size = 14,
                        weight = androidx.compose.ui.text.font.FontWeight.Bold)
                    T("关闭后启动直接显示主界面", size = 11, color = palette.textSecondary)
                }
                LiquidToggle(
                    checked = splashOn,
                    onCheckedChange = {
                        splashOn = it
                        SecureStore.putBoolean("show_splash", it)
                    },
                )
            }
        }

        GlassCard {
            T("运行时能力", size = 16, weight = androidx.compose.ui.text.font.FontWeight.Bold)
            if (liquid) {
                KeyValueRow("RenderEffect (Android 12+)",
                    if (GlassCapability.supportsRenderEffect) "支持" else "不支持")
                KeyValueRow("Lens 折射 (Android 13+)",
                    if (GlassCapability.supportsLens) "支持" else "不支持")
                KeyValueRow("当前档位", currentLevel.name)
                KeyValueRow("建议档位", GlassCapability.autoLevel().name)
            } else {
                KeyValueRow("界面", "纯色 / 无玻璃")
            }
            KeyValueRow("Shizuku", if (ShizukuShell.isReady) "已就绪" else "未就绪")
        }

        GlassCard {
            T("诊断与崩溃日志", size = 16, weight = androidx.compose.ui.text.font.FontWeight.Bold)
            val log = CrashHandler.lastCrash(context)
            KeyValueRow("累计崩溃次数", CrashHandler.crashCount(context).toString())
            T(
                if (log.isBlank()) "暂无崩溃日志" else log.take(400),
                size = 10, color = palette.textSecondary, maxLines = 6,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6f.dp)) {
                LiquidButton(
                    onClick = onViewCrashLog,
                    modifier = Modifier.weight(1f),
                ) { T("查看日志", size = 11, align = TextAlign.Center) }
                LiquidButton(
                    onClick = { clipboard.setText(AnnotatedString(log)) },
                    modifier = Modifier.weight(1f),
                ) { T("复制日志", size = 11, align = TextAlign.Center) }
                LiquidButton(
                    onClick = { CrashHandler.clear(context) },
                    modifier = Modifier.weight(1f),
                ) { T("清除", size = 11, align = TextAlign.Center) }
            }
            T("若再次闪退，重新打开 App 后进本页「查看日志」复制堆栈发我。",
                size = 11, color = palette.textSecondary)
        }

        GlassCard {
            T("关于", size = 16, weight = androidx.compose.ui.text.font.FontWeight.Bold)
            T("LiquidBox 1.0beta", size = 13)
            T("液态玻璃组件：Kyant0 / AndroidLiquidGlass catalog 源码（原样引入，未改动）\n" +
                "底层：Backdrop 2.0.1 · shapes 1.2.1\n" +
                "特权能力：Shizuku API 13.1.5\n" +
                "AI 操作：OpenAI-compatible / Anthropic / Gemini\n" +
                "API Key 由 Android Keystore 加密，聊天记录仅存本地。",
                size = 11, color = palette.textSecondary)
        }

        val openUrl: (String) -> Unit = { url ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
        GlassCard {
            T("官网与开源", size = 16, weight = androidx.compose.ui.text.font.FontWeight.Bold)
            T(
                "官网：omg17639303359.github.io/LiquidBox",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openUrl("https://omg17639303359.github.io/LiquidBox/") },
                size = 12, color = palette.accent,
            )
            T(
                "GitHub 开源：github.com/OMG17639303359/LiquidBox",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openUrl("https://github.com/OMG17639303359/LiquidBox") },
                size = 12, color = palette.accent,
            )
            T("别忘了点个Star！！！", size = 12,
                color = palette.textSecondary, align = TextAlign.Center)
        }
    }
}

