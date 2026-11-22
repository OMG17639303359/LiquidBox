package com.liquidbox.app.glass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kyant.backdrop.Backdrop

/**
 * 薄转发层 -> 官方 LiquidSlider。
 * 官方实现里拇指的 lens 折射强度由 DampedDragAnimation 的 pressProgress 驱动，
 * 长按/拖动时色散折射随之增强，底层 combined backdrop 合并了页面背景与轨道层。
 */
@Composable
fun LiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    // 非液态玻璃风格：纯自绘轨道 + 拇指，无任何 shader / backdrop 录制
    if (LocalAppStyle.current != AppStyle.LIQUID) {
        SimpleSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = modifier,
        )
        return
    }
    com.kyant.backdrop.catalog.components.LiquidSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        visibilityThreshold = (valueRange.endInclusive - valueRange.start) / 1000f,
        backdrop = backdrop,
        modifier = modifier,
    )
}
