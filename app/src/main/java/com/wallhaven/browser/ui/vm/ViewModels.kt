package com.wallhaven.browser.ui.vm

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wallhaven.browser.data.ApiKeyStore
import com.wallhaven.browser.data.LocalStore
import com.wallhaven.browser.data.SearchResponse
import com.wallhaven.browser.data.TagStat
import com.wallhaven.browser.data.Wallpaper
import com.wallhaven.browser.data.WallhavenApi
import com.wallhaven.browser.data.friendlyMessage
import com.wallhaven.browser.util.AppLog
import com.wallhaven.browser.util.DeviceRatio
import com.wallhaven.browser.util.daysSince
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

enum class FeedKind(val title: String) {
    Top("热门"),
    Latest("最新"),
    Random("随机"),
    MostViewed("最多浏览"),
    MostFavorited("最多收藏"),
}

/** 分页浏览的公共逻辑：首屏加载、触底加载更多、失败重试。 */
abstract class PagedViewModel(app: Application) : AndroidViewModel(app) {

    var items by mutableStateOf<List<Wallpaper>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var total by mutableStateOf(0)
        private set
    var page by mutableStateOf(0)
        private set
    var lastPage by mutableStateOf(1)
        private set

    private var seed: String? = null
    private var job: Job? = null

    /**
     * 列表滚动位置（首项 index + 偏移）。放在 ViewModel 里，进详情页再返回时能精确回到原位，
     * 不依赖 Compose 的 rememberSaveable，也不触发重组。
     */
    var savedScrollIndex: Int = 0
        private set
    var savedScrollOffset: Int = 0
        private set

    fun rememberScroll(index: Int, offset: Int) {
        savedScrollIndex = index
        savedScrollOffset = offset
    }

    protected abstract suspend fun fetchPage(page: Int, seed: String?): SearchResponse

    /** 每页数据到手后可以在这里重排（首页按本机比例优先就靠它）。 */
    protected open fun arrange(data: List<Wallpaper>, page: Int): List<Wallpaper> = data

    val canLoadMore: Boolean
        get() = !isLoading && error == null && page < lastPage

    fun refresh() {
        job?.cancel()
        items = emptyList()
        page = 0
        lastPage = 1
        total = 0
        error = null
        seed = null
        savedScrollIndex = 0
        savedScrollOffset = 0
        load(1)
    }

    fun loadMore() {
        if (!canLoadMore || items.isEmpty()) return
        load(page + 1)
    }

    fun retry() {
        error = null
        if (items.isEmpty()) refresh() else load(page + 1)
    }

