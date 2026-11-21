package com.wallhaven.browser.data

import android.content.Context

/**
 * wallhaven 网站的会话（Cookie）保存在本机，不上传。
 * 只存会话 Cookie 和用户名，绝不保存密码。
 */
class AccountStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("wallhaven_account", Context.MODE_PRIVATE)

    var username: String
        get() = prefs.getString(KEY_USERNAME, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var loggedIn: Boolean
        get() = prefs.getBoolean(KEY_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_LOGGED_IN, value).apply()

    var cookies: String
        get() = prefs.getString(KEY_COOKIES, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_COOKIES, value).apply()

    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    /** 账号的 API key（从设置页读取），用于请求 NSFW 内容。 */
    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }

    /** 把 "a=1; b=2" 形式的 Cookie 头合并进已有值（同名覆盖，过期删除）。 */
    fun mergeCookieHeader(rawSetCookie: String) {
        cookies = mergeCookie(cookies, rawSetCookie)
    }

    companion object {
        private const val KEY_USERNAME = "username"
        private const val KEY_LOGGED_IN = "logged_in"
        private const val KEY_COOKIES = "cookies"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_API_KEY = "api_key"

        fun mergeCookie(existing: String, setCookieHeader: String): String {
            val pair = setCookieHeader.substringBefore(';').trim()
            if (!pair.contains('=')) return existing
            val name = pair.substringBefore('=').trim()
            val value = pair.substringAfter('=').trim()
            if (name.isEmpty()) return existing

            val expired = setCookieHeader.contains("Max-Age=0", ignoreCase = true) ||
                setCookieHeader.contains("expires=Thu, 01 Jan 1970", ignoreCase = true)

            val map = LinkedHashMap<String, String>()
            existing.split(';').map { it.trim() }.filter { it.contains('=') }.forEach {
                map[it.substringBefore('=')] = it.substringAfter('=')
            }
            if (expired) map.remove(name) else map[name] = value
            return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }
    }
}
