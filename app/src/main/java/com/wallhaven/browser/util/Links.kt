package com.wallhaven.browser.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.wallhaven.browser.data.Wallpaper

fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "没有可以打开网页的应用", Toast.LENGTH_SHORT).show()
    }
}

fun shareWallpaper(context: Context, wallpaper: Wallpaper) {
    val link = wallpaper.shortUrl.ifBlank { wallpaper.url }
    if (link.isBlank()) return
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "wallhaven ${wallpaper.displayResolution}")
            putExtra(Intent.EXTRA_TEXT, "wallhaven.cc 壁纸 ${wallpaper.displayResolution}：$link")
        }
        context.startActivity(
            Intent.createChooser(intent, "分享壁纸链接").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: Exception) {
        Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
    }
}
