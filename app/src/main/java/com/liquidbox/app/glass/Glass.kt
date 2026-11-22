package com.liquidbox.app.glass

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 液态玻璃质量档位。三档都会逐帧采样「背面」并做实时背面模糊，
 * 区别只在额外叠加多少效果，不存在「预先做好的静态玻璃」：
 * FULL    : vibrancy -> blur -> lens（实时背面模糊 + 实时折射）
 * REDUCED : vibrancy -> blur（实时背面模糊，不做折射）
 * BASIC   : blur（较小半径的实时背面模糊，最省电）
 */
enum class GlassLevel { FULL, REDUCED, BASIC }

/**
 * 运行时能力探测：只用于界面如实告知用户，绝不偷偷关掉效果。
 * 实时模糊在所有档位都保留；只有 lens 折射依赖 Android 13+ 的 RuntimeShader。
 */
object GlassCapability {
    val supportsRenderEffect: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val supportsLens: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** 给设置页展示的建议档位，不参与默认档位决策 */
    fun autoLevel(): GlassLevel = when {
        !supportsRenderEffect -> GlassLevel.BASIC
        !supportsLens -> GlassLevel.REDUCED
        else -> GlassLevel.FULL
    }
}

val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop> {
    error("LocalGlassBackdrop 未提供，请把页面包在 CompositionLocalProvider 中")
}
/**
 * 浮层（工具确认卡、崩溃日志等）的采样源：壁纸 + 整页实时内容。
 * 浮层盖在页面之上，用它才能对「当前这一帧的界面」做实时背面模糊和折射。
 */
val LocalOverlayBackdrop = compositionLocalOf<Backdrop?> { null }
val LocalGlassLevel = compositionLocalOf { GlassLevel.FULL }
val LocalGlassPalette = compositionLocalOf { darkPalette() }

/**
 * 界面风格。
 * LIQUID —— 完整液态玻璃（壁纸 + Backdrop vibrancy→blur→lens + 官方 catalog 组件）
 * MIUIX  —— 小米 HyperOS 风格（浅色纯色、无玻璃、有边框）
 * M3     —— Material 3 风格（深色纯色、无玻璃、有边框）
 *
 * 后两种完全不调用官方 catalog、不调用 drawBackdrop / layerBackdrop，
 * 因此没有 RuntimeShader 编译、没有 RenderEffect、没有 backdrop 采样。
 */
enum class AppStyle(val label: String) {
    LIQUID("液态玻璃"),
    MIUIX("MIUIX"),
    M3("M3"),
}

val LocalAppStyle = staticCompositionLocalOf { AppStyle.LIQUID }

/** 非液态玻璃风格的纯色规格：全部不透明，没有渐变、光斑、阴影等装饰 */
data class FlatColors(
    val background: Color,
    val card: Color,
    val overlay: Color,
    val button: Color,
    val border: Color,
    val accent: Color,
    val cardRadius: Float,
    val tabRadius: Float,
)

fun flatColors(style: AppStyle): FlatColors = when (style) {
    // 小米 HyperOS：浅灰底 + 纯白卡片 + 小米蓝
    AppStyle.MIUIX -> FlatColors(
        background = Color(0xFFF4F6F9),
        card = Color(0xFFFFFFFF),
        overlay = Color(0xFFEAEFF7),
        button = Color(0xFFE7ECF5),
        border = Color(0xFFD9DFEA),
        accent = Color(0xFF2B7BFF),
        cardRadius = 20f,
        tabRadius = 20f,
    )
    // Material 3 深色：tonal surface + 蓝紫主色 + 中等圆角
    else -> FlatColors(
        background = Color(0xFF0E1118),
        card = Color(0xFF1B2130),
        overlay = Color(0xFF242C3E),
        button = Color(0xFF2C3549),
        border = Color(0xFF39435A),
        accent = Color(0xFF8AB4FF),
        cardRadius = 16f,
        tabRadius = 16f,
    )
}

/** 非液态玻璃风格的文字配色，直接复用现有 GlassPalette，改动最小 */
fun flatPalette(style: AppStyle): GlassPalette = when (style) {
    AppStyle.MIUIX -> GlassPalette(
        isDark = false,
        textPrimary = Color(0xFF1B1D24),
        textSecondary = Color(0xFF6B7280),
        accent = Color(0xFF2B7BFF),
        accentWarm = Color(0xFFE8792B),
        danger = Color(0xFFD63A4C),
        success = Color(0xFF0E9E68),
        surfaceTint = Color(0xFFEAEFF7),
        containerTint = Color(0xFFFFFFFF),
        trackTint = Color(0xFFD9DFEA),
    )
    else -> GlassPalette(
        isDark = true,
        textPrimary = Color(0xFFE9EEFA),
        textSecondary = Color(0xFFA3ADC4),
        accent = Color(0xFF8AB4FF),
        accentWarm = Color(0xFFFFB27A),
        danger = Color(0xFFFF8A90),
        success = Color(0xFF6DD6A0),
        surfaceTint = Color(0xFF242C3E),
        containerTint = Color(0xFF1B2130),
        trackTint = Color(0xFF39435A),
    )
}

data class GlassPalette(
    val isDark: Boolean,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentWarm: Color,
    val danger: Color,
    val success: Color,
    val surfaceTint: Color,
    val containerTint: Color,
    val trackTint: Color,
)

fun darkPalette() = GlassPalette(
    isDark = true,
    textPrimary = Color(0xFFF2F5FF),
    textSecondary = Color(0xFFA7B0CC),
    accent = Color(0xFF6E9BFF),
    accentWarm = Color(0xFFFFB27A),
    danger = Color(0xFFFF7A85),
    success = Color(0xFF68D6A0),
    surfaceTint = Color(0xFF1A2342).copy(alpha = 0.34f),
    containerTint = Color(0xFF0C1128).copy(alpha = 0.46f),
    trackTint = Color(0xFF8892B0).copy(alpha = 0.30f),
)

