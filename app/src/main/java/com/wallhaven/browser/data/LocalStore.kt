package com.wallhaven.browser.data

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 收藏与浏览历史都保存在本地 SharedPreferences 里，不上传任何数据。 */
class LocalStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("wallhaven_local", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun favorites(): List<Wallpaper> = decode(prefs.getString(KEY_FAVORITES, null))

    fun isFavorite(id: String): Boolean = favorites().any { it.id == id }

    /** @return 操作后是否处于收藏状态 */
    fun toggleFavorite(wallpaper: Wallpaper): Boolean {
        val list = favorites().toMutableList()
        val existed = list.removeAll { it.id == wallpaper.id }
        if (!existed) list.add(0, wallpaper)
        save(KEY_FAVORITES, list)
        return !existed
    }

    fun removeFavorite(id: String) {
        save(KEY_FAVORITES, favorites().filterNot { it.id == id })
    }

    fun clearFavorites() = prefs.edit().remove(KEY_FAVORITES).apply()

    fun history(): List<Wallpaper> = decode(prefs.getString(KEY_HISTORY, null))

    fun addHistory(wallpaper: Wallpaper) {
        val list = history().filterNot { it.id == wallpaper.id }.toMutableList()
        list.add(0, wallpaper)
        save(KEY_HISTORY, list.take(MAX_HISTORY))
    }

    fun findLocally(id: String): Wallpaper? =
        favorites().firstOrNull { it.id == id } ?: history().firstOrNull { it.id == id }

    fun clearHistory() = prefs.edit().remove(KEY_HISTORY).apply()

    /** 用详情页拿到的完整数据覆盖收藏里的旧副本（主要是为了补上 tags）。 */
    fun updateFavorite(wallpaper: Wallpaper) {
        val list = favorites()
        if (list.none { it.id == wallpaper.id }) return
        save(KEY_FAVORITES, list.map { if (it.id == wallpaper.id) wallpaper else it })
    }

    fun searchHistory(): List<String> {
        val raw = prefs.getString(KEY_SEARCH_HISTORY, null)
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split('\n').filter { it.isNotBlank() }
    }

    fun addSearchHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val next = (listOf(trimmed) + searchHistory().filterNot { it.equals(trimmed, ignoreCase = true) })
            .take(MAX_SEARCH_HISTORY)
        prefs.edit().putString(KEY_SEARCH_HISTORY, next.joinToString("\n")).apply()
    }

    fun clearSearchHistory() = prefs.edit().remove(KEY_SEARCH_HISTORY).apply()

    /** 首页是否优先展示适配本机比例的壁纸（默认开）。 */
    var matchDeviceRatio: Boolean
        get() = prefs.getBoolean(KEY_MATCH_DEVICE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_MATCH_DEVICE, value).apply()
        }

    /** 卡片里显示完整壁纸（按原比例）还是填满裁切（更清晰）。默认完整。 */
    var cardPreviewFull: Boolean
        get() = prefs.getBoolean(KEY_CARD_FULL, true)
        set(value) {
            prefs.edit().putBoolean(KEY_CARD_FULL, value).apply()
        }

    /** 收藏页是否隐藏 Sketchy / NSFW 卡片（默认隐藏）。 */
    var hideRestrictedInFavorites: Boolean
        get() = prefs.getBoolean(KEY_HIDE_RESTRICTED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_HIDE_RESTRICTED, value).apply()
        }

    private fun save(key: String, list: List<Wallpaper>) {
        prefs.edit().putString(key, json.encodeToString(ListSerializer(Wallpaper.serializer()), list)).apply()
    }

    private fun decode(raw: String?): List<Wallpaper> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<Wallpaper>>(raw) }.getOrDefault(emptyList())
    }

    private companion object {
        const val KEY_FAVORITES = "favorites_json"
        const val KEY_HISTORY = "history_json"
        const val KEY_MATCH_DEVICE = "match_device_ratio"
        const val KEY_CARD_FULL = "card_preview_full"
        const val KEY_HIDE_RESTRICTED = "favorites_hide_restricted"
        const val KEY_SEARCH_HISTORY = "search_history"
        const val MAX_HISTORY = 200
        const val MAX_SEARCH_HISTORY = 12
    }
}
