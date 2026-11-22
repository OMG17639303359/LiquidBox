package com.liquidbox.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 启动动画：完全由 Compose Canvas 绘制，不引用任何 backdrop / drawBackdrop。
 * 这样不会参与背景层录制，避免此前「录制层内部放玻璃组件」导致的自引用递归绘制闪退。
 *
 * 视觉：深空冷色渐变 + 漂移光斑 + 网格 + 扫光；中心是旋转的液态玻璃球
 * （色散描边、内部流动高光、液滴剪影），外圈进度环与绕行粒子；
 * 标题字距收拢淡入，底部进度条与版本号。首帧即显示完整图形，淡出时机由
 * AppStartup 在确认主界面首帧已绘制后统一控制，避免中途露出窗口底色。
 */
private val SplashBg = listOf(
    Color(0xFF04060D), Color(0xFF0A1428), Color(0xFF101C3A), Color(0xFF04070F),
)
// 全程冷色：冰蓝 / 青 / 一点暖金作点缀，去掉紫色，避免整屏泛紫。
private val AccentBlue = Color(0xFF7FA8FF)
private val AccentIce = Color(0xFF9CC2FF)
private val AccentTeal = Color(0xFF5FDCCB)
private val AccentGold = Color(0xFFFFC98A)
private val ParticleColors = listOf(AccentBlue, AccentIce, AccentTeal, AccentGold)

private const val DURATION = 2200

private fun seg(p: Float, a: Float, b: Float): Float = ((p - a) / (b - a)).coerceIn(0f, 1f)

private fun easeOut(x: Float): Float {
    val t = 1f - x
    return 1f - t * t * t
}

/** 轻微回弹，让液滴出现时带弹性 */
private fun backOut(x: Float): Float {
    val c = 1.70158f
    val t = x - 1f
    return 1f + (c + 1f) * t * t * t + c * t * t
}

@Composable
fun LiquidSplash(
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    fadeOut: Boolean = false,
    onFinished: () -> Unit,
    onFadeOutEnd: () -> Unit = {},
) {
    val progress = remember { Animatable(0f) }
    // 淡出完全交给启动编排：只有主界面首帧确实绘制完成才会置 fadeOut = true，
    // 开屏不再在动画末尾自己淡到透明、把窗口底色露出来。
    val outAlpha = remember { Animatable(1f) }
    val consumed = remember { mutableStateOf(false) }
    val fadeConsumed = remember { mutableStateOf(false) }

    fun finish() {
        if (!consumed.value) {
            consumed.value = true
            onFinished()
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(DURATION, easing = LinearEasing))
        finish()
    }

    // 关键：LOADING 阶段不再跑任何持续动画。
    // 动画播完后主线程要立刻组合整个主界面（shader 首次编译 + 整页 layerBackdrop 录制 +
    // 壁纸解码），这段是 unavoidable 的重活；如果此时开屏还在转圈/呼吸进度条，
    // 主线程一被占住动画就顿住，用户看到的就是「开屏播完后卡一下」。
    // 改为定格在动画终帧：主界面组合再重，静止的开屏画面也不会有可见的卡顿，
    // 首帧画完后直接淡出，衔接顺滑。

    LaunchedEffect(fadeOut) {
        if (fadeOut && !fadeConsumed.value) {
            fadeConsumed.value = true
            outAlpha.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
            onFadeOutEnd()
        }
    }

    // 点「跳过」后 loading 立刻为 true，画面直接定格在动画终帧（而不是继续播完）；
    // 动画协程仍在后台跑完也无所谓，这里不再读它。
    val p = if (loading) 1f else progress.value
    // 定格终帧：光斑、外环、进度条都停在动画完成那一刻，不再随 spinLoop 转动。
    val spinOffset = 0f
    val revealScale = 1f + 0.025f * (1f - outAlpha.value)

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = outAlpha.value
                scaleX = revealScale
                scaleY = revealScale
            }
            .clickable(onClick = { finish() }),
    ) {
        // 底层：渐变 + 漂移光斑 + 网格 + 扫光
        Canvas(Modifier.fillMaxSize()) { drawSplashBackground(p, spinOffset) }

        // 中层：液滴玻璃球 + 旋转外环 + 进度环 + 绕行粒子
        Canvas(
            Modifier
                .size(320f.dp)
                .align(Alignment.Center)
                .offset(y = (-26f).dp),
        ) { drawSplashOrb(p, spinOffset) }

        // 文案：字距收拢 + 淡入上移
        Column(
            Modifier
                .align(Alignment.Center)
                .offset(y = 150f.dp)
                .graphicsLayer {
                    // 首帧就有内容：即使某一帧被加载卡在 p = 0，也不会是一整屏空渐变。
                    val a = 0.45f + 0.55f * seg(p, 0.18f, 0.44f)
                    alpha = a
                    translationY = (1f - easeOut(a)) * 28f
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = "LiquidBox",
                style = TextStyle(
                    color = Color(0xFFF2F5FF),
                    fontSize = 34f.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (12f - 9f * easeOut(seg(p, 0.18f, 0.50f))).sp,
                ),
            )
            Spacer(Modifier.height(10f.dp))
            BasicText(
                text = "SHIZUKU · LIQUID GLASS · ON-DEVICE AI",
                modifier = Modifier.graphicsLayer { alpha = seg(p, 0.34f, 0.58f) },
                style = TextStyle(
                    color = Color(0xFFA7B0CC),
                    fontSize = 11f.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.2f.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }

        // 底部：进度条 + 版本 + 跳过提示
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 52f.dp)
                .graphicsLayer { alpha = 0.35f + 0.65f * seg(p, 0.45f, 0.68f) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.size(180f.dp, 3f.dp)) {
                val w = size.width
                val mid = size.height / 2f
                drawLine(
                    color = Color.White.copy(alpha = 0.10f),
                    start = Offset(0f, mid),
                    end = Offset(w, mid),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                )
                // loading 时 p 已定格为 1，进度条停在满格「准备就绪」，不再左右呼吸。
                val head = (w * easeOut(seg(p, 0.05f, 0.92f))).coerceAtLeast(1f)
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(AccentBlue, AccentTeal, AccentIce),
                        start = Offset(0f, 0f),
                        end = Offset(w, 0f),
                    ),
                    start = Offset(0f, mid),
                    end = Offset(head, mid),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                )
                drawCircle(Color.White, radius = size.height * 1.3f, center = Offset(head, mid))
            }
            Spacer(Modifier.height(16f.dp))
            BasicText(
                text = "1.0beta",
                style = TextStyle(
                    color = Color(0xFF8B95B8),
                    fontSize = 12f.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 3f.sp,
                ),
            )
            Spacer(Modifier.height(8f.dp))
            BasicText(
                text = if (loading) "正在准备界面…" else "轻触跳过",
                style = TextStyle(
                    color = Color(0xFF616C90),
                    fontSize = 11f.sp,
                    letterSpacing = 1.5f.sp,
                ),
            )
        }
    }
}

