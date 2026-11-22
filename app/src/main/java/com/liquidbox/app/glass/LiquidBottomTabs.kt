package com.liquidbox.app.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

/**
 * 薄转发层 -> 官方 LiquidBottomTabs。
 *
 * 液态玻璃：完全走官方实现（连续玻璃胶囊 + DampedDragAnimation 液滴 +
 * 色散折射 + 高光）。
 * MIUIX：悬浮圆角胶囊，不透明纯色 + 四周边框 + 整格滑动指示块。
 * M3：贴底连体导航条——全宽、背景一直铺到屏幕最底（含手势条区域）、
 *     顶部一条分割线，选中项是居中的 active indicator 胶囊。
 * 两种纯色风格都不调用官方 catalog；指示块位移放在 graphicsLayer，
 * 动画期间只重绘图层、不重组整棵内容树，并支持横向拖拽切换页签。
 */
@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val style = LocalAppStyle.current
    if (style == AppStyle.LIQUID) {
        com.kyant.backdrop.catalog.components.LiquidBottomTabs(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            backdrop = backdrop,
            tabsCount = tabsCount,
            modifier = modifier,
            content = content,
        )
        return
    }

    val c = flatColors(style)
    val m3 = style == AppStyle.M3
    val selected = selectedTabIndex()
    val density = LocalDensity.current

    val barShape: Shape = if (m3) RectangleShape else RoundedRectangle(c.tabRadius.dp)
    val indicatorShape: Shape =
        if (m3) Capsule() else RoundedRectangle((c.tabRadius - 6f).coerceAtLeast(8f).dp)
    val sidePad = if (m3) 12f.dp else 5f.dp
    val vertPad = if (m3) 10f.dp else 5f.dp
    val sidePadPx = with(density) { sidePad.toPx() }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .then(
                if (m3) Modifier.background(c.overlay)
                else Modifier.clip(barShape).background(c.card)
            )
            .then(
                if (m3) Modifier.drawBehind {
                    // 贴底栏的上边界：一条 1dp 分割线，左右与屏幕边缘相接
                    drawLine(
                        color = c.border,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1f.dp.toPx(),
                    )
                } else Modifier.border(1f.dp, c.border, barShape)
            )
            // M3 是贴底连体栏：背景铺到屏幕最底，只有内容避开手势条
            .then(if (m3) Modifier.navigationBarsPadding() else Modifier)
            .height(64f.dp),
    ) {
        val tabWidth = maxWidth / tabsCount
        val tabWidthPx = with(density) { tabWidth.toPx() }

        val indicator = remember { Animatable(selected.toFloat()) }
        LaunchedEffect(selected) {
            indicator.animateTo(
                selected.toFloat(),
                spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
            )
        }

        Box(
            Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer { translationX = indicator.value * tabWidthPx + sidePadPx }
                .padding(vertical = vertPad)
                .width(tabWidth - sidePad * 2f)
                .fillMaxHeight()
                .clip(indicatorShape)
                .background(c.accent.copy(alpha = if (m3) 0.24f else 0.18f))
                .border(1f.dp, c.accent.copy(alpha = 0.38f), indicatorShape),
        )

        Row(
            Modifier
                .fillMaxSize()
                .pointerInput(tabsCount) {
                    val one = size.width / tabsCount.toFloat()
                    detectHorizontalDragGestures(
                        onDragEnd = {},
                        onHorizontalDrag = { change, _ ->
                            val i = (change.position.x / one)
                                .toInt().coerceIn(0, tabsCount - 1)
                            if (i != selectedTabIndex()) onTabSelected(i)
                        },
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** 底栏单个页签：按当前风格自动在官方液滴页签与纯色页签之间切换 */
@Composable
fun RowScope.TabItem(
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalAppStyle.current != AppStyle.LIQUID) {
        SimpleTabItem(onClick = onClick, content = content)
    } else {
        LiquidBottomTab(
            onClick = onClick,
            content = content,
        )
    }
}
