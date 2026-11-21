package com.wallhaven.browser.ui.vm

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wallhaven.browser.data.AccountStore
import com.wallhaven.browser.data.AddFavoriteResult
import com.wallhaven.browser.data.ApiKeyStore
import com.wallhaven.browser.data.CloudflareBlockedException
import com.wallhaven.browser.data.SessionExpiredException
import com.wallhaven.browser.data.WallhavenSession
import com.wallhaven.browser.data.Wallpaper
import com.wallhaven.browser.util.AppLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AccountViewModel(app: Application) : AndroidViewModel(app) {

    private val store = AccountStore(app)
    private val session = WallhavenSession(store)

    var username by mutableStateOf(store.username)
        private set
    var loggedIn by mutableStateOf(store.loggedIn)
        private set

    /** 账号 API key（登录后从设置页读取，用于 NSFW 内容） */
    var apiKey by mutableStateOf(store.apiKey)
        private set

    var cloudFavorites by mutableStateOf<List<Wallpaper>>(emptyList())
        private set
    var cloudLoading by mutableStateOf(false)
        private set
    var cloudError by mutableStateOf<String?>(null)
        private set
    var cloudProgress by mutableStateOf("")
        private set
    var cloudSkipped by mutableStateOf(0)
        private set
    var cloudTruncated by mutableStateOf(false)
        private set

    var syncing by mutableStateOf(false)
        private set
    var syncProgress by mutableStateOf("")
        private set
    var syncResult by mutableStateOf<String?>(null)
        private set

    init {
        ApiKeyStore.update(store.apiKey)
        // 启动时校验一次会话：过期的清掉，缺用户名的补上
        if (loggedIn) {
            viewModelScope.launch {
                val name = runCatching { session.checkLogin() }.getOrNull()
                when {
                    name == null -> {
                        AppLog.warn("Account", "本地会话已失效，已自动登出")
                        loggedIn = false
                        store.loggedIn = false
                    }
                    name.isNotBlank() -> {
                        if (name != username) {
                            username = name
                            store.username = name
                        }
                    }
                }
                if (name != null) refreshApiKey()
            }
        }
    }

    private fun refreshApiKey() {
        viewModelScope.launch {
            val key = runCatching { session.fetchApiKey() }.getOrNull()
            if (!key.isNullOrBlank()) {
                store.apiKey = key
                apiKey = key
                ApiKeyStore.update(key)
            }
        }
    }

    /** WebView 登录成功后把 Cookie 交给这里校验并保存。 */
    fun loginWithCookies(cookieHeader: String) {        if (cookieHeader.isBlank()) return
        viewModelScope.launch {
            store.cookies = cookieHeader
            val name = runCatching { session.checkLogin() }.getOrNull()
            if (name != null) {
                store.username = name
                store.loggedIn = true
                username = name
                loggedIn = true
                AppLog.info("Account", "登录成功：${name.ifBlank { "(未读到用户名)" }}")
                refreshApiKey()
                refreshCloud()
            } else {
                store.cookies = ""
                store.loggedIn = false
                loggedIn = false
                AppLog.warn("Account", "拿到 Cookie 但校验未通过（可能没登录成功）")
            }
        }
    }

    fun logout() {
        store.clear()
        username = ""
        loggedIn = false
        apiKey = ""
        ApiKeyStore.update("")
        cloudFavorites = emptyList()
        cloudError = null
        cloudTruncated = false
        cloudSkipped = 0
        syncResult = null
        AppLog.info("Account", "已退出账号")
    }

    fun refreshCloud() {
        if (!loggedIn) {
            cloudError = "请先登录 wallhaven 账号"
            return
        }
        if (cloudLoading) return
        cloudLoading = true
        cloudError = null
        cloudProgress = "正在读取云端收藏…"
        viewModelScope.launch {
            try {
                val result = session.fetchFavorites { cloudProgress = it }
                cloudFavorites = result.items
                cloudSkipped = result.skipped
                cloudTruncated = result.truncated
            } catch (e: SessionExpiredException) {
                loggedIn = false
                store.loggedIn = false
                cloudError = "登录已失效，请重新登录"
            } catch (e: CloudflareBlockedException) {
                cloudError = "wallhaven 返回了人机验证页，稍后再试或重新登录"
            } catch (e: Exception) {
                cloudError = "读取云端收藏失败：${e.message ?: e.javaClass.simpleName}"
            } finally {
                cloudLoading = false
                cloudProgress = ""
            }
        }
    }

    /** 把本地收藏里「账号中还没有」的图，逐张加入云端收藏。 */
    fun syncToCloud(localFavorites: List<Wallpaper>) {
        if (!loggedIn) {
            syncResult = "请先登录 wallhaven 账号"
            return
        }
        if (syncing) return
        val cloudIds = cloudFavorites.mapTo(HashSet()) { it.id }
        val todo = localFavorites.filterNot { cloudIds.contains(it.id) }
        if (localFavorites.isEmpty()) {
            syncResult = "本地还没有收藏"
            return
        }
        if (todo.isEmpty()) {
            syncResult = "本地收藏都已经在账号里了"
            return
        }

        syncing = true
        syncResult = null
        viewModelScope.launch {
            var success = 0
            var failed = 0
            var needLogin = false
            for ((index, item) in todo.withIndex()) {
                syncProgress = "同步 ${index + 1}/${todo.size}"
                when (session.addFavorite(item.id)) {
                    AddFavoriteResult.SUCCESS -> success++
                    AddFavoriteResult.NEED_LOGIN -> {
                        needLogin = true
                        break
                    }
                    AddFavoriteResult.FAILED -> failed++
                }
                delay(900)
            }
            syncing = false
            syncProgress = ""
            syncResult = when {
                needLogin -> "登录已失效，请重新登录（成功 $success 张）"
                failed == 0 -> "已把 $success 张同步到账号"
                success == 0 -> "同步失败，可能是网页接口有变；详细原因见「运行日志」"
                else -> "同步完成：成功 $success 张，失败 $failed 张（原因见运行日志）"
            }
            store.lastSyncAt = System.currentTimeMillis()
            AppLog.info("Account", "同步结束：成功 $success，失败 $failed")
            if (success > 0) refreshCloud()
        }
    }

    companion object {
        fun factory(app: Application) = viewModelFactory {
            initializer { AccountViewModel(app) }
        }
    }
}
