package com.liquidbox.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.liquidbox.app.data.SecureStore
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 启动编排（三段式）。
 *
 * 上一个版本的问题：开屏刚出现 400ms 就开始组合沉重的主界面，而主界面首帧
 * （layerBackdrop 录制层 / 玻璃 RuntimeShader 首次编译 / 壁纸解码）全部跑在主线程，
 * 会连续占用数秒的帧调度，导致开屏动画的 Animatable 一直停在 progress = 0。
 * 偏偏开屏在 p = 0 时水滴、文字、进度环的 alpha 全是 0，只剩一层纯渐变 ——
 * 于是用户看到的就是「点开图标后好几秒整屏空白（发紫/发蓝），动画才突然开始」。
 *
 * 现在严格分段，动画播放期间绝不组合主界面：
 *  SPLASH  —— 首帧只组合轻量开屏（纯 Canvas），动画立刻开始，不受任何加载干扰；
 *  LOADING —— 动画结束或点「跳过」后才组合主界面；开屏层保持完全不透明盖在上层，
 *             把主线程组合 / shader 编译的等待整段遮住，全程都有画面；
 *  REVEAL  —— 连续等到 3 个 vsync（意味着主界面首帧已真正绘制完成），开屏层淡出，
 *             无缝露出主界面，不会再闪出窗口底色；
 *  DONE    —— 移除开屏层。
 *
 * 设置里关闭开屏时直接从 LOADING 开始；此时由系统启动窗口的品牌画面兜底。
 *
 * 三种界面风格共用这一套编排：即使纯色风格没有 shader 编译，冷启动时
 * 主界面组合 / 应用初始化仍需要一点时间，开屏层会把这段等待盖住，
 * 不会出现点开图标后直接怼出半张界面、看起来像卡顿的情况。
 */
private const val STAGE_SPLASH = 0
private const val STAGE_LOADING = 1
private const val STAGE_REVEAL = 2
private const val STAGE_DONE = 3

@Composable
fun AppStartup() {
    val showSplash = remember { SecureStore.getBoolean("show_splash", true) }
    var stage by remember {
        mutableIntStateOf(if (showSplash) STAGE_SPLASH else STAGE_LOADING)
    }

    Box(Modifier.fillMaxSize()) {
        // 主界面只在开屏已经播完、且开屏层正盖着的时候才组合。
        if (stage >= STAGE_LOADING) {
            LiquidBoxRoot()

            // 组合 + 首帧 draw 都完成后才能连续拿到帧回调；以此判断主界面确实可见，
            // 而不是靠固定 delay 猜时间（不同机型差异可达数秒）。
            LaunchedEffect(Unit) {
                // 正常情况：三帧内主界面已绘制；极端机型加 6 秒兜底，绝不永久卡在开屏。
                withTimeoutOrNull(6000L) {
                    awaitFrame()
                    awaitFrame()
                    awaitFrame()
                }
                if (stage == STAGE_LOADING) stage = STAGE_REVEAL
            }
        }

        if (showSplash && stage < STAGE_DONE) {
            LiquidSplash(
                modifier = Modifier.fillMaxSize(),
                loading = stage >= STAGE_LOADING,
                fadeOut = stage == STAGE_REVEAL,
                onFinished = { if (stage == STAGE_SPLASH) stage = STAGE_LOADING },
                onFadeOutEnd = { stage = STAGE_DONE },
            )
        }
    }
}