    private fun load(target: Int) {
        isLoading = true
        error = null
        job = viewModelScope.launch {
            try {
                val response = fetchPage(target, seed)
                seed = response.meta.seed ?: seed
                page = response.meta.currentPage.coerceAtLeast(1)
                lastPage = response.meta.lastPage.coerceAtLeast(1)
                total = response.meta.total
                val known = items.mapTo(HashSet()) { it.id }
                val fresh = response.data.filterNot { known.contains(it.id) }
                items = items + arrange(fresh, target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.error(
                    "Feed",
                    "${this@PagedViewModel::class.simpleName} 第 ${target} 页加载失败：${e.friendlyMessage()}",
                    e,
                )
                error = e.friendlyMessage()
            } finally {
                isLoading = false
            }
        }
    }
}

class FeedViewModel(
    app: Application,
    private val kind: FeedKind,
    private val deviceAspect: Float,
    matchDeviceInitially: Boolean,
    private val store: LocalStore,
) : PagedViewModel(app) {

    var matchDevice by mutableStateOf(matchDeviceInitially)
        private set

    init {
        refresh()
    }

    private val ratiosParam: String
        get() = if (matchDevice) DeviceRatio.filterFor(deviceAspect) else ""

    /** 同一页里，把与本机比例最接近的排前面。 */
    override fun arrange(data: List<Wallpaper>, page: Int): List<Wallpaper> =
        if (matchDevice) data.sortedBy { abs(it.aspect - deviceAspect) } else data

    override suspend fun fetchPage(page: Int, seed: String?): SearchResponse {
        val ratios = ratiosParam
        return when (kind) {
            FeedKind.Top -> WallhavenApi.search(
                page = page,
                sorting = "toplist",
                topRange = "1M",
                ratios = ratios,
            )
            FeedKind.Latest -> WallhavenApi.search(
                page = page,
                sorting = "date_added",
                ratios = ratios,
            )
            FeedKind.Random -> WallhavenApi.search(
                page = page,
                sorting = "random",
                seed = seed,
                ratios = ratios,
            )
            FeedKind.MostViewed -> WallhavenApi.search(
                page = page,
                sorting = "views",
                ratios = ratios,
            )
            FeedKind.MostFavorited -> WallhavenApi.search(
                page = page,
                sorting = "favorites",
                ratios = ratios,
            )
        }
    }

    fun toggleMatchDevice() {
        matchDevice = !matchDevice
        store.matchDeviceRatio = matchDevice
        refresh()
    }

    companion object {
        fun factory(
            app: Application,
            kind: FeedKind,
            deviceAspect: Float,
            matchDeviceInitially: Boolean,
            store: LocalStore,
        ) = viewModelFactory {
            initializer {
                FeedViewModel(app, kind, deviceAspect, matchDeviceInitially, store)
            }
        }
    }
}

class SearchViewModel(
    app: Application,
    private val deviceAspect: Float,
) : PagedViewModel(app) {

    private val store = LocalStore(app)

    var query by mutableStateOf("")
        private set
    var includeWords by mutableStateOf("")
        private set
    var excludeWords by mutableStateOf("")
        private set
    var categories by mutableStateOf(setOf("general", "anime", "people"))
        private set
    /** 分级三选：SFW 默认开；Sketchy 可选；NSFW 需要登录 + API key */
    var includeSfw by mutableStateOf(true)
        private set
    var includeSketchy by mutableStateOf(false)
        private set
    var includeNsfw by mutableStateOf(false)
        private set
    var nsfwAllowed by mutableStateOf(false)
        private set
    private var accountApiKey = ""
    var sorting by mutableStateOf("date_added")
        private set
    var order by mutableStateOf("desc")
        private set
    var topRange by mutableStateOf("1M")
        private set
    var atleast by mutableStateOf("")
        private set
    var customWidth by mutableStateOf("")
        private set
    var customHeight by mutableStateOf("")
        private set
    /** device / any / portrait / landscape / square，默认按本机比例 */
    var orientation by mutableStateOf("device")
        private set
    var ratios by mutableStateOf(setOf<String>())
        private set
    var colors by mutableStateOf(setOf<String>())
        private set
    /** 从「收藏常用标签」里选的标签，会拼成 +tag */
    var tagInclude by mutableStateOf(setOf<String>())
        private set
    /** 本地筛选：最小文件大小（MB），0 表示关闭 */
    var minFileSizeMb by mutableStateOf(0)
        private set
    /** 本地筛选：只看最近 N 天，0 表示关闭 */
    var maxAgeDays by mutableStateOf(0)
        private set

    var searchHistory by mutableStateOf(store.searchHistory())
        private set

    init {
        refresh()
    }

    val categoriesParam: String
        get() = buildString {
            append(if (categories.contains("general")) '1' else '0')
            append(if (categories.contains("anime")) '1' else '0')
            append(if (categories.contains("people")) '1' else '0')
        }

    /** wallhaven purity 位掩码：1=SFW、2=Sketchy、4=NSFW，对应字符串 100/010/001。 */
    private val purityParam: String
        get() = buildString {
            append(if (includeSfw) '1' else '0')
            append(if (includeSketchy) '1' else '0')
            append(if (includeNsfw && nsfwAllowed) '1' else '0')
        }

    val deviceLabel: String
        get() = DeviceRatio.nearestLabel(deviceAspect)

    private val ratioParam: String
        get() = when (orientation) {
            "device" -> DeviceRatio.filterFor(deviceAspect)
            "portrait" -> PORTRAIT_RATIOS
            "landscape" -> LANDSCAPE_RATIOS
            "square" -> "1x1"
            else -> ratios.joinToString(",")
        }

    private val colorsParam: String
        get() = colors.joinToString(",")

    private val atleastParam: String
        get() {
            val w = customWidth.toIntOrNull()
            val h = customHeight.toIntOrNull()
            return if (w != null && h != null && w > 0 && h > 0) "${w}x${h}" else atleast
        }

    /** wallhaven 搜索语法：+ 词表示必须包含，- 词表示排除。 */
    private val queryParam: String
        get() = buildString {
            tagInclude.forEach { append("+").append(it).append(' ') }
            words(includeWords).forEach { append("+").append(it).append(' ') }
            words(excludeWords).forEach { append("-").append(it).append(' ') }
            if (query.isNotBlank()) append(query.trim())
        }.trim()

    val activeFilterCount: Int
        get() = listOf(
            !(includeSfw && !includeSketchy && (!includeNsfw || !nsfwAllowed)),
            sorting != "date_added",
            includeWords.isNotBlank(),
            excludeWords.isNotBlank(),
            tagInclude.isNotEmpty(),
            atleast.isNotBlank() || (customWidth.isNotBlank() && customHeight.isNotBlank()),
            orientation != "device",
            ratios.isNotEmpty(),
            colors.isNotEmpty(),
            minFileSizeMb > 0,
            maxAgeDays > 0,
        ).count { it }

    /** 给界面用的一行摘要，让用户知道现在到底筛了什么。 */
    val summaryText: String
        get() = buildList {
            tagInclude.forEach { add("+$it") }
            if (includeWords.isNotBlank()) add(words(includeWords).joinToString(" ") { "+$it" })
            if (excludeWords.isNotBlank()) add(words(excludeWords).joinToString(" ") { "-$it" })
            if (sorting != "date_added") {
                add(
                    when (sorting) {
                        "toplist" -> "热门"
                        "relevance" -> "相关度"
                        "random" -> "随机"
                        "views" -> "浏览最多"
                        "favorites" -> "收藏最多"
                        else -> sorting
                    },
                )
            }
            when (orientation) {
                "device" -> add("本机 $deviceLabel")
                "portrait" -> add("竖屏")
                "landscape" -> add("横屏")
                "square" -> add("方形")
            }
            if (orientation == "any" && ratios.isNotEmpty()) {
                add(ratios.joinToString(",") { it.replace("x", ":") })
            }
            val custom = if (customWidth.isNotBlank() && customHeight.isNotBlank()) {
                "${customWidth}×${customHeight}"
            } else {
                null
            }
            when {
                custom != null -> add("≥$custom")
                atleast.isNotBlank() -> add("≥$atleast")
            }
            if (colors.isNotEmpty()) add("${colors.size} 种主色")
            if (minFileSizeMb > 0) add("≥${minFileSizeMb}MB")
            if (maxAgeDays > 0) add("近${maxAgeDays}天")
            val levels = buildList {
                if (includeSfw) add("SFW")
                if (includeSketchy) add("Sketchy")
                if (includeNsfw && nsfwAllowed) add("NSFW")
            }
            if (levels.size < 3) add("仅" + levels.joinToString("/"))
        }.joinToString(" · ")

    override fun arrange(data: List<Wallpaper>, page: Int): List<Wallpaper> {
        if (minFileSizeMb <= 0 && maxAgeDays <= 0) return data
        val minBytes = minFileSizeMb * 1024L * 1024L
        return data.filter { wallpaper ->
            val sizeOk = minFileSizeMb <= 0 || wallpaper.fileSize >= minBytes
            val ageOk = maxAgeDays <= 0 || (daysSince(wallpaper.createdAt)?.let { it <= maxAgeDays } ?: true)
            sizeOk && ageOk
        }
    }

    override suspend fun fetchPage(page: Int, seed: String?): SearchResponse = WallhavenApi.search(
        page = page,
        sorting = sorting,
        order = order,
        query = queryParam,
        categories = categoriesParam,
        purity = purityParam,
        atleast = atleastParam,
        ratios = ratioParam,
        colors = colorsParam,
        topRange = topRange,
        seed = seed,
        apiKey = if (includeNsfw && nsfwAllowed) accountApiKey else "",
    )

    fun submit(newQuery: String? = null) {
        if (newQuery != null) query = newQuery.trim()
        if (query.isNotBlank()) {
            store.addSearchHistory(query)
            searchHistory = store.searchHistory()
        }
        refresh()
    }

    fun clearSearchHistory() {
        store.clearSearchHistory()
        searchHistory = store.searchHistory()
    }

    fun toggleTagInclude(tag: String) {
        val next = tagInclude.toMutableSet()
        if (!next.remove(tag)) next.add(tag)
        tagInclude = next
        refresh()
    }

    fun applyAdvanced(include: String, exclude: String, width: String, height: String) {
        includeWords = include.trim()
        excludeWords = exclude.trim()
        customWidth = width.filter { it.isDigit() }.take(5)
        customHeight = height.filter { it.isDigit() }.take(5)
        if (customWidth.isNotBlank() && customHeight.isNotBlank()) atleast = ""
        refresh()
    }

    fun clearColors() {
        colors = emptySet()
        refresh()
    }

    fun toggleCategory(name: String) {
        val next = categories.toMutableSet()
        if (!next.remove(name)) next.add(name)
        if (next.isEmpty()) next.add(name)
        categories = next
        refresh()
    }

    /** 三级筛选：至少保留一级，不能全关。 */
    private fun applyPurity(sfw: Boolean, sketchy: Boolean, nsfw: Boolean) {
        val nsfwEffective = nsfw && nsfwAllowed
        if (!sfw && !sketchy && !nsfwEffective) return
        if (includeSfw == sfw && includeSketchy == sketchy && includeNsfw == nsfwEffective) return
        includeSfw = sfw
        includeSketchy = sketchy
        includeNsfw = nsfwEffective
        refresh()
    }

    fun updateSfw(value: Boolean) = applyPurity(value, includeSketchy, includeNsfw)

    fun updateSketchy(value: Boolean) = applyPurity(includeSfw, value, includeNsfw)

    /** 由界面把账号状态同步进来：只有登录且拿到 API key 才允许开 NSFW。 */
    fun updateAccount(loggedIn: Boolean, apiKey: String) {
        accountApiKey = apiKey
        val allowed = loggedIn && apiKey.isNotBlank()
        if (allowed == nsfwAllowed) {
            nsfwAllowed = allowed
            return
        }
        nsfwAllowed = allowed
        if (!allowed && includeNsfw) {
            includeNsfw = false
            if (!includeSfw && !includeSketchy) includeSfw = true
            refresh()
        }
    }

    fun updateNsfw(value: Boolean) {
        if (!nsfwAllowed) return
        applyPurity(includeSfw, includeSketchy, value)
    }

    fun updateSorting(value: String) {
        sorting = value
        refresh()
    }

    fun updateOrder(value: String) {
        order = value
        refresh()
    }

    fun updateTopRange(value: String) {
        topRange = value
        refresh()
    }

    fun updateAtleast(value: String) {
        atleast = value
        if (value.isNotBlank()) {
            customWidth = ""
            customHeight = ""
        }
        refresh()
    }

    fun updateCustomResolution(width: String, height: String) {
        customWidth = width.filter { it.isDigit() }.take(5)
        customHeight = height.filter { it.isDigit() }.take(5)
        if (customWidth.isNotBlank() && customHeight.isNotBlank()) atleast = ""
        refresh()
    }

    fun updateOrientation(value: String) {
        orientation = value
        if (value != "any") ratios = emptySet()
        refresh()
    }
    fun toggleRatio(value: String) {
        orientation = "any"
        val next = ratios.toMutableSet()
        if (!next.remove(value)) next.add(value)
        ratios = next
        refresh()
    }

    fun toggleColor(value: String) {
        val next = colors.toMutableSet()
        if (!next.remove(value)) next.add(value)
        colors = next
        refresh()
    }

    fun updateMinFileSize(mb: Int) {
        minFileSizeMb = mb
        refresh()
    }

    fun updateMaxAge(days: Int) {
        maxAgeDays = days
        refresh()
    }

    fun resetFilters() {
        includeWords = ""
        excludeWords = ""
        includeSfw = true
        includeSketchy = false
        includeNsfw = false
        sorting = "date_added"
        order = "desc"
        topRange = "1M"
        atleast = ""
        customWidth = ""
        customHeight = ""
        orientation = "device"
        ratios = emptySet()
        colors = emptySet()
        tagInclude = emptySet()
        minFileSizeMb = 0
        maxAgeDays = 0
        refresh()
    }

    private fun words(raw: String): List<String> =
        raw.split(',', '，', ' ', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    companion object {
        private const val PORTRAIT_RATIOS =
            "9x16,10x16,9x18,9x19,9x20,9x21,9x22,9x24,3x4,2x3,3x5"
        private const val LANDSCAPE_RATIOS = "16x9,16x10,21x9,32x9,4x3,3x2,2x1"

        fun factory(app: Application, deviceAspect: Float) = viewModelFactory {
            initializer { SearchViewModel(app, deviceAspect) }
        }
    }
}

class DetailViewModel(
    app: Application,
    private val wallpaperId: String,
) : AndroidViewModel(app) {

    private val store = LocalStore(app)

    var item by mutableStateOf<Wallpaper?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        isLoading = true
        error = null
        viewModelScope.launch {
            try {
                item = WallhavenApi.wallpaper(wallpaperId)
            } catch (e: Exception) {
                val cached = store.findLocally(wallpaperId)
                if (cached != null) {
                    AppLog.warn("Detail", "详情 $wallpaperId 联网失败，改用本地缓存：${e.friendlyMessage()}")
                    item = cached
                } else {
                    AppLog.error("Detail", "详情 $wallpaperId 加载失败：${e.friendlyMessage()}", e)
                    error = e.friendlyMessage()
                }
            } finally {
                isLoading = false
            }
        }
    }

    companion object {
        fun factory(app: Application, id: String) = viewModelFactory {
            initializer { DetailViewModel(app, id) }
        }
    }
}

/**
 * 「更多类似」。
 *
 * 官网的按钮指向 /search?q=like:<id>，但该查询对 API 客户端会被 Cloudflare 拦（403，
 * 带账号 key 也一样），所以这里用 API 支持的等效策略：
 * 用这张图的标签（+tag）+ 主色 + 相关度排序来找相似内容；标签太多时逐级放宽，
 * 保证不会因为标签组合太窄而空手而归。
 */
class SimilarViewModel(
    app: Application,
    private val seedId: String,
) : PagedViewModel(app) {

    /** 界面副标题用：当前是基于什么在找（例如 "+skull +digital art · 主色"）。 */
    var basis by mutableStateOf("")
        private set

    /** 短摘要，例如「4 个标签 + 主色」 */
    var summary by mutableStateOf("")
        private set

    private var chosenQuery: String? = null
    private var chosenColor: String = ""
    private var purity = "100"
    private var apiKey = ""

    /** 探测阶段拿到的那一页，直接当作第 1 页用，省一次请求。 */
    private var preparedFirstPage: SearchResponse? = null

    init {
        refresh()
    }

    override fun arrange(data: List<Wallpaper>, page: Int): List<Wallpaper> =
        data.filterNot { it.id == seedId }

    override suspend fun fetchPage(page: Int, seed: String?): SearchResponse {
        if (page == 1) {
            preparedFirstPage?.let {
                preparedFirstPage = null
                if (chosenQuery != null) return it
            }
            prepare()
            preparedFirstPage?.let {
                preparedFirstPage = null
                return it
            }
        }
        val query = chosenQuery ?: return SearchResponse()
        return WallhavenApi.search(
            page = page,
            sorting = "relevance",
            order = "desc",
            query = query,
            colors = chosenColor,
            purity = purity,
            apiKey = apiKey,
        )
    }

    private suspend fun prepare() {
        val wallpaper = runCatching { WallhavenApi.wallpaper(seedId) }.getOrNull()
        purity = when (wallpaper?.purity) {
            "nsfw" -> "111"
            "sketchy" -> "110"
            else -> "100"
        }
        apiKey = if (purity == "111") ApiKeyStore.key else ""

        val tags = wallpaper?.tags.orEmpty().map { it.name }.filter { it.isNotBlank() }
        val color = wallpaper?.colors?.firstOrNull().orEmpty().removePrefix("#")

        val attempts = buildList {
            if (tags.size >= 3) add(tags.take(4).joinToString(" ") { "+$it" } to color)
            if (tags.size >= 2) add(tags.take(3).joinToString(" ") { "+$it" } to "")
            if (tags.isNotEmpty()) add("+" + tags.first() to "")
        }
        if (attempts.isEmpty()) {
            chosenQuery = null
            basis = "这张图没有标签"
            return
        }

        var lastResponse: SearchResponse? = null
        for ((query, attemptColor) in attempts) {
            val response = runCatching {
                WallhavenApi.search(
                    page = 1,
                    sorting = "relevance",
                    order = "desc",
                    query = query,
                    colors = attemptColor,
                    purity = purity,
                    apiKey = apiKey,
                )
            }.getOrNull() ?: continue
            chosenQuery = query
            chosenColor = attemptColor
            basis = describe(query, attemptColor)
            val tagCount = query.split(" ").count { it.startsWith("+") }
            summary = if (attemptColor.isNotBlank()) "$tagCount 个标签 + 主色" else "$tagCount 个标签"
            lastResponse = response
            if (response.meta.total >= 12) break
        }
        preparedFirstPage = lastResponse
    }

    private fun describe(query: String, color: String): String {
        val suffix = if (color.isNotBlank()) " · 主色 #$color" else ""
        return "$query$suffix"
    }

    companion object {
        fun factory(app: Application, id: String) = viewModelFactory {
            initializer { SimilarViewModel(app, id) }
        }
    }
}

/** 收藏 / 历史：全局共享一份，任何页面改动都会立刻反映到其它页面。 */
class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LocalStore(app)