private fun DrawScope.drawSplashBackground(p: Float, spinOffset: Float) {
    drawRect(brush = Brush.linearGradient(colors = SplashBg))

    val t = p * 2f * PI.toFloat() + spinOffset * PI.toFloat() / 180f
    val orbs = listOf(
        Triple(AccentBlue, 0.22f, Offset(0.20f, 0.18f)),
        Triple(AccentIce, 0.15f, Offset(0.82f, 0.30f)),
        Triple(AccentTeal, 0.12f, Offset(0.60f, 0.74f)),
        Triple(AccentGold, 0.09f, Offset(0.12f, 0.86f)),
    )
    val brighten = 0.6f + 0.4f * seg(p, 0f, 0.2f)
    orbs.forEachIndexed { i, (color, alpha, base) ->
        val phase = t + i * 1.4f
        val cx = size.width * (base.x + 0.05f * sin(phase))
        val cy = size.height * (base.y + 0.05f * cos(phase * 0.8f))
        val r = size.minDimension * (0.50f + 0.07f * sin(phase * 0.6f))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha * brighten), color.copy(alpha = 0f)),
                center = Offset(cx, cy),
                radius = r,
            ),
            radius = r,
            center = Offset(cx, cy),
        )
    }

    val step = 52f * density
    val line = Color.White.copy(alpha = 0.022f)
    var x = 0f
    while (x < size.width) {
        drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }

    val sweepY = size.height * (0.15f + 0.7f * easeOut(seg(p, 0.1f, 0.9f)))
    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.05f),
                Color.Transparent,
            ),
            start = Offset(0f, sweepY),
            end = Offset(size.width, sweepY),
        ),
        start = Offset(0f, sweepY),
        end = Offset(size.width, sweepY),
        strokeWidth = 90f * density,
    )

    // 四周渐暗：压住边缘、让中心液滴成为唯一焦点，整体更沉稳
    drawRect(
        brush = Brush.radialGradient(
            0.40f to Color.Transparent,
            0.72f to Color(0xFF02030A).copy(alpha = 0.16f),
            1f to Color(0xFF02030A).copy(alpha = 0.62f),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = size.maxDimension * 0.80f,
        ),
    )
}

