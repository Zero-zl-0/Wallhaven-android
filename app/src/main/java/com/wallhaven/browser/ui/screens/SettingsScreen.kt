package com.wallhaven.browser.ui.screens

import android.widget.Toast
import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.imageLoader
import com.wallhaven.browser.data.LocalStore
import com.wallhaven.browser.ui.components.LargeTitleHeader
import com.wallhaven.browser.ui.components.ListDivider
import com.wallhaven.browser.ui.components.ListGroup
import com.wallhaven.browser.ui.components.ListRow
import com.wallhaven.browser.ui.components.LocalPreviewSettings
import com.wallhaven.browser.ui.components.SectionCaption
import com.wallhaven.browser.ui.components.UpdateDialog
import com.wallhaven.browser.ui.components.tabBarContentPadding
import com.wallhaven.browser.ui.theme.Pal
import com.wallhaven.browser.ui.vm.AccountViewModel
import com.wallhaven.browser.ui.vm.LibraryViewModel
import com.wallhaven.browser.ui.vm.UpdateViewModel
import com.wallhaven.browser.util.ApkInstaller
import com.wallhaven.browser.util.AppLog
import com.wallhaven.browser.util.DeviceProfile
import com.wallhaven.browser.util.openUrl

/** 作者主页：App 内所有「找作者」的入口都用它。 */
private const val AUTHOR_GITHUB = "https://github.com/Zero-zl-0"

