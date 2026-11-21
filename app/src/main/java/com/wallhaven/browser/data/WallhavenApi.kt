package com.wallhaven.browser.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.wallhaven.browser.util.AppLog
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : IOException(message)

/**
 * 极简 wallhaven.cc v1 API 客户端（只用到搜索与详情两个接口）。
 * 未登录时可读 SFW / Sketchy 内容；NSFW 需要账号 API key，本 App 不提供登录。
 */
object WallhavenApi {

    private const val USER_AGENT = "WallhavenBrowser/1.0 (Android; personal use)"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    suspend fun search(
        page: Int = 1,
        sorting: String = "date_added",
        order: String = "desc",
        query: String = "",
        categories: String = "111",
        purity: String = "100",
        atleast: String = "",
        ratios: String = "",
        colors: String = "",
        topRange: String = "1M",
        seed: String? = null,
        apiKey: String = "",
    ): SearchResponse = withContext(Dispatchers.IO) {
        val builder = baseUrl("api/v1/search")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sorting", sorting)
            .addQueryParameter("order", order)
            .addQueryParameter("categories", categories)
            .addQueryParameter("purity", purity)
        if (query.isNotBlank()) builder.addQueryParameter("q", query.trim())
        if (atleast.isNotBlank()) builder.addQueryParameter("atleast", atleast)
        if (ratios.isNotBlank()) builder.addQueryParameter("ratios", ratios)
        if (colors.isNotBlank()) builder.addQueryParameter("colors", colors)
        if (sorting == "toplist") builder.addQueryParameter("topRange", topRange)
        if (!seed.isNullOrBlank()) builder.addQueryParameter("seed", seed)
        if (apiKey.isNotBlank()) builder.addQueryParameter("apikey", apiKey)
        execute<SearchResponse>(builder.build())
    }

    suspend fun wallpaper(id: String, apiKey: String = ApiKeyStore.key): Wallpaper =
        withContext(Dispatchers.IO) {
            val builder = baseUrl("api/v1/w/$id")
            if (apiKey.isNotBlank()) builder.addQueryParameter("apikey", apiKey)
            execute<SingleResponse>(builder.build()).data
        }

    private fun baseUrl(path: String): HttpUrl.Builder =
        HttpUrl.Builder().scheme("https").host("wallhaven.cc").addPathSegments(path)

    private inline fun <reified T> execute(url: HttpUrl): T {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        val startedAt = System.currentTimeMillis()
        val rawQuery = url.query.orEmpty()
            .replace(Regex("apikey=[^&]*"), "apikey=***")
            .replace(Regex("_token=[^&]*"), "_token=***")
        val shortUrl = "${url.encodedPath}?$rawQuery".take(220)
        AppLog.debug("API", "GET $shortUrl")
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val elapsed = System.currentTimeMillis() - startedAt
                if (!response.isSuccessful) {
                    val serverMessage = runCatching {
                        json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
                    }.getOrNull()
                    AppLog.error(
                        "API",
                        "GET $shortUrl → HTTP ${response.code} ${serverMessage.orEmpty()} (${elapsed}ms)",
                    )
                    throw ApiException(response.code, serverMessage ?: "HTTP ${response.code}")
                }
                AppLog.debug("API", "GET $shortUrl → ${response.code} (${elapsed}ms)")
                if (body.isBlank()) {
                    AppLog.error("API", "空响应：$shortUrl")
                    throw IOException("服务器返回了空内容")
                }
                return json.decodeFromString<T>(body)
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: java.io.IOException) {
            AppLog.error("API", "GET $shortUrl 网络失败：${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }
}

fun Throwable.friendlyMessage(): String = when (this) {
    is ApiException -> when (code) {
        403 -> "wallhaven 返回 403：该内容需要账号 API key，或请求被拒绝"
        404 -> "找不到这个内容（可能已被删除）"
        429 -> "请求太频繁了，等几十秒再试"
        500, 502, 503, 504 -> "wallhaven 服务器出问题了（$code），稍后再试"
        else -> "服务器返回错误 HTTP $code"
    }
    is java.net.UnknownHostException -> "解析不到 wallhaven.cc：手机网络连不上，请开启 VPN / 代理后再试"
    is java.net.SocketTimeoutException -> "连接超时：网络太慢或无法访问 wallhaven.cc，请检查 VPN / 代理"
    is java.net.ConnectException -> "连接失败：wallhaven.cc 无法访问，请检查 VPN / 代理"
    is IOException -> "网络异常：${message ?: "未知错误"}"
    else -> message ?: "未知错误"
}