    var favorites by mutableStateOf<List<Wallpaper>>(emptyList())
        private set
    var history by mutableStateOf<List<Wallpaper>>(emptyList())
        private set
    var favoriteIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** 收藏里出现频率最高的 10 个标签 */
    var favoriteTagStats by mutableStateOf<List<TagStat>>(emptyList())
        private set
    var tagsRefreshing by mutableStateOf(false)
        private set

    /** 收藏页是否隐藏 Sketchy / NSFW 卡片（默认开启） */
    var hideRestricted by mutableStateOf(store.hideRestrictedInFavorites)
        private set

    fun toggleHideRestricted() {
        hideRestricted = !hideRestricted
        store.hideRestrictedInFavorites = hideRestricted
    }

    /** 收藏/云端收藏列表的滚动位置，返回时用来复位。 */
    var localScrollIndex: Int = 0
        private set
    var localScrollOffset: Int = 0
        private set
    var cloudScrollIndex: Int = 0
        private set
    var cloudScrollOffset: Int = 0
        private set

    fun rememberLocalScroll(index: Int, offset: Int) {
        localScrollIndex = index
        localScrollOffset = offset
    }

    fun rememberCloudScroll(index: Int, offset: Int) {
        cloudScrollIndex = index
        cloudScrollOffset = offset
    }

