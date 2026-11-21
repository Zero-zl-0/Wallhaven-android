package com.wallhaven.browser.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wallhaven.browser.data.LocalStore

/**
 * 卡片预览偏好：
 * - fullPreview = true  按壁纸真实比例显示完整图（wallhaven 的缩略图是 16:9 裁切的，
 *   所以完整预览要用 thumbs.original，清晰度会低一些）
 * - fullPreview = false 用 16:9 缩略图填满卡片（清晰，但竖图会被裁）
 */
class PreviewSettings(private val store: LocalStore) {

    var fullPreview by mutableStateOf(store.cardPreviewFull)
        private set

    fun updateFullPreview(value: Boolean) {
        fullPreview = value
        store.cardPreviewFull = value
    }
}

val LocalPreviewSettings = compositionLocalOf<PreviewSettings?> { null }
