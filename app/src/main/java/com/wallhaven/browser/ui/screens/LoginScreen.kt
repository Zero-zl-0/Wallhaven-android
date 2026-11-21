package com.wallhaven.browser.ui.screens

import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.wallhaven.browser.ui.components.LargeTitleHeader
import com.wallhaven.browser.ui.theme.Pal
import com.wallhaven.browser.ui.vm.AccountViewModel

/** 收起软键盘并清掉 WebView 焦点，避免 IME 的 Enter 冒泡到当前页面。 */
private fun hideKeyboard(context: android.content.Context, webView: WebView) {
    runCatching { webView.clearFocus() }
    runCatching {
        val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(webView.windowToken, 0)
    }
}

/**
 * 用官网真实页面登录：密码只输给 wallhaven，App 只拿会话 Cookie。
 * 登录成功后自动返回，并去拉取云端收藏。
 */
@Composable
fun LoginScreen(account: AccountViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var progress by remember { mutableFloatStateOf(0f) }
    var loginCheckFired by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = false
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress / 100f
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    progress = 1f
                    if (loginCheckFired) return
                    val isLoginPage = url.contains("/login") || url.contains("/auth/") ||
                        url.contains("/register")
                    if (!isLoginPage) {
                        val cookie = CookieManager.getInstance()
                            .getCookie("https://wallhaven.cc")
                            .orEmpty()
                        if (cookie.contains("wallhaven_session")) {
                            loginCheckFired = true
                            account.loginWithCookies(cookie)
                        }
                    }
                }
            }

            loadUrl("https://wallhaven.cc/login")
        }
    }

    // 登录成功后先收起键盘并清掉 WebView 焦点，否则软键盘残留的 Enter 会被派发给返回后的页面
    LaunchedEffect(account.loggedIn) {
        if (account.loggedIn) {
            hideKeyboard(context, webView)
            onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            hideKeyboard(context, webView)
            runCatching {
                webView.stopLoading()
                webView.destroy()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        LargeTitleHeader(
            title = "登录 wallhaven",
            subtitle = "密码只输入给官网，App 不保存密码",
            onBack = onBack,
        )
        if (progress < 1f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Pal.Accent,
                trackColor = Pal.Separator,
            )
        }
        Text(
            text = "登录后会自动返回并读取账号收藏；如果页面一直转圈，请先确认手机 VPN / 代理可用。",
            style = MaterialTheme.typography.labelSmall,
            color = Pal.LabelTertiary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (account.cloudLoading) {
            Text(
                text = account.cloudProgress.ifBlank { "正在读取云端收藏…" },
                style = MaterialTheme.typography.labelSmall,
                color = Pal.LabelSecondary,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Spacer(Modifier.height(4.dp))
    }
}
