package com.wallhaven.browser.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.wallhaven.browser.data.Wallpaper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class DownloadStatus { RUNNING, SUCCESS, FAILED, UNKNOWN }

data class DownloadProgress(val status: DownloadStatus, val fraction: Float)

object Downloader {

    /** 交给系统下载管理器，进度在通知栏里显示，文件落在 相册/Pictures/Wallhaven/ 下。 */
    fun enqueue(context: Context, wallpaper: Wallpaper): Long? {
        if (wallpaper.path.isBlank()) {
            Toast.makeText(context, "这张图的下载地址是空的", Toast.LENGTH_SHORT).show()
            return null
        }
        val name = wallpaper.fileName
        val request = DownloadManager.Request(Uri.parse(wallpaper.path))
            .setTitle(name)
            .setDescription("来自 wallhaven.cc 的壁纸")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "Wallhaven/$name")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        return try {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = manager.enqueue(request)
            AppLog.info("Download", "开始下载 $name ← ${wallpaper.path.take(160)}")
            id
        } catch (e: Exception) {
            AppLog.error("Download", "下载失败 $name：${e.javaClass.simpleName}: ${e.message}")
            Toast.makeText(context, "下载失败：${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    /** 查询某个下载任务的进度；任务不存在时返回 null。 */
    fun progressOf(context: Context, downloadId: Long): DownloadProgress? {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return null
        return try {
            manager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                )
                val total = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                )
                val fraction = if (total > 0) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> DownloadProgress(DownloadStatus.SUCCESS, 1f)
                    DownloadManager.STATUS_FAILED -> DownloadProgress(DownloadStatus.FAILED, 0f)
                    DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_PAUSED -> DownloadProgress(DownloadStatus.RUNNING, fraction)
                    else -> DownloadProgress(DownloadStatus.UNKNOWN, fraction)
                }
            }
        } catch (e: Exception) {
            AppLog.warn("Download", "查询进度失败：${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }
}

/**
 * 本机记录：壁纸 id -> 下载任务 id / 文件名 / 是否已完成。
 * 用于详情页显示「下载完成」，以及删除文件后清理状态。
 */
object DownloadRegistry {

    data class Entry(val downloadId: Long, val name: String, val done: Boolean)

    private const val PREFS = "wallhaven_downloads"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun put(context: Context, wallpaperId: String, downloadId: Long, name: String) {
        prefs(context).edit().putString(wallpaperId, "$downloadId|$name|false").apply()
    }

    fun entry(context: Context, wallpaperId: String): Entry? {
        val raw = prefs(context).getString(wallpaperId, null) ?: return null
        val parts = raw.split("|")
        if (parts.size < 3) return null
        return Entry(
            downloadId = parts[0].toLongOrNull() ?: return null,
            name = parts[1],
            done = parts[2].toBoolean(),
        )
    }

    fun markDone(context: Context, wallpaperId: String) {
        val entry = entry(context, wallpaperId) ?: return
        prefs(context).edit()
            .putString(wallpaperId, "${entry.downloadId}|${entry.name}|true")
            .apply()
    }

    fun remove(context: Context, wallpaperId: String) {
        prefs(context).edit().remove(wallpaperId).apply()
    }

    /** 文件被删除时，把对应记录一起清掉。 */
    fun removeByName(context: Context, name: String) {
        val all = prefs(context).all
        val editor = prefs(context).edit()
        all.forEach { (key, value) ->
            val parts = (value as? String)?.split("|").orEmpty()
            if (parts.size >= 2 && parts[1] == name) editor.remove(key)
        }
        editor.apply()
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "未知大小"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1) String.format(Locale.CHINA, "%.1f MB", mb)
    else String.format(Locale.CHINA, "%.0f KB", bytes / 1024.0)
}

fun formatDate(raw: String): String {
    if (raw.isBlank()) return ""
    val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return try {
        val date = parser.parse(raw) ?: return raw.take(10)
        val diff = System.currentTimeMillis() - date.time
        val day = 24 * 60 * 60 * 1000L
        when {
            diff < 60_000L -> "刚刚"
            diff < 60 * 60_000L -> "${diff / 60_000L} 分钟前"
            diff < day -> "${diff / (60 * 60_000L)} 小时前"
            diff < 30 * day -> "${diff / day} 天前"
            diff < 365 * day -> "${diff / (30 * day)} 个月前"
            else -> SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(date)
        }
    } catch (e: Exception) {
        raw.take(10)
    }
}

fun Long.toCompact(): String = when {
    this >= 1_000_000 -> String.format(Locale.CHINA, "%.1fM", this / 1_000_000.0)
    this >= 1_000 -> String.format(Locale.CHINA, "%.1fk", this / 1_000.0)
    else -> toString()
}

fun Int.toCompact(): String = toLong().toCompact()

/** 距离现在多少天（用于“只看最近 N 天”这类本地筛选）。 */
fun daysSince(raw: String): Long? {
    if (raw.isBlank()) return null
    val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return try {
        val date = parser.parse(raw) ?: return null
        (System.currentTimeMillis() - date.time) / (24 * 60 * 60 * 1000L)
    } catch (e: Exception) {
        null
    }
}