    init {
        sync()
    }

    fun sync() {
        favorites = store.favorites()
        history = store.history()
        favoriteIds = favorites.mapTo(HashSet()) { it.id }
        favoriteTagStats = computeTagStats(favorites)
    }

    private fun computeTagStats(list: List<Wallpaper>): List<TagStat> {
        val counts = HashMap<String, Int>()
        list.forEach { wallpaper ->
            wallpaper.tags.orEmpty()
                .map { it.name.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()
                .forEach { tag -> counts[tag] = (counts[tag] ?: 0) + 1 }
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(10)
            .map { TagStat(it.key, it.value) }
    }

    fun favoritesWithTags(): Int = favorites.count { !it.tags.isNullOrEmpty() }

    /** 详情页拿到完整数据后，顺手刷新收藏里那条记录的标签。 */
    fun updateStoredFavorite(wallpaper: Wallpaper) {
        if (!favoriteIds.contains(wallpaper.id)) return
        if (!wallpaper.tags.isNullOrEmpty()) {
            store.updateFavorite(wallpaper)
            sync()
        }
    }

    /** 收藏里有些是从列表直接收藏的，没有标签；这里按需补齐（每次最多 60 张，3 并发）。 */
    fun refreshFavoriteTags() {
        if (tagsRefreshing) return
        val missing = favorites.filter { it.tags.isNullOrEmpty() }
        if (missing.isEmpty()) return
        tagsRefreshing = true
        viewModelScope.launch {
            val targets = missing.take(60)
            targets.chunked(3).forEach { chunk ->
                chunk.map { wallpaper ->
                    async(Dispatchers.IO) {
                        runCatching { WallhavenApi.wallpaper(wallpaper.id) }.getOrNull()
                    }
                }.awaitAll().forEach { fresh ->
                    if (fresh != null) store.updateFavorite(fresh)
                }
            }
            sync()
            tagsRefreshing = false
            AppLog.info("Favorites", "已补全 ${targets.size} 张收藏的标签")
        }
    }

    fun toggleFavorite(wallpaper: Wallpaper) {
        store.toggleFavorite(wallpaper)
        sync()
    }

    fun removeFavorite(id: String) {
        store.removeFavorite(id)
        sync()
    }

    fun clearFavorites() {
        store.clearFavorites()
        sync()
    }

    fun recordHistory(wallpaper: Wallpaper) {
        store.addHistory(wallpaper)
        history = store.history()
    }

    fun clearHistory() {
        store.clearHistory()
        history = store.history()
    }

    companion object {
        fun factory(app: Application) = viewModelFactory {
            initializer { LibraryViewModel(app) }
        }
    }
}
