package com.liquidbox.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** 图片上传：从 SAF Uri 采样解码、等比缩放、压缩成 JPEG 并转 base64；同时提供缩略图 */
object ImageUtils {

    private const val MAX_EDGE = 1024
    private const val JPEG_QUALITY = 78

    /** 先读尺寸算采样率，避免大图直接进内存 */
    private fun decodeSampled(context: Context, uri: Uri, maxEdge: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val (w, h) = bounds.outWidth to bounds.outHeight
        if (w <= 0 || h <= 0) return null
        var sample = 1
        val longest = max(w, h)
        while (longest / sample > maxEdge * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        // 二次等比缩放，保证最长边 <= maxEdge
        val scale = maxEdge.toFloat() / max(bmp.width, bmp.height)
        if (scale < 1f) {
            val scaled = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).roundToInt().coerceAtLeast(1),
                (bmp.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
            if (scaled != bmp) bmp.recycle()
            scaled
        } else bmp
    } catch (e: Exception) {
        null
    }

    /** 缩略图（给 Compose 显示） */
    fun thumbnail(context: Context, uri: Uri): ImageBitmap? =
        decodeSampled(context, uri, 256)?.asImageBitmap()

    /** 压缩成 JPEG + base64，供多模态请求 */
    fun encode(context: Context, uri: Uri): LlmClient.Image? {
        val bmp = decodeSampled(context, uri, MAX_EDGE) ?: return null
        val out = ByteArrayOutputStream()
        val ok = bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        val data = out.toByteArray()
        bmp.recycle()
        if (!ok || data.isEmpty()) return null
        return LlmClient.Image("image/jpeg", Base64.encodeToString(data, Base64.NO_WRAP))
    }
}
