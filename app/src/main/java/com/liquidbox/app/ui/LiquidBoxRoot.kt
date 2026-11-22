package com.liquidbox.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.RoundedRectangle
import com.liquidbox.app.data.SecureStore
import com.liquidbox.app.glass.AppStyle
import com.liquidbox.app.glass.GlassLevel
import com.liquidbox.app.glass.LiquidBottomTabs
import com.liquidbox.app.glass.LocalAppStyle
import com.liquidbox.app.glass.LocalGlassBackdrop
import com.liquidbox.app.glass.LocalGlassLevel
import com.liquidbox.app.glass.LocalGlassPalette
import com.liquidbox.app.glass.LocalOverlayBackdrop
import com.liquidbox.app.glass.TabItem
import com.liquidbox.app.glass.flatColors
import com.liquidbox.app.glass.flatPalette
import com.liquidbox.app.glass.liquidGlass
import com.liquidbox.app.glass.rememberGlassPalette
import com.liquidbox.app.shizuku.ShizukuShell
import com.liquidbox.app.util.CrashHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val tabs = listOf("首页" to "◆", "工具" to "⚙", "终端" to "❯", "AI" to "✦", "设置" to "◎")

private const val KEY_STYLE = "app_style"
private const val KEY_GLASS_LEVEL = "glass_level"

private fun readStyle(): AppStyle {
    val saved = SecureStore.getPlain(KEY_STYLE, "")
    if (saved.isNotBlank()) {
        return runCatching { AppStyle.valueOf(saved) }.getOrDefault(AppStyle.LIQUID)
    }
    // 兼容旧版「最简风格」布尔开关
    return if (SecureStore.getBoolean("simple_mode", false)) AppStyle.M3 else AppStyle.LIQUID
}

/**
 * 采样层分三份：
 *  - wallpaperBackdrop：只录壁纸，供页面上的玻璃卡片采样，避免自引用递归绘制；
 *  - contentBackdrop  ：录整页内容（卡片、按钮、文字），只有底栏会采样它；
 *  - barBackdrop      ：两者组合，交给官方 LiquidBottomTabs 的液滴放大折射，
 *                       所以液滴里能同时看到壁纸和它后面的按钮。
 * 页面内容与底栏互不采样对方，不会产生 layerBackdrop 自引用。
 * 官方 catalog 源码一字未改，只在 com.liquidbox.app.glass 下做参数转发。
 */
