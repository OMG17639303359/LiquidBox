package com.liquidbox.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liquidbox.app.glass.LocalGlassPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用图标缓存。
 *
 * PackageManager.getApplicationIcon 返回 Drawable（可能是 AdaptiveIconDrawable /
 * VectorDrawable / BitmapDrawable），统一画到固定尺寸的 ARGB_8888 Bitmap 再转成
 * Compose 的 ImageBitmap。按包名缓存，避免列表滚动时反复解码。
 */
object AppIconStore {
    private const val MAX_ENTRIES = 120
    private const val PX = 144

    private val cache = object : LruCache<String, ImageBitmap>(MAX_ENTRIES) {}

    fun cached(packageName: String): ImageBitmap? =
        if (packageName.isBlank()) null else cache.get(packageName)

    @Synchronized
    fun load(context: Context, packageName: String): ImageBitmap? {
        if (packageName.isBlank()) return null
        cached(packageName)?.let { return it }
        return runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = Bitmap.createBitmap(PX, PX, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, PX, PX)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        }.getOrNull()?.also { cache.put(packageName, it) }
    }

    /** 安装 / 卸载后调用，避免列表里出现旧图标 */
    fun clear() = cache.evictAll()
}

/**
 * 应用图标：命中缓存立即显示，否则在 IO 线程解码。
 * 取不到图标时显示应用名首字，永远不会空白或闪退。
 */
@Composable
fun AppIcon(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 44f.dp,
) {
    val context = LocalContext.current
    val palette = LocalGlassPalette.current
    var icon by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        icon = AppIconStore.cached(packageName)
            ?: withContext(Dispatchers.IO) { AppIconStore.load(context, packageName) }
    }

    val shape = RoundedCornerShape(percent = 24)
    Box(
        modifier.size(size).clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = icon
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = label,
                modifier = Modifier.size(size).clip(shape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                T(
                    label.take(1).ifEmpty { "?" },
                    size = (size.value * 0.42f).toInt().coerceAtLeast(10),
                    color = palette.textSecondary,
                    align = TextAlign.Center,
                )
            }
        }
    }
}
