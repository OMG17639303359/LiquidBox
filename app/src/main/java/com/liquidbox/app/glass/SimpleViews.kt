package com.liquidbox.app.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule

/**
 * 非液态玻璃风格专用控件：只用 Compose foundation 自绘，
 * 不含任何 backdrop / drawBackdrop / RenderEffect / RuntimeShader。
 * 尺寸对齐官方 catalog 组件，切换风格时布局不会跳动。
 */

@Composable
fun SimpleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val checkedState = rememberUpdatedState(checked)
    val callback = rememberUpdatedState(onCheckedChange)
    val c = flatColors(LocalAppStyle.current)
    val track = if (checked) c.accent else c.border
    Box(
        modifier
            .size(64f.dp, 30f.dp)
            .clip(Capsule())
            .background(track)
            .border(1f.dp, c.border, Capsule())
            .clickable(
                enabled = enabled,
                onClick = { callback.value(!checkedState.value) },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = if (checked) 36f.dp else 3f.dp)
                .size(24f.dp)
                .clip(Capsule())
                .background(Color.White),
        )
    }
}

@Composable
fun SimpleSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    val c = flatColors(LocalAppStyle.current)
    val start = valueRange.start
    val span = (valueRange.endInclusive - start).coerceAtLeast(0.0001f)
    // 组合期读取 value()，状态变化时自动重组并重算 fraction
    val fraction = ((value() - start) / span).coerceIn(0f, 1f)
    val callback = rememberUpdatedState(onValueChange)

    fun applyAt(x: Float, width: Int) {
        val f = (x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
        callback.value((start + f * span).coerceIn(valueRange))
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(28f.dp)
            // 用标准手势检测替代 while(true) + awaitPointerEvent 的事件轮询，
            // 没有手势时不占用主线程，滚动列表明显更顺。
            .pointerInput(Unit) {
                detectTapGestures { offset -> applyAt(offset.x, size.width) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {},
                    onDragEnd = {},
                    onHorizontalDrag = { change, _ ->
                        applyAt(change.position.x, size.width)
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(6f.dp).clip(Capsule()).background(c.border))
        Box(
            Modifier
                .width(maxWidth * fraction)
                .height(6f.dp)
                .clip(Capsule())
                .background(c.accent),
        )
        Box(
            Modifier
                .offset(x = (maxWidth - 22f.dp) * fraction)
                .size(22f.dp)
                .clip(Capsule())
                .background(Color.White)
                .border(2f.dp, c.accent, Capsule()),
        )
    }
}

/** 非液态玻璃风格底栏的单个页签：透明容器，选中态由底层的滑动指示块表达 */
@Composable
fun RowScope.SimpleTabItem(
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .clip(Capsule())
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick,
            )
            .fillMaxHeight()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(2f.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
