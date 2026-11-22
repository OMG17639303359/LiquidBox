package com.liquidbox.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquidbox.app.glass.AppStyle
import com.liquidbox.app.glass.LocalAppStyle
import com.liquidbox.app.glass.LocalGlassPalette
import com.liquidbox.app.glass.flatColors
import com.liquidbox.app.glass.liquidGlass
import com.kyant.shapes.RoundedRectangle

@Composable
fun T(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 14,
    color: Color? = null,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
) {
    val palette = LocalGlassPalette.current
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color ?: palette.textPrimary,
            fontSize = size.sp,
            fontWeight = weight,
            textAlign = align,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 玻璃卡片：所有页面内容都基于它，边缘折射 + 高光 + 阴影 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: () -> Shape = { RoundedRectangle(24f.dp) },
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val style = LocalAppStyle.current
    val cardShape: () -> Shape = if (style == AppStyle.LIQUID) {
        shape
    } else {
        { RoundedRectangle(flatColors(style).cardRadius.dp) }
    }
    Column(
        modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = cardShape,
                blurDp = 10f,
                lensHeightDp = 18f,
                lensAmountDp = 22f,
            )
            .padding(18f.dp),
        verticalArrangement = Arrangement.spacedBy(12f.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    T(text, modifier.padding(start = 6f.dp, top = 10f.dp, bottom = 2f.dp), size = 13,
        color = LocalGlassPalette.current.textSecondary, weight = FontWeight.Bold)
}

/** 纯 Compose 绘制的简易图标，避免额外依赖 */
@Composable
fun Glyph(symbol: String, modifier: Modifier = Modifier, size: Int = 20, color: Color? = null) {
    T(symbol, modifier, size = size, color = color, align = TextAlign.Center)
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        T(label, size = 13, color = LocalGlassPalette.current.textSecondary)
        Spacer(Modifier.width(12f.dp))
        T(value, size = 13, weight = FontWeight.Medium, maxLines = 2, align = TextAlign.End)
    }
}

/** 危险操作二次确认：本身就是一块液态玻璃卡片 */
@Composable
fun ConfirmGlassCard(
    title: String,
    detail: String,
    confirmLabel: String = "确认执行",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onRollbackHint: String? = null,
) {
    val palette = LocalGlassPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(24f.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .liquidGlass(
                    shape = { RoundedRectangle(28f.dp) },
                    surfaceColor = palette.containerTint,
                    blurDp = 16f,
                    lensHeightDp = 22f,
                    lensAmountDp = 26f,
                )
                .padding(22f.dp),
            verticalArrangement = Arrangement.spacedBy(14f.dp),
        ) {
            T(title, size = 19, weight = FontWeight.Bold, color = palette.danger)
            T(detail, size = 14, color = palette.textSecondary)
            if (onRollbackHint != null) {
                T("回滚方式：$onRollbackHint", size = 12, color = palette.success)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12f.dp)) {
                com.liquidbox.app.glass.LiquidButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { T("取消", size = 14, align = TextAlign.Center) }
                com.liquidbox.app.glass.LiquidButton(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    tint = palette.danger,
                ) { T(confirmLabel, size = 14, weight = FontWeight.Bold, align = TextAlign.Center) }
            }
        }
    }
}
