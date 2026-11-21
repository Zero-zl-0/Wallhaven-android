package com.wallhaven.browser.data

import com.wallhaven.browser.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SessionExpiredException : Exception("登录已失效，请重新登录")
class CloudflareBlockedException : Exception("wallhaven 返回了人机验证页，请稍后再试")

enum class AddFavoriteResult { SUCCESS, NEED_LOGIN, FAILED }

data class CloudFavorites(
    val items: List<Wallpaper>,
    val skipped: Int,
    val truncated: Boolean,
)

/**
 * 用 WebView 登录后拿到的会话 Cookie 访问 wallhaven 网站本身：
 * 只做两件事——读 /favorites 列表、把收藏加进账号。
 *
 * 注意：这是对网站的模拟访问，不是官方 API。网页结构变化时可能失效，
 * 所以所有失败都会写进 AppLog，方便排查。
 */
class WallhavenSession(private val store: AccountStore) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            val cookie = store.cookies
            val request = if (cookie.isNotBlank()) {
                chain.request().newBuilder().header("Cookie", cookie).build()
            } else {
                chain.request()
            }
            val response = chain.proceed(request)
            val setCookies = response.headers("Set-Cookie")
            if (setCookies.isNotEmpty()) {
                var merged = store.cookies
                setCookies.forEach { merged = AccountStore.mergeCookie(merged, it) }
                store.cookies = merged
            }
            response
        }
        .build()

    /** 返回登录用户名；未登录或拿不到页面时返回 null。用户名可能为空字符串（已登录但读不到名字）。 */
    suspend fun checkLogin(): String? = withContext(Dispatchers.IO) {
        val page = getPage("/") ?: return@withContext null
        if (isChallenge(page.html)) return@withContext null
        if (!page.html.contains("/auth/logout")) return@withContext null
        detectUsername() ?: ""
    }

    /**
     * 从账号设置页里读 API key（NSFW 内容必须带它才能通过公开 API 获取）。
     * 页面结构：<input type="text" readonly="readonly" value="32 位 key"/>
     * 注意：key 是凭据，只存本机，绝不写日志。
     */
    suspend fun fetchApiKey(): String? = withContext(Dispatchers.IO) {
        val page = getPage("/settings/account") ?: return@withContext null
        if (isChallenge(page.html) || page.finalUrl.contains("/login")) return@withContext null

        val patterns = listOf(
            Regex("""<input[^>]*readonly[^>]*value="([A-Za-z0-9]{32})"""", RegexOption.IGNORE_CASE),
            Regex("""value="([A-Za-z0-9]{32})"[^>]*readonly""", RegexOption.IGNORE_CASE),
        )
        for (pattern in patterns) {
            val key = pattern.find(page.html)?.groupValues?.get(1)
            if (!key.isNullOrBlank()) {
                AppLog.info("Account", "已读取到账号 API key")
                return@withContext key
            }
        }
        AppLog.warn("Account", "设置页里没找到 API key（NSFW 开关将不可用）")
        null
    }

    /** 从设置页或收藏页里读当前账号名。 */
    private fun detectUsername(): String? {
        val settings = getPage("/settings")
        if (settings != null) {
            AppLog.debug(
                "Account",
                "settings 页 ${settings.html.length} 字符，final=${settings.finalUrl}，challenge=${isChallenge(settings.html)}",
            )
            if (!isChallenge(settings.html) && !settings.finalUrl.contains("/login")) {
                val patterns = listOf(
                    Regex("""name="username"[^>]*value="([^"]+)""""),
                    Regex("""value="([^"]+)"[^>]*name="username""""),
                    Regex("""id="username"[^>]*value="([^"]+)""""),
                )
                for (pattern in patterns) {
                    val found = pattern.find(settings.html)?.groupValues?.get(1)?.trim()
                    if (!found.isNullOrEmpty()) return found
                }
                val inputs = Regex("""<input[^>]*name="([^"]+)"""")
                    .findAll(settings.html)
                    .map { it.groupValues[1] }
                    .take(12)
                    .joinToString(",")
                AppLog.debug("Account", "settings 输入框：$inputs")
            }
        }

        val favorites = getPage("/favorites?page=1")
        if (favorites != null && !isChallenge(favorites.html)) {
            usernameNearLogout(favorites.html)?.let {
                AppLog.debug("Account", "从收藏页识别到用户名：$it")
                return it
            }
        }
        return null
    }

    /**
     * 导航栏里「账号名」通常紧挨着「退出登录」链接，用它做锚点找最近的 /user/xxx。
     */
    private fun usernameNearLogout(html: String): String? {
        val logoutIndex = html.indexOf("/auth/logout")
        val matches = USER_LINK_REGEX.findAll(html).toList()
        if (matches.isEmpty()) return null
        val usable = matches.filterNot { BAD_USER_NAMES.contains(it.groupValues[1].lowercase()) }
        if (usable.isEmpty()) return null
        if (logoutIndex <= 0) return usable.first().groupValues[1]
        val before = usable.lastOrNull { it.range.first < logoutIndex }
        val after = usable.firstOrNull { it.range.first > logoutIndex }
        return (before ?: after)?.groupValues?.get(1)
    }

    suspend fun fetchFavorites(onProgress: (String) -> Unit = {}): CloudFavorites =
        withContext(Dispatchers.IO) {
            val ids = LinkedHashSet<String>()
            var page = 1
            var truncated = false
            while (page <= MAX_PAGES) {
                val result = getPage("/favorites?page=$page") ?: break
                if (result.finalUrl.contains("/login")) throw SessionExpiredException()
                if (isChallenge(result.html)) throw CloudflareBlockedException()
                // 有些情况下会话失效不会跳转，而是直接返回登录表单，这里再兜一层
                if (result.html.contains("name=\"password\"", ignoreCase = true)) {
                    throw SessionExpiredException()
                }

                val found = ID_REGEX.findAll(result.html)
                    .map { it.groupValues[1] }
                    .toList()
                    .distinct()
                if (found.isEmpty()) break

                val fresh = found.filter { ids.add(it) }
                AppLog.info("Account", "云端收藏第 $page 页：解析到 ${found.size} 个，新增 ${fresh.size} 个")
                onProgress("第 $page 页 · 已找到 ${ids.size} 张")
                if (fresh.isEmpty()) break
                if (ids.size >= MAX_ITEMS) {
                    truncated = true
                    break
                }
                page++
            }

            if (ids.isEmpty()) return@withContext CloudFavorites(emptyList(), 0, false)

            val items = mutableListOf<Wallpaper>()
            var skipped = 0
            ids.take(MAX_ITEMS).chunked(4).forEachIndexed { index, chunk ->
                val batch = chunk.map { id ->
                    async(Dispatchers.IO) { runCatching { WallhavenApi.wallpaper(id) }.getOrNull() }
                }.awaitAll()
                batch.filterNotNull().forEach { items += it }
                skipped += batch.count { it == null }
                onProgress("读取详情 ${minOf((index + 1) * 4, ids.size)}/${minOf(ids.size, MAX_ITEMS)}")
            }
            AppLog.info("Account", "云端收藏解析完成：${items.size} 张，跳过 ${skipped} 张")
            CloudFavorites(items, skipped, truncated)
        }

    /**
     * 把一张图加到账号收藏里。
     * 网站的真实做法是点作品页里的「Add to Favorites」链接：
     *   GET /favorites/add?wallHashid=<id>&collectionId=<集合>&_token=<csrf>
     * 所以优先直接复用页面里那条链接；找不到再退回到自己拼/常见的 POST 形式。
     */
    suspend fun addFavorite(id: String): AddFavoriteResult = withContext(Dispatchers.IO) {
        val page = getPage("/w/$id")
        if (page == null) {
            AppLog.warn("Account", "收藏 $id 失败：拿不到作品页")
            return@withContext AddFavoriteResult.FAILED
        }
        if (page.finalUrl.contains("/login")) return@withContext AddFavoriteResult.NEED_LOGIN
        if (isChallenge(page.html)) throw CloudflareBlockedException()

        if (page.html.contains("remove-fav", ignoreCase = true)) {
            AppLog.info("Account", "$id 已经在云端收藏里了")
            return@withContext AddFavoriteResult.SUCCESS
        }

        val token = CSRF_REGEX.find(page.html)?.groupValues?.get(1)
        if (token.isNullOrBlank()) {
            AppLog.warn("Account", "收藏 $id 失败：作品页没找到 csrf-token")
            return@withContext AddFavoriteResult.FAILED
        }

        val attempts = favoriteAttempts(page.html, id, token)
        AppLog.debug("Account", "收藏 $id 准备尝试 ${attempts.size} 个端点")

        for (attempt in attempts) {
            val url = (if (attempt.url.startsWith("http")) attempt.url else BASE + attempt.url)
                .replace("&amp;", "&")
            val safeUrl = url.replace(Regex("_token=[^&]*"), "_token=***")
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-CSRF-TOKEN", token)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "$BASE/w/$id")
            val request = if (attempt.useGet) {
                builder.get().build()
            } else {
                val form = FormBody.Builder().add("_token", token)
                attempt.fields.forEach { (k, v) -> form.add(k, v) }
                builder.post(form.build()).build()
            }
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    val finalUrl = response.request.url.toString()
                    if (finalUrl.contains("/login")) return@withContext AddFavoriteResult.NEED_LOGIN
                    if (response.isSuccessful || response.code in 300..399) {
                        AppLog.info(
                            "Account",
                            "已把 $id 加入云端收藏（${if (attempt.useGet) "GET" else "POST"} $safeUrl → HTTP ${response.code}）",
                        )
                        return@withContext AddFavoriteResult.SUCCESS
                    }
                    AppLog.warn("Account", "端点 $safeUrl 失败：HTTP ${response.code} ${body.take(160)}")
                }
            } catch (e: Exception) {
                AppLog.warn("Account", "端点 $safeUrl 异常：${e.javaClass.simpleName}: ${e.message}")
            }
        }
        AppLog.error("Account", "收藏 $id 失败：所有端点都没成功")
        AddFavoriteResult.FAILED
    }

    private fun favoriteAttempts(html: String, id: String, token: String): List<FavoriteAttempt> {
        val found = mutableListOf<FavoriteAttempt>()

        // 1) 页面里现成的「Add to Favorites」链接，参数最全（带 collectionId）
        Regex("""<a[^>]*class="[^"]*add-fav[^"]*"[^>]*href="([^"]+)"""", RegexOption.IGNORE_CASE)
            .find(html)?.let { found += FavoriteAttempt(it.groupValues[1], emptyMap(), useGet = true) }
        Regex("""<a[^>]*href="([^"]*favorites/add[^"]*)"[^>]*class="[^"]*add-fav""", RegexOption.IGNORE_CASE)
            .find(html)?.let { found += FavoriteAttempt(it.groupValues[1], emptyMap(), useGet = true) }

        // 2) 自己拼同样的地址（缺 collectionId 时站内会走默认收藏夹）
        found += FavoriteAttempt(
            "$BASE/favorites/add?wallHashid=$id&_token=$token",
            emptyMap(),
            useGet = true,
        )

        // 3) 老式表单 / data-* 线索
        Regex("""data-favorite-url="([^"]+)"""").find(html)?.let {
            found += FavoriteAttempt(it.groupValues[1], emptyMap())
        }

        // 4) 兜底
        found += FavoriteAttempt("$BASE/favorites/$id", emptyMap())
        found += FavoriteAttempt("$BASE/favorites", mapOf("wallpaper_id" to id))
        return found.distinctBy { it.url + (if (it.useGet) "#get" else "#post") }    }

    private data class FavoriteAttempt(
        val url: String,
        val fields: Map<String, String>,
        val useGet: Boolean = false,
    )

    private fun isChallenge(html: String): Boolean =
        html.contains("_cf_chl_opt") || html.contains("Just a moment")

    private data class Page(val html: String, val finalUrl: String)

    private fun getPage(path: String): Page? = try {
        val request = Request.Builder()
            .url(BASE + path)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            Page(body, response.request.url.toString())
        }
    } catch (e: Exception) {
        AppLog.error("Account", "请求 $path 失败：${e.javaClass.simpleName}: ${e.message}")
        null
    }

    companion object {
        private const val BASE = "https://wallhaven.cc"
        private const val MAX_PAGES = 20
        private const val MAX_ITEMS = 300

        private const val UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Mobile Safari/537.36"

        private val ID_REGEX = Regex("""/w/([a-z0-9]{6})""")
        private val CSRF_REGEX = Regex("""name="csrf-token"\s+content="([^"]+)"""")
        private val USER_LINK_REGEX = Regex("""/user/([A-Za-z0-9_\-]+)""")
        private val BAD_USER_NAMES = setOf(
            "avatar", "default", "images", "image", "css", "js", "login", "logout",
            "register", "settings", "favorites", "search",
        )
    }
}