@Composable
fun LiquidBoxRoot() {
    val context = LocalContext.current
    var showCrashLog by remember { mutableStateOf(false) }

    var appStyle by remember { mutableStateOf(readStyle()) }
    val liquid = appStyle == AppStyle.LIQUID
    val palette = if (liquid) rememberGlassPalette() else flatPalette(appStyle)
    var level by remember { mutableStateOf(resolveInitialLevel()) }
    var tab by remember { mutableIntStateOf(0) }

    var wallpaper by remember { mutableStateOf(WallpaperStore.current()) }
    // 自定义壁纸解码放到 IO：主界面首帧不再在主线程 BitmapFactory.decodeFile
    var customBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var customVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { ShizukuShell.init() }
    LaunchedEffect(wallpaper, customVersion, liquid) {
        // 非液态玻璃风格不显示壁纸，直接跳过解码，避免无谓的 IO 与内存占用
        customBitmap = if (!liquid) null else when (wallpaper) {
            WallpaperStyle.CUSTOM -> withContext(Dispatchers.IO) {
                WallpaperStore.loadCustom(context)
            }
            WallpaperStyle.PHOTO -> withContext(Dispatchers.IO) {
                WallpaperStore.loadBuiltIn(context)
            }
            else -> null
        }
    }

    CompositionLocalProvider(
        LocalGlassPalette provides palette,
        LocalGlassLevel provides level,
        LocalAppStyle provides appStyle,
    ) {
        Box(Modifier.fillMaxSize()) {
            val wallpaperBackdrop = rememberLayerBackdrop()
            val contentBackdrop = rememberLayerBackdrop()
            val barBackdrop = rememberCombinedBackdrop(wallpaperBackdrop, contentBackdrop)
            // 所有档位都逐帧录制整页内容：玻璃/底栏/浮层采样的都是当前这一帧，
            // 而不是预先准备好的一张静态壁纸。
            val captureContent = liquid

            // ===== 背景层（只有壁纸，供页面玻璃卡片采样）=====
            if (!liquid) {
                Box(Modifier.fillMaxSize().background(flatColors(appStyle).background))
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .layerBackdrop(wallpaperBackdrop),
                ) {
                    WallpaperBackdrop(style = wallpaper, image = customBitmap)
                }
            }

            CompositionLocalProvider(
                LocalGlassBackdrop provides wallpaperBackdrop,
                // 浮层用它采样「壁纸 + 整页实时内容」，弹出时模糊/折射的是当前界面
                LocalOverlayBackdrop provides barBackdrop,
            ) {
                // ===== 页面内容层 =====
                // 整页录制进 contentBackdrop；页面自身的玻璃卡片仍只采样壁纸层，
                // 因此不会自引用。底栏采样「壁纸 + 本层」，液滴就能折射到按钮。
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (captureContent) Modifier.layerBackdrop(contentBackdrop)
                            else Modifier
                        ),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            // 只避状态栏：底部不再叠 systemBarsPadding，
                            // 页面内容一直铺到屏幕底部、从底栏下面连续滚上来。
                            .statusBarsPadding()
                            .then(
                                if (tab == 1 || tab == 2 || tab == 3) Modifier
                                else Modifier.verticalScroll(rememberScrollState())
                            )
                            .padding(horizontal = 16f.dp)
                            .padding(
                                top = 26f.dp,
                                // 只留底栏本身的高度：最后一张卡片能滚出来，
                                // 中途的内容则穿过底栏区域，被玻璃折射。
                                bottom = if (appStyle == AppStyle.M3) 96f.dp else 78f.dp,
                            ),
                    ) {
                        when (tab) {
                            0 -> HomeScreen(
                                wallpaperBackdrop,
                                onGoTools = { tab = 1 },
                                onGoAi = { tab = 3 },
                            )
                            1 -> ToolsScreen()
                            2 -> ShellScreen()
                            3 -> AiScreen()
                            4 -> SettingsScreen(
                                currentLevel = level,
                                onLevelChange = { newLevel ->
                                    level = newLevel
                                    // 档位与「低端机降级」是同一件事，统一持久化到一处。
                                    // 手动选过档位后清掉旧版强制降级标记，重启不再被拉回基础。
                                    SecureStore.putPlain(KEY_GLASS_LEVEL, newLevel.name)
                                    SecureStore.putBoolean("force_basic_glass", false)
                                },
                                wallpaper = wallpaper,
                                onWallpaperChange = { style ->
                                    wallpaper = style
                                    WallpaperStore.set(style)
                                },
                                onCustomImageChanged = { customVersion++ },
                                onViewCrashLog = { showCrashLog = true },
                                appStyle = appStyle,
                                onStyleChange = {
                                    appStyle = it
                                    SecureStore.putPlain(KEY_STYLE, it.name)
                                },
                            )
                        }
                    }
                }

                // ===== 底栏 =====
                // 不再加任何深色淡出遮罩：底栏后面的页面内容要能被看见、被折射。
                // 液态玻璃：官方胶囊 + 液滴 + 色散折射 + 高光（悬浮）
                // MIUIX：悬浮圆角胶囊，不透明纯色栏 + 四周边框 + 滑动指示块
                // M3：贴底连体全宽导航条，背景延伸到屏幕最底
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .then(
                            if (appStyle == AppStyle.M3) Modifier.fillMaxWidth()
                            else Modifier
                                .systemBarsPadding()
                                .padding(horizontal = 12f.dp)
                                .padding(bottom = 10f.dp)
                        ),
                ) {
                    val selectedColor =
                        if (liquid) Color.White else flatColors(appStyle).accent
                    LiquidBottomTabs(
                        selectedTabIndex = { tab },
                        onTabSelected = { tab = it },
                        // 关键：液滴采样「壁纸 + 页面内容」的组合，
                        // 被底栏盖住的按钮也会出现在折射里。
                        backdrop = barBackdrop,
                        tabsCount = tabs.size,
                    ) {
                        tabs.forEachIndexed { index, (label, glyph) ->
                            TabItem(onClick = { tab = index }) {
                                T(
                                    glyph,
                                    size = 18,
                                    color = if (tab == index) selectedColor else palette.textSecondary,
                                    align = TextAlign.Center,
                                )
                                T(
                                    label,
                                    size = 10,
                                    color = if (tab == index) selectedColor else palette.textSecondary,
                                    align = TextAlign.Center,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                if (showCrashLog) {
                    CrashLogOverlay(
                        text = CrashHandler.lastCrash(context),
                        onClose = { showCrashLog = false },
                        onClear = {
                            CrashHandler.clear(context)
                            showCrashLog = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * 恢复用户上次手动选择的档位。默认始终是「完整」：实时背面模糊与折射都先开着，
 * 由库在不支持的设备上自行跳过 lens，并在设置页如实显示设备能力，
 * 不再偷偷把整台手机降到没有模糊的静态档位。
 */
private fun resolveInitialLevel(): GlassLevel {
    if (SecureStore.getBoolean("force_basic_glass", false)) return GlassLevel.BASIC
    return runCatching {
        GlassLevel.valueOf(SecureStore.getPlain(KEY_GLASS_LEVEL, ""))
    }.getOrDefault(GlassLevel.FULL)
}

@Composable
private fun CrashLogOverlay(
    text: String,
    onClose: () -> Unit,
    onClear: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val palette = LocalGlassPalette.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20f.dp)
                .heightIn(max = 520f.dp)
                .liquidGlass(
                    backdrop = LocalOverlayBackdrop.current ?: LocalGlassBackdrop.current,
                    shape = { RoundedRectangle(26f.dp) },
                    surfaceColor = palette.containerTint,
                    blurDp = 14f,
                )
                .padding(18f.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10f.dp),
        ) {
            T("上次崩溃堆栈", size = 17,
                weight = androidx.compose.ui.text.font.FontWeight.Bold, color = palette.danger)
            T("把下面内容复制发给我即可精确定位问题。", size = 12, color = palette.textSecondary)
            T(text.ifBlank { "（暂无日志）" }.take(8000), size = 10, color = palette.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                com.liquidbox.app.glass.LiquidButton(
                    onClick = { clipboard.setText(AnnotatedString(text)) },
                    modifier = Modifier.weight(1f),
                ) { T("复制日志", size = 12, align = TextAlign.Center) }
                com.liquidbox.app.glass.LiquidButton(
                    onClick = onClear, modifier = Modifier.weight(1f),
                ) { T("清除", size = 12, align = TextAlign.Center) }
                com.liquidbox.app.glass.LiquidButton(
                    onClick = onClose, modifier = Modifier.weight(1f),
                ) { T("关闭", size = 12, align = TextAlign.Center) }
            }
        }
    }
}