private fun DrawScope.drawSplashOrb(p: Float, spinOffset: Float) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val ringR = size.minDimension * 0.40f
    val baseR = size.minDimension * 0.26f
    val scale = 0.55f + 0.45f * backOut(seg(p, 0.05f, 0.40f))
    val r = baseR * scale
    // 首帧就能看到液滴与外环，避免「整屏只有渐变」的空屏观感
    val alpha = 0.55f + 0.45f * seg(p, 0f, 0.18f)

    // 外发光
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                AccentBlue.copy(alpha = 0.20f * alpha),
                AccentIce.copy(alpha = 0.10f * alpha),
                Color.Transparent,
            ),
            center = c,
            radius = ringR * 1.6f,
        ),
        radius = ringR * 1.6f,
        center = c,
    )

    val ringSize = Size(ringR * 2f, ringR * 2f)
    val ringTopLeft = Offset(c.x - ringR, c.y - ringR)
    val spin = p * 720f + spinOffset

    // 三段旋转弧
    repeat(3) { i ->
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(
                    AccentBlue.copy(alpha = 0f),
                    AccentBlue.copy(alpha = 0.55f * alpha),
                    AccentTeal.copy(alpha = 0.45f * alpha),
                    AccentBlue.copy(alpha = 0f),
                ),
                center = c,
            ),
            startAngle = spin + i * 120f,
            sweepAngle = 62f,
            useCenter = false,
            topLeft = ringTopLeft,
            size = ringSize,
            style = Stroke(width = 2.2f * density, cap = StrokeCap.Round),
        )
    }

    // 从顶部扫过的进度环
    drawArc(
        brush = Brush.sweepGradient(
            colors = listOf(AccentBlue, AccentIce, AccentGold, AccentTeal, AccentBlue),
            center = c,
        ),
        startAngle = -90f,
        sweepAngle = 360f * easeOut(seg(p, 0.05f, 0.92f)),
        useCenter = false,
        topLeft = ringTopLeft,
        size = ringSize,
        style = Stroke(width = 3.2f * density, cap = StrokeCap.Round),
    )

    // 玻璃球体（左上高光 -> 深蓝底）
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.30f * alpha),
                AccentBlue.copy(alpha = 0.24f * alpha),
                Color(0xFF101733).copy(alpha = 0.86f * alpha),
            ),
            center = Offset(c.x - r * 0.35f, c.y - r * 0.40f),
            radius = r * 1.35f,
        ),
        radius = r,
        center = c,
    )

    // 色散边缘：三层偏移描边模拟折射
    listOf(AccentIce, AccentTeal, AccentBlue).forEachIndexed { i, col ->
        drawCircle(
            color = col.copy(alpha = 0.34f * alpha),
            radius = r + (i + 1) * 1.6f * density,
            center = c,
            style = Stroke(width = 1.4f * density),
        )
    }
    drawCircle(
        color = Color.White.copy(alpha = 0.55f * alpha),
        radius = r,
        center = c,
        style = Stroke(width = 1.5f * density),
    )

    // 球内流动的弧形高光
    drawArc(
        brush = Brush.sweepGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.55f * alpha),
                Color.Transparent,
            ),
            center = c,
        ),
        startAngle = -spin * 0.6f,
        sweepAngle = 110f,
        useCenter = false,
        topLeft = Offset(c.x - r * 0.72f, c.y - r * 0.72f),
        size = Size(r * 1.44f, r * 1.44f),
        style = Stroke(width = r * 0.30f, cap = StrokeCap.Round),
    )

    // 中心液滴剪影
    val s = r * 0.42f
    val drop = Path().apply {
        moveTo(c.x, c.y - s * 1.05f)
        cubicTo(c.x + s * 0.30f, c.y - s * 0.46f, c.x + s * 0.74f, c.y - s * 0.06f, c.x + s * 0.74f, c.y + s * 0.28f)
        cubicTo(c.x + s * 0.74f, c.y + s * 0.74f, c.x + s * 0.38f, c.y + s * 0.96f, c.x, c.y + s * 0.96f)
        cubicTo(c.x - s * 0.38f, c.y + s * 0.96f, c.x - s * 0.74f, c.y + s * 0.74f, c.x - s * 0.74f, c.y + s * 0.28f)
        cubicTo(c.x - s * 0.74f, c.y - s * 0.06f, c.x - s * 0.30f, c.y - s * 0.46f, c.x, c.y - s * 1.05f)
        close()
    }
    drawPath(
        drop,
        brush = Brush.linearGradient(
            colors = listOf(Color.White, Color(0xFFDCE7FF), AccentBlue.copy(alpha = 0.85f)),
            start = Offset(c.x - s, c.y - s),
            end = Offset(c.x + s, c.y + s),
        ),
        alpha = alpha,
    )
    drawPath(
        drop,
        color = Color.White.copy(alpha = 0.85f * alpha),
        style = Stroke(width = 1.4f * density),
    )

    // 绕行粒子
    repeat(8) { i ->
        val a = p * 2f * PI.toFloat() * 1.15f + i * (2f * PI.toFloat() / 8f)
        val pr = ringR * (1.05f + 0.06f * sin(p * 6f * PI.toFloat() + i))
        val pc = Offset(c.x + pr * cos(a), c.y + pr * sin(a) * 0.92f)
        val col = ParticleColors[i % 4]
        drawCircle(col.copy(alpha = 0.18f * alpha), radius = 7f * density, center = pc)
        drawCircle(col.copy(alpha = 0.85f * alpha), radius = 2.6f * density, center = pc)
    }
}
