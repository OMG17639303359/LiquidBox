package com.liquidbox.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.liquidbox.app.ui.AppStartup

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 启动窗口用 Manifest 里的 Theme.LiquidBox.Splash（不透明深空画面）；
        // Activity 自身切回主主题，玻璃主界面仍保持边到边的透明系统栏。
        setTheme(R.style.Theme_LiquidBox)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        setContent {
            AppStartup()
        }
    }
}
