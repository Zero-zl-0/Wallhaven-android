package com.wallhaven.browser.util

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel(val label: String) {
    DEBUG("调试"),
    INFO("信息"),
    WARN("警告"),
    ERROR("错误"),
}

data class LogEntry(
    val time: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
) {
    fun timeText(): String = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(time))

    fun fileLine(): String =
        "$time|${level.name}|$tag|${message.replace('\n', ' ').take(1200)}"
}

/**
 * 轻量运行日志：同时写 logcat 和 App 私有目录下的 app.log。
 * 用于用户反馈问题时把日志导出来排查，不联网、不上传。
 */
object AppLog {

    private const val MAX_ENTRIES = 800
    private const val MAX_FILE_BYTES = 256 * 1024L
    private const val FILE_NAME = "app.log"

    private val lock = Any()
    private val _entries = mutableStateListOf<LogEntry>()
    private var logFile: File? = null

    val entries: List<LogEntry> get() = _entries

    fun init(context: Context) {
        synchronized(lock) {
            if (logFile != null) return
            val dir = File(context.filesDir, "logs").apply { mkdirs() }
            val file = File(dir, FILE_NAME)
            logFile = file
            runCatching {
                if (file.exists()) {
                    file.readLines().takeLast(MAX_ENTRIES).forEach { line ->
                        parse(line)?.let { _entries.add(it) }
                    }
                }
            }
        }
        info("AppLog", "日志已启动，载入历史 ${_entries.size} 条")
    }

    fun debug(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)

    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message)

    fun warn(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.WARN, tag, message, throwable)

    fun error(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.ERROR, tag, message, throwable)

    fun count(level: LogLevel): Int = _entries.count { it.level == level }

    /** 给界面看的一行一条，带时间和级别。 */
    fun exportText(): String = synchronized(lock) {
        _entries.joinToString("\n") { "${it.timeText()} ${it.level.label} [${it.tag}] ${it.message}" }
    }

    /** 原始格式，方便追加到日志文件。 */
    fun exportRaw(): String = synchronized(lock) {
        _entries.joinToString("\n") { it.fileLine() }
    }

    fun clear() {
        synchronized(lock) {
            _entries.clear()
            runCatching { logFile?.writeText("") }
        }
        info("AppLog", "日志已清空")
    }

    fun diagnostic(context: Context): String {
        val pm = context.packageManager
        val pkg = runCatching { pm.getPackageInfo(context.packageName, 0) }.getOrNull()
        val versionName = pkg?.versionName ?: "-"
        val versionCode = if (pkg == null) {
            "-"
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkg.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            pkg.versionCode.toString()
        }
        val metrics = context.resources.displayMetrics
        return buildString {
            appendLine("版本：$versionName ($versionCode)")
            appendLine("系统：Android ${Build.VERSION.RELEASE}（SDK ${Build.VERSION.SDK_INT}）")
            appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("屏幕：${metrics.widthPixels}×${metrics.heightPixels} @ ${metrics.density}×")
            append("日志：${_entries.size} 条")
        }
    }

    private fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message)
        synchronized(lock) {
            _entries.add(entry)
            while (_entries.size > MAX_ENTRIES) {
                _entries.removeAt(0)
            }
            runCatching {
                val file = logFile ?: return@runCatching
                if (file.length() > MAX_FILE_BYTES) {
                    val keep = file.readLines().takeLast(MAX_ENTRIES / 2)
                    file.writeText(keep.joinToString("\n", postfix = "\n"))
                }
                file.appendText(entry.fileLine() + "\n")
            }
        }
        val line = if (throwable != null) {
            "$message\n${throwable.stackTraceToString().take(3000)}"
        } else {
            message
        }
        when (level) {
            LogLevel.DEBUG -> Log.d("WH/$tag", line)
            LogLevel.INFO -> Log.i("WH/$tag", line)
            LogLevel.WARN -> Log.w("WH/$tag", line)
            LogLevel.ERROR -> Log.e("WH/$tag", line)
        }
    }

    private fun parse(line: String): LogEntry? {
        val parts = line.split('|', limit = 4)
        if (parts.size < 4) return null
        val time = parts[0].toLongOrNull() ?: return null
        val level = runCatching { LogLevel.valueOf(parts[1]) }.getOrNull() ?: return null
        return LogEntry(time, level, parts[2], parts[3])
    }
}