@Composable
fun SettingsScreen(
    library: LibraryViewModel,
    account: AccountViewModel,
    onOpenHistory: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var confirmLogout by remember { mutableStateOf(false) }
    var showCredits by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }

    val app = context.applicationContext as Application
    val updateVm: UpdateViewModel = viewModel(factory = UpdateViewModel.factory(app))

    // 从「允许安装未知应用」设置页回来时，自动继续安装
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) updateVm.refreshInstallPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val configuration = LocalConfiguration.current
    val store = remember(context) { LocalStore(context) }
    val profile = remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        val metrics = context.resources.displayMetrics
        DeviceProfile.fromPixels(metrics.widthPixels, metrics.heightPixels)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        LargeTitleHeader(title = "设置", subtitle = "所有数据都留在本机，不收集、不上传")
        Spacer(Modifier.height(14.dp))

        SectionCaption("账号")
        ListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            if (account.loggedIn) {
                ListRow(
                    title = "已登录 wallhaven",
                    subtitle = if (account.cloudFavorites.isEmpty()) {
                        "登录有效 · 可把本地收藏同步上去"
                    } else {
                        "云端收藏 ${account.cloudFavorites.size} 张 · 可同步本地收藏"
                    },
                    value = if (account.username.isBlank()) "已登录" else "@${account.username}",
                    icon = Icons.Filled.Person,
                    iconTint = Pal.Green,
                )
                ListDivider(startPadding = 59.dp)
                ListRow(
                    title = "退出登录",
                    subtitle = "只清除本机会话，不影响网站上的收藏",
                    icon = Icons.Filled.PowerSettingsNew,
                    iconTint = Pal.Pink,
                    onClick = { confirmLogout = true },
                )
            } else {
                ListRow(
                    title = "登录 wallhaven 账号",
                    subtitle = "登录后可读取账号收藏，并把本地收藏同步上去",
                    icon = Icons.Filled.Person,
                    iconTint = Pal.Accent,
                    showChevron = true,
                    onClick = onOpenLogin,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionCaption("显示")
        ListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            ListRow(
                title = "本机屏幕比例",
                subtitle = if (store.matchDeviceRatio) {
                    if (profile.isPortrait) "首页优先展示竖屏壁纸（更贴合手机屏幕）" else "首页优先展示横屏壁纸"
                } else {
                    "适配已关闭，首页显示全部比例"
                },
                value = profile.label,
                icon = Icons.Filled.Smartphone,
                iconTint = Pal.Green,
            )
            ListDivider(startPadding = 59.dp)
            val previewSettings = LocalPreviewSettings.current
            val fullPreview = previewSettings?.fullPreview ?: true
            ListRow(
                title = "卡片显示完整壁纸",
                subtitle = if (fullPreview) {
                    "按壁纸原比例显示（竖图不会被裁，但预览清晰度略低）"
                } else {
                    "用 16:9 缩略图填满卡片（更清晰，但竖图会被裁切）"
                },
                icon = Icons.Filled.AspectRatio,
                iconTint = Pal.Purple,
                trailing = {
                    Switch(
                        checked = fullPreview,
                        onCheckedChange = { previewSettings?.updateFullPreview(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Pal.Accent,
                            uncheckedThumbColor = Color(0xFF8E8E93),
                            uncheckedTrackColor = Pal.SwitchTrack,
                        ),
                    )
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionCaption("图库")
        ListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            ListRow(
                title = "浏览历史",
                value = "${library.history.size} 条",
                icon = Icons.Filled.History,
                iconTint = Pal.Orange,
                showChevron = true,
                onClick = onOpenHistory,
            )
            ListDivider(startPadding = 59.dp)
            ListRow(
                title = "已下载壁纸",
                subtitle = "相册 / Pictures / Wallhaven，可查看和删除",
                icon = Icons.Filled.Image,
                iconTint = Pal.Accent,
                showChevron = true,
                onClick = onOpenDownloads,
            )
            ListDivider(startPadding = 59.dp)
            ListRow(
                title = "清空收藏",
                value = "${library.favorites.size} 张",
                icon = Icons.Filled.DeleteForever,
                iconTint = Pal.Pink,
                onClick = { library.clearFavorites() },
            )
            ListDivider(startPadding = 59.dp)
            ListRow(
                title = "清空浏览历史",
                value = "${library.history.size} 条",
                icon = Icons.Filled.DeleteForever,
                iconTint = Pal.Pink,
                onClick = { library.clearHistory() },
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionCaption("存储与网络")
        ListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            ListRow(
                title = "下载保存位置",
                subtitle = "手机相册 / Pictures / Wallhaven",
                icon = Icons.Filled.Folder,
                iconTint = Pal.Accent,
            )
            ListDivider(startPadding = 59.dp)
            ListRow(
                title = "清理图片缓存",
                subtitle = "释放占用的存储空间",
                icon = Icons.Filled.Image,
                iconTint = Pal.Green,
                onClick = {
                    context.imageLoader.memoryCache?.clear()
                    context.imageLoader.diskCache?.clear()
                    Toast.makeText(context, "图片缓存已清理", Toast.LENGTH_SHORT).show()
                },
            )
            ListDivider(startPadding = 59.dp)
            ListRow(
                title = "打开 wallhaven.cc 官网",
                icon = Icons.Filled.OpenInNew,
                iconTint = Pal.Purple,
                showChevron = true,
                onClick = { openUrl(context, "https://wallhaven.cc") },
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionCaption("诊断")
        ListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            ListRow(
                title = "运行日志",
                subtitle = "记录网络请求、下载和错误，反馈问题用",
                value = "${AppLog.entries.size} 条",
                icon = Icons.Filled.BugReport,
                iconTint = Pal.Purple,
                showChevron = true,
                onClick = onOpenLogs,
            )
            ListDivider(startPadding = 59.dp)
            ListRow(
                title = "复制诊断信息",
                subtitle = "版本、系统、设备、屏幕尺寸",
                icon = Icons.Filled.ContentCopy,
                iconTint = Pal.Accent,
                onClick = {
                    clipboard.setText(AnnotatedString(AppLog.diagnostic(context)))
                    Toast.makeText(context, "诊断信息已复制", Toast.LENGTH_SHORT).show()
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionCaption("关于作者")
        ListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            ListRow(
                title = "作者",
                subtitle = "独立开发者 · 业余时间写的第三方客户端",
                value = "Zero-zl-0",
                icon = Icons.Filled.Person,
                iconTint = Pal.Green,
            )
            ListDivider(startPadding = 59.dp)
            ListRow(
                title = "GitHub 主页",
                subtitle = "github.com/Zero-zl-0",
                icon = Icons.Filled.Code,
                iconTint = Pal.Purple,
                showChevron = true,
                onClick = { openUrl(context, AUTHOR_GITHUB) },
            )
            ListDivider(startPadding = 59.dp)
            InfoRow(
                icon = Icons.Filled.BugReport,
                tint = Pal.Orange,
                title = "遇到问题怎么反馈",
                body = "点上面的 GitHub 主页找到作者，把问题描述加上「运行日志」里的诊断信息一起发过去，定位会快很多。功能建议同样欢迎。",
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionCaption("关于")
        ListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            InfoRow(
                icon = Icons.Filled.Wifi,
                tint = Pal.Accent,
                title = "网络",
                body = "本 App 直连 wallhaven.cc 的公开接口。如果一直提示连不上，请先在手机上打开 VPN 或代理，再点榜单右上角的刷新按钮。",
            )
            ListDivider()
            InfoRow(
                icon = Icons.Filled.Info,
                tint = Pal.LabelSecondary,
                title = "内容与版权",
                body = "个人使用的第三方客户端，与 wallhaven.cc 官方无关。壁纸版权归原作者，下载后请仅作个人桌面使用。",
            )
            ListDivider()
            InfoRow(
                icon = Icons.Filled.Info,
                tint = Pal.LabelSecondary,
                title = "版本",
                body = "Wallhaven 壁纸 v${updateVm.currentVersion}\n作者：Zero-zl-0（github.com/Zero-zl-0）\n数据来源：wallhaven.cc 公开 API v1\n收藏与历史保存在本机，卸载即删除。",
            )
            ListDivider()
            ListRow(
                title = "检查更新",
                subtitle = "当前 v${updateVm.currentVersion} · 从 GitHub 获取最新版",
                icon = Icons.Filled.Refresh,
                iconTint = Pal.Accent,
                showChevron = true,
                onClick = {
                    showUpdate = true
                    updateVm.check()
                },
            )
            ListDivider()
            ListRow(
                title = "开源组件与致谢",
                subtitle = "Kotlin、Compose、OkHttp、Coil、Haze 等",
                icon = Icons.Filled.Code,
                iconTint = Pal.Accent,
                showChevron = true,
                onClick = { showCredits = true },
            )
        }

        Spacer(Modifier.height(22.dp))
        Text(
            text = "Made by Zero-zl-0 · 非官方客户端 · 仅供个人使用",
            style = MaterialTheme.typography.labelSmall,
            color = Pal.LabelTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(tabBarContentPadding()))
    }

    if (showUpdate) {
        UpdateDialog(
            state = updateVm.state,
            currentVersion = updateVm.currentVersion,
            needsInstallPermission = updateVm.needsInstallPermission,
            onDownload = { updateVm.download() },
            onInstall = { updateVm.installNow() },
            onOpenRelease = { url -> openUrl(context, url) },
            onOpenSettings = { ApkInstaller.openInstallPermissionSettings(context) },
            onRetry = { updateVm.check() },
            onDismiss = {
                showUpdate = false
                updateVm.reset()
            },
        )
    }

    if (showCredits) {
        AlertDialog(
            onDismissRequest = { showCredits = false },
            containerColor = Pal.SurfaceAlt,
            titleContentColor = Pal.Label,
            textContentColor = Pal.LabelSecondary,
            title = { Text("开源组件与致谢") },
            text = {
                Text(
                    text = "界面与逻辑：Kotlin + Jetpack Compose\n" +
                        "网络：OkHttp 4\n" +
                        "图片：Coil 2\n" +
                        "JSON：kotlinx.serialization\n" +
                        "毛玻璃：Haze\n\n" +
                        "数据来源：wallhaven.cc 公开 API v1。\n" +
                        "壁纸与标签版权归 wallhaven.cc 及原作者，本 App 仅作个人浏览使用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Pal.LabelSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { showCredits = false }) {
                    Text("知道了", color = Pal.Accent, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            containerColor = Pal.SurfaceAlt,
            titleContentColor = Pal.Label,
            textContentColor = Pal.LabelSecondary,
            title = { Text("退出登录？") },
            text = { Text("会清除本机保存的会话和 API key，网站上的收藏不受影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        account.logout()
                    },
                ) {
                    Text("退出", color = Pal.Pink, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) {
                    Text("取消", color = Pal.LabelSecondary)
                }
            },
        )
    }
}

@Composable
private fun InfoRow(icon: ImageVector, tint: Color, title: String, body: String) {
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(30.dp)
                .padding(top = 1.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Pal.Label,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = Pal.LabelSecondary,
                lineHeight = 19.sp,
            )
        }
    }
}
