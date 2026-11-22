package com.liquidbox.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import com.liquidbox.app.data.SecureStore
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 可切换的壁纸方案。PHOTO 为内置背景图，CUSTOM 使用用户从相册选择的图片。 */
enum class WallpaperStyle(val label: String) {
    PHOTO("海岸"),
    AURORA("极光紫"),
    SUNSET("日落橙"),
    OCEAN("深海蓝"),
    MONO("暗夜灰"),
    CUSTOM("自定义图片"),
}

object WallpaperStore {
    private const val KEY = "wallpaper_style"
    private const val FILE = "wallpaper_custom.png"
    private const val BUILT_IN_ASSET = "wallpaper_default.jpg"
    private const val KEY_BUILT_IN_APPLIED = "wallpaper_builtin_v1"

    @Volatile
    private var builtInCache: ImageBitmap? = null

    fun current(): WallpaperStyle {
        // 首次升级到带内置背景的版本时切换一次到新背景；之后用户的选择不再被覆盖。
        if (!SecureStore.getBoolean(KEY_BUILT_IN_APPLIED, false)) {
            SecureStore.putBoolean(KEY_BUILT_IN_APPLIED, true)
            SecureStore.putPlain(KEY, WallpaperStyle.PHOTO.name)
        }
        return runCatching {
            WallpaperStyle.valueOf(SecureStore.getPlain(KEY, WallpaperStyle.PHOTO.name))
        }.getOrDefault(WallpaperStyle.PHOTO)
    }

    fun set(style: WallpaperStyle) = SecureStore.putPlain(KEY, style.name)

    private fun customFile(context: Context): File = File(context.filesDir, FILE)

    fun loadCustom(context: Context): ImageBitmap? = runCatching {
        val f = customFile(context)
        if (!f.exists()) null else BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
    }.getOrNull()

    /**
     * 内置背景图（打包在 assets）。只解码一次并缓存；inSampleSize = 2 降采样，
     * 作为玻璃模糊 / 折射的采样源足够清晰，同时把内存占用压到约 2.5MB。
     */
    fun loadBuiltIn(context: Context): ImageBitmap? = builtInCache ?: runCatching {
        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
        context.assets.open(BUILT_IN_ASSET).use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
        }
    }.getOrNull().also { builtInCache = it }

    /** 把相册图片复制到应用私有目录，重启后仍可读取，不依赖 URI 持久权限 */
    fun saveCustom(context: Context, uri: Uri): Boolean = try {
        val stream = context.contentResolver.openInputStream(uri) ?: return false
        val bitmap = stream.use { BitmapFactory.decodeStream(it) } ?: return false
        FileOutputStream(customFile(context)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        true
    } catch (_: Exception) {
        false
    }

    fun hasCustom(context: Context): Boolean = customFile(context).exists()
}

private data class WallpaperColors(
    val gradient: List<Color>,
    val orbs: List<Triple<Color, Float, Offset>>,
)

