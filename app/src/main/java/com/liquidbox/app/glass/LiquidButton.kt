package com.liquidbox.app.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule

/**
 * 薄转发层：只把参数透传给 Kyant0 官方 catalog 组件，不实现任何玻璃效果。
 *
 * 官方源码位于 com.kyant.backdrop.catalog.components，一字未改地引入工程；
 * 折射(lens)、高光(InteractiveHighlight)、按压弹簧(DampedDragAnimation)
 * 全部由官方实现提供。保留本层函数签名是为了让工具箱页面无需改动调用点。
 */
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    @Suppress("UNUSED_PARAMETER") shape: () -> Shape = { Capsule() },
    content: @Composable RowScope.() -> Unit,
) {
    // 非液态玻璃风格：纯色圆角按钮，无 backdrop 采样、无高光、无弹簧动画
    val style = LocalAppStyle.current
    if (style != AppStyle.LIQUID) {
        val sh = shape()
        Row(
            modifier
                .clip(sh)
                .background(flatColors(style).button)
                .then(
                    if (tint != null && tint.isSpecified) Modifier.background(tint, sh)
                    else Modifier
                )
                .border(1f.dp, flatColors(style).border, sh)
                .clickable(enabled = enabled, onClick = onClick)
                .height(48f.dp)
                .padding(horizontal = 16f.dp),
            horizontalArrangement = Arrangement.spacedBy(8f.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        return
    }
    // 官方组件把 tint 用 BlendMode.Hue 画：色相取自 tint，饱和度和亮度取自
    // 逐帧采样的背面画面。彩色壁纸/界面上同一个按钮就会一会儿红、一会儿黄、一会儿蓝。
    // 这里改成固定的淡色叠加（surfaceColor 是普通 drawRect），选中态颜色稳定不漂移。
    // 官方 catalog 源码保持原样，未做任何修改。
    val fixedTint = if (tint != null && tint.isSpecified) {
        tint.copy(alpha = (tint.alpha * 0.55f).coerceAtMost(0.34f))
    } else {
        Color.Unspecified
    }
    com.kyant.backdrop.catalog.components.LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier,
        isInteractive = enabled,
        tint = Color.Unspecified,
        surfaceColor = fixedTint,
        content = content,
    )
}

@Composable
fun LiquidToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") enabled: Boolean = true,
    backdrop: Backdrop = LocalGlassBackdrop.current,
) {
    // 非液态玻璃风格：静态自绘开关，点击即取反，天然没有旧闭包问题
    if (LocalAppStyle.current != AppStyle.LIQUID) {
        SimpleSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
        )
        return
    }
    // 官方 LiquidToggle 会把 selected / onSelect 闭包缓存进 remember 的
    // DampedDragAnimation：点击（无拖动）时靠 selected() 判断新状态，
    // 拖动时靠 targetValue 判断。若直接传 { checked }，被缓存的闭包会永远
    // 读到首次组合时的旧值 —— 表现为「点击只能打开、无法关闭，滑动却能关闭」。
    // 这里用 rememberUpdatedState 让被缓存的闭包始终读到最新值；同时把 lambda
    // 实例固定下来，避免官方 LaunchedEffect(selected) 每次重组都重启。
    // 官方 catalog 源码保持原样，未做任何修改。
    val checkedState = rememberUpdatedState(checked)
    val callbackState = rememberUpdatedState(onCheckedChange)
    val selectedProvider: () -> Boolean = remember { { checkedState.value } }
    val onSelectProvider: (Boolean) -> Unit = remember { { callbackState.value(it) } }
    com.kyant.backdrop.catalog.components.LiquidToggle(
        selected = selectedProvider,
        onSelect = onSelectProvider,
        backdrop = backdrop,
        modifier = modifier,
    )
}
