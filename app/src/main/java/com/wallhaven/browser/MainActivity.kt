package com.wallhaven.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wallhaven.browser.ui.WallhavenApp
import com.wallhaven.browser.ui.theme.WallhavenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 跟随系统深浅色自动切换状态栏/导航栏图标颜色
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WallhavenTheme {
                WallhavenApp()
            }
        }
    }
}
