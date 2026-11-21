package com.wallhaven.browser.data

import android.content.Context
import android.util.Log
import com.wallhaven.browser.BuildConfig
import com.wallhaven.browser.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 检查更新：优先读 GitHub Release（带 APK 附件），没有 Release 时退回到读仓库里的 versionName。
 *
 * 仓库是开源的，App 本身不带任何服务端，所以「最新版」就是仓库里的版本。
 */
object UpdateChecker {

    const val OWNER = "Zero-zl-0"
    const val REPO = "Wallhaven-android"
    const val REPO_URL = "https://github.com/$OWNER/$REPO"
    private const val API_LATEST = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val RAW_GRADLE =
        "https://raw.githubusercontent.com/$OWNER/$REPO/main/app/build.gradle.kts"

    data class ReleaseInfo(
        val version: String,
        /** Release 附件里的 APK；没有 Release 时为 null。 */
        val apkUrl: String?,
        /** 手动下载页（Release 页 / 仓库页）。 */
        val htmlUrl: String,
        val notes: String,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** 比较 a 是否比 b 新（按 . 分段数字比较，忽略 -beta 这种后缀）。 */
    fun isNewer(a: String, b: String): Boolean {
        val pa = parts(a)
        val pb = parts(b)
        val size = maxOf(pa.size, pb.size)
        for (i in 0 until size) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(version: String): List<Int> =
        version.trim()
            .removePrefix("v")
            .substringBefore('-')
            .split('.')
            .map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }

    /** 联网拿最新版本信息。失败抛异常，由界面提示。 */
    suspend fun fetchLatest(): ReleaseInfo = withContext(Dispatchers.IO) {
        val release = runCatching { fetchRelease() }.getOrNull()
        if (release != null) return@withContext release

        // 没有 Release（或接口失败）时，读仓库里的 versionName 兜底
        val raw = runCatching { get(RAW_GRADLE) }.getOrNull()
        if (raw != null) {
            val version = Regex("""versionName\s*=\s*"([^"]+)"""")
                .find(raw)
                ?.groupValues
                ?.get(1)
            if (!version.isNullOrBlank()) {
                AppLog.info("Update", "仓库里没有 Release，读到 versionName=$version")
                return@withContext ReleaseInfo(
                    version = version.removePrefix("v"),
                    apkUrl = null,
                    htmlUrl = "$REPO_URL/releases",
                    notes = "仓库里还没有发布 Release，可以先去 Releases 页面看看。",
                )
            }
        }
        throw IllegalStateException("读不到最新版本号，检查网络或仓库是否改名")
    }

    private fun fetchRelease(): ReleaseInfo? {
        val body = get(API_LATEST) ?: return null
        val dto = runCatching { json.decodeFromString<ReleaseDto>(body) }.getOrNull() ?: return null
        val version = dto.tagName.removePrefix("v").ifBlank { dto.name.removePrefix("v") }
        if (version.isBlank()) return null
        val apk = dto.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        return ReleaseInfo(
            version = version,
            apkUrl = apk?.downloadUrl,
            htmlUrl = dto.htmlUrl.ifBlank { "$REPO_URL/releases" },
            notes = dto.body.trim(),
        )
    }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "WallhavenBrowser/${BuildConfig.VERSION_NAME}")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLog.warn("Update", "GET $url → HTTP ${response.code}")
                    return null
                }
                response.body?.string()
            }
        } catch (e: Exception) {
            AppLog.warn("Update", "GET $url 失败：${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /** 下载 APK 到应用私有目录，带进度回调（0..1）。 */
    suspend fun download(
        context: Context,
        url: String,
        version: String,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "updates")
        if (!dir.exists()) dir.mkdirs()
        // 清掉旧包，避免越积越多
        dir.listFiles()?.forEach { it.delete() }

        val target = File(dir, "wallhaven-$version.apk")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "WallhavenBrowser/${BuildConfig.VERSION_NAME}")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("下载失败 HTTP ${response.code}")
            val body = response.body ?: throw IllegalStateException("服务器没有返回内容")
            val total = body.contentLength()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                    }
                    output.flush()
                }
            }
        }
        onProgress(1f)
        AppLog.info("Update", "APK 已下载：${target.name}（${target.length() / 1024} KB）")
        target
    }

    @Serializable
    private data class ReleaseDto(
        @SerialName("tag_name") val tagName: String = "",
        val name: String = "",
        val body: String = "",
        @SerialName("html_url") val htmlUrl: String = "",
        val assets: List<AssetDto> = emptyList(),
    )

    @Serializable
    private data class AssetDto(
        val name: String = "",
        @SerialName("browser_download_url") val downloadUrl: String = "",
        val size: Long = 0,
    )
}