fun lightPalette() = GlassPalette(
    isDark = false,
    textPrimary = Color(0xFF141A33),
    textSecondary = Color(0xFF5A6482),
    accent = Color(0xFF2F6BFF),
    accentWarm = Color(0xFFE87A2F),
    danger = Color(0xFFD63A4C),
    success = Color(0xFF1E9E68),
    surfaceTint = Color(0xFFFFFFFF).copy(alpha = 0.46f),
    containerTint = Color(0xFFF6F8FF).copy(alpha = 0.52f),
    trackTint = Color(0xFF787880).copy(alpha = 0.24f),
)

@Composable
fun rememberGlassPalette(): GlassPalette =
    if (isSystemInDarkTheme()) darkPalette() else lightPalette()

/** 按压弹簧动画状态：用标准 Compose 动画替代 catalog 内的 DampedDragAnimation */
@Stable
class GlassPressState internal constructor(
    private val scope: CoroutineScope,
    private val animatable: Animatable<Float, AnimationVector1D>,
) {
    /** 0f = 未按下，1f = 完全按下；在 draw lambda 中读取可自动触发重绘 */
    val progress: Float get() = animatable.value
    private val spec = spring<Float>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f)

    fun press() {
        scope.launch { animatable.animateTo(1f, spec) }
    }

    fun release() {
        scope.launch { animatable.animateTo(0f, spec) }
    }
}

@Composable
fun rememberGlassPressState(): GlassPressState {
    val scope = rememberCoroutineScope()
    return remember(scope) { GlassPressState(scope, Animatable(0f)) }
}

/**
 * 严格按 vibrancy() -> blur(...) -> lens(...) 顺序施加（与官方组件一致）。
 * 每一帧都重新采样 backdrop，所以模糊与折射都是实时的；
 * 三档都保留实时背面模糊，只有 FULL 且设备支持时才叠加实时折射。
 */
fun BackdropEffectScope.applyGlassEffects(
    level: GlassLevel,
    blurPx: Float,
    lensHeightPx: Float,
    lensAmountPx: Float,
) {
    val blurAmount = when (level) {
        GlassLevel.FULL -> blurPx
        GlassLevel.REDUCED -> blurPx * 0.75f
        GlassLevel.BASIC -> blurPx * 0.5f
    }
    if (level != GlassLevel.BASIC) vibrancy()
    blur(blurAmount)
    if (level == GlassLevel.FULL && GlassCapability.supportsLens) {
        lens(lensHeightPx, lensAmountPx)
    }
}

/**
 * 统一的液态玻璃表面。只做官方那一件事：逐帧采样背板 -> vibrancy -> blur -> lens
 * -> 官方默认高光/阴影。不再叠大面积半透明色块、浓阴影、色彩增强等「预先做好」的装饰。
 */
@Composable
fun Modifier.liquidGlass(
    shape: () -> Shape = { Capsule() },
    backdrop: Backdrop = LocalGlassBackdrop.current,
    level: GlassLevel = LocalGlassLevel.current,
    palette: GlassPalette = LocalGlassPalette.current,
    surfaceColor: Color? = null,
    blurDp: Float = 8f,
    lensHeightDp: Float = 20f,
    lensAmountDp: Float = 24f,
    press: GlassPressState? = null,
    highlight: (() -> Highlight?)? = null,
    shadow: (() -> Shadow?)? = null,
    innerShadow: (() -> InnerShadow?)? = null,
    extraLayerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    onDrawFront: (DrawScope.() -> Unit)? = null,
): Modifier {
    // 非液态玻璃风格：直接退化成不透明纯色圆角块 + 细边框，
    // 不采样 backdrop、不编译任何 shader。
    val style = LocalAppStyle.current
    if (style != AppStyle.LIQUID) {
        val sh = shape()
        val c = flatColors(style)
        val bg = if (surfaceColor != null && surfaceColor.isSpecified) c.overlay else c.card
        return this
            .background(bg, sh)
            .border(1f.dp, c.border, sh)
            // 液态玻璃的 drawBackdrop 自带形状裁剪，纯色分支没有；补上 clip，
            // 否则终端输出等长文本会画到圆角框外面。
            .clip(sh)
    }
    return this.drawBackdrop(
    backdrop = backdrop,
    shape = shape,
    effects = {
        applyGlassEffects(
            level = level,
            blurPx = blurDp.dp.toPx(),
            lensHeightPx = lensHeightDp.dp.toPx(),
            lensAmountPx = lensAmountDp.dp.toPx(),
        )
    },
    // 官方默认高光与阴影，不再自行叠加一层浓 shadow
    highlight = highlight ?: { Highlight.Default },
    shadow = shadow,
    innerShadow = innerShadow,
    layerBlock = {
        val p = press?.progress ?: 0f
        val scale = lerp(1f, 1f + 5f.dp.toPx() / size.height.coerceAtLeast(1f), p)
        scaleX = scale
        scaleY = scale
        extraLayerBlock?.invoke(this)
    },
    onDrawSurface = {
        onDrawSurface?.invoke(this)
        // 只留一层极淡的表面色保证文字可读；任何档位都不再是「一块铺好的色板」
        val c = surfaceColor ?: palette.surfaceTint
        if (c.isSpecified) drawRect(c.copy(alpha = c.alpha.coerceAtMost(0.16f)))
    },
    onDrawFront = onDrawFront,
    )
}