private fun colorsFor(style: WallpaperStyle): WallpaperColors = when (style) {
    WallpaperStyle.AURORA -> WallpaperColors(
        listOf(Color(0xFF10162F), Color(0xFF1D2450), Color(0xFF2A1E4E), Color(0xFF0C1128)),
        listOf(
            Triple(Color(0xFF3D6BFF), 0.30f, Offset(0.18f, 0.16f)),
            Triple(Color(0xFFFF6BA8), 0.20f, Offset(0.86f, 0.30f)),
            Triple(Color(0xFF27D3C2), 0.16f, Offset(0.62f, 0.72f)),
            Triple(Color(0xFFFFB35C), 0.14f, Offset(0.10f, 0.88f)),
        ),
    )
    WallpaperStyle.SUNSET -> WallpaperColors(
        listOf(Color(0xFF2B1220), Color(0xFF5A2038), Color(0xFF8A3A2E), Color(0xFF1A0F1C)),
        listOf(
            Triple(Color(0xFFFF8A3D), 0.34f, Offset(0.20f, 0.18f)),
            Triple(Color(0xFFFF4D7A), 0.26f, Offset(0.82f, 0.28f)),
            Triple(Color(0xFFFFD166), 0.20f, Offset(0.58f, 0.70f)),
            Triple(Color(0xFF9B5DE5), 0.18f, Offset(0.12f, 0.86f)),
        ),
    )
    WallpaperStyle.OCEAN -> WallpaperColors(
        listOf(Color(0xFF061A2B), Color(0xFF0B3A4A), Color(0xFF106870), Color(0xFF04121E)),
        listOf(
            Triple(Color(0xFF22D3EE), 0.30f, Offset(0.16f, 0.20f)),
            Triple(Color(0xFF3B82F6), 0.24f, Offset(0.84f, 0.32f)),
            Triple(Color(0xFF34D399), 0.18f, Offset(0.60f, 0.74f)),
            Triple(Color(0xFFE0F2FE), 0.12f, Offset(0.10f, 0.88f)),
        ),
    )
    WallpaperStyle.MONO -> WallpaperColors(
        listOf(Color(0xFF0A0B0F), Color(0xFF1B1D26), Color(0xFF262835), Color(0xFF05060A)),
        listOf(
            Triple(Color(0xFF8C96B5), 0.24f, Offset(0.20f, 0.18f)),
            Triple(Color(0xFFD8DEEF), 0.16f, Offset(0.84f, 0.30f)),
            Triple(Color(0xFF6B7280), 0.20f, Offset(0.62f, 0.72f)),
            Triple(Color(0xFFB8C0D9), 0.12f, Offset(0.10f, 0.88f)),
        ),
    )
    // 图片类样式只在图片尚未解码出来时回退到渐变，避免首帧出现空白
    WallpaperStyle.CUSTOM -> colorsFor(WallpaperStyle.AURORA)
    WallpaperStyle.PHOTO -> colorsFor(WallpaperStyle.OCEAN)
}

/**
 * 供液态玻璃采样的背景层。
 *
 * 结构参照官方 BackdropDemoScaffold：本组件由外层 Modifier.layerBackdrop 录制，
 * 内部只放壁纸内容（不含任何 drawBackdrop 玻璃组件），玻璃组件全部在层外。
 */
@Composable
fun WallpaperBackdrop(
    style: WallpaperStyle = WallpaperStyle.PHOTO,
    image: ImageBitmap? = null,
    modifier: Modifier = Modifier,
) {
    if ((style == WallpaperStyle.CUSTOM || style == WallpaperStyle.PHOTO) && image != null) {
        Box(modifier.fillMaxSize()) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // 照片整体偏亮：压一层冷色暗调 + 上下暗角，保证玻璃卡片上的白字、
            // 状态栏图标可读，也让玻璃内的折射更沉稳。遮罩会被一起采样进 backdrop。
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color(0xFF05070F).copy(alpha = 0.30f))
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF05070F).copy(alpha = 0.40f),
                            Color.Transparent,
                            Color.Transparent,
                            Color(0xFF05070F).copy(alpha = 0.46f),
                        ),
                    ),
                )
            }
        }
        return
    }
    val colors = colorsFor(style)
    Canvas(modifier.fillMaxSize()) {
        drawRect(brush = Brush.linearGradient(colors = colors.gradient))
        colors.orbs.forEach { (color, alpha, pos) ->
            drawOrb(
                Offset(size.width * pos.x, size.height * pos.y),
                size.minDimension * 0.42f,
                color,
                alpha,
            )
        }
        drawGrid()
    }
}

private fun DrawScope.drawOrb(center: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

private fun DrawScope.drawGrid() {
    val step = 46f * density
    val lineColor = Color.White.copy(alpha = 0.035f)
    var x = 0f
    while (x < size.width) {
        drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 1f)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }
    val a = (18f * PI / 180f).toFloat()
    drawLine(
        Color.White.copy(alpha = 0.06f),
        Offset(0f, size.height * 0.55f),
        Offset(size.width, size.height * 0.55f - size.width * sin(a) / cos(a) * 0.35f),
        2f * density,
    )
}
