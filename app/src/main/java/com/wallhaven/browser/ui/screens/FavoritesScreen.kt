package com.wallhaven.browser.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wallhaven.browser.data.Wallpaper
import com.wallhaven.browser.ui.components.EmptyState
import com.wallhaven.browser.ui.components.ErrorPanel
import com.wallhaven.browser.ui.components.FeedGrid
import com.wallhaven.browser.ui.components.GlassIconButton
import com.wallhaven.browser.ui.components.IosButton
import com.wallhaven.browser.ui.components.LargeTitleHeader
import com.wallhaven.browser.ui.components.PillChip
import com.wallhaven.browser.ui.components.iosClickable
import com.wallhaven.browser.ui.components.pressScale
import com.wallhaven.browser.ui.components.tabBarContentPadding
import com.wallhaven.browser.ui.theme.Pal
import com.wallhaven.browser.ui.vm.AccountViewModel
import com.wallhaven.browser.ui.vm.LibraryViewModel
import com.wallhaven.browser.util.formatDate

@Composable
fun FavoritesScreen(
    library: LibraryViewModel,
    account: AccountViewModel,
    onOpenWallpaper: (String) -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCloud by rememberSaveable { mutableStateOf(false) }
    var deleteStep by remember { mutableIntStateOf(0) }
    var pendingUnfavorite by remember { mutableStateOf<Wallpaper?>(null) }
    var unfavoriteStep by remember { mutableIntStateOf(0) }

    val visibleFavorites = remember(library.favorites, library.hideRestricted) {
        if (library.hideRestricted) library.favorites.filter { it.purity == "sfw" } else library.favorites
    }
    val hiddenLocalCount = library.favorites.size - visibleFavorites.size
    val visibleCloud = remember(account.cloudFavorites, library.hideRestricted) {
        if (library.hideRestricted) {
            account.cloudFavorites.filter { it.purity == "sfw" }
        } else {
            account.cloudFavorites
        }
    }
    val hiddenCloudCount = account.cloudFavorites.size - visibleCloud.size

    LaunchedEffect(Unit) {
        library.refreshFavoriteTags()
    }
    LaunchedEffect(showCloud) {
        if (showCloud && account.loggedIn && account.cloudFavorites.isEmpty() && !account.cloudLoading) {
            account.refreshCloud()
        }
    }

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        LargeTitleHeader(
            title = "收藏",
            subtitle = if (showCloud) {
                if (account.loggedIn) {
                    val who = if (account.username.isBlank()) "账号" else "@${account.username}"
                    val hidden = if (hiddenCloudCount > 0) " · 已隐藏 $hiddenCloudCount 张限制级" else ""
                    "$who · ${visibleCloud.size} 张$hidden"
                } else {
                    "登录后可读取 wallhaven 账号里的收藏"
                }
            } else {
                val hidden = if (hiddenLocalCount > 0) " · 已隐藏 $hiddenLocalCount 张限制级" else ""
                "共 ${visibleFavorites.size} 张 · 只保存在这台手机上$hidden"
            },
            actions = {
                GlassIconButton(
                    icon = if (library.hideRestricted) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (library.hideRestricted) "显示限制级内容" else "隐藏限制级内容",
                    onClick = { library.toggleHideRestricted() },
                )
                Spacer(Modifier.width(8.dp))
                if (showCloud) {
                    if (account.loggedIn) {
                        GlassIconButton(
                            icon = Icons.Filled.Refresh,
                            contentDescription = "刷新云端收藏",
                            onClick = { account.refreshCloud() },
                        )
                        Spacer(Modifier.width(8.dp))
                        GlassIconButton(
                            icon = Icons.Filled.CloudUpload,
                            contentDescription = "同步本地收藏到账号",
                            onClick = { account.syncToCloud(library.favorites) },
                        )
                    }
                } else if (library.favorites.isNotEmpty()) {
                    GlassIconButton(
                        icon = Icons.Filled.Delete,
                        contentDescription = "清空收藏",
                        onClick = { deleteStep = 1 },
                    )
                }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillChip(
                text = "本地 ${library.favorites.size}",
                selected = !showCloud,
                onClick = { showCloud = false },
            )
            PillChip(
                text = if (account.loggedIn) "账号 ${account.cloudFavorites.size}" else "账号",
                selected = showCloud,
                onClick = { showCloud = true },
            )
        }

        if (showCloud) {
            CloudFavoritesContent(
                library = library,
                account = account,
                items = visibleCloud,
                onOpenLogin = onOpenLogin,
                onOpenWallpaper = onOpenWallpaper,
                modifier = Modifier.weight(1f),
            )
        } else {
            LocalFavoritesContent(
                library = library,
                items = visibleFavorites,
                onOpenWallpaper = onOpenWallpaper,
                onOpenTag = onOpenTag,
                onUnfavorite = { pendingUnfavorite = it },
                modifier = Modifier.weight(1f),
            )
        }
    }

    // 取消收藏：两次确认
    pendingUnfavorite?.let { wallpaper ->
        AlertDialog(
            onDismissRequest = {
                pendingUnfavorite = null
                unfavoriteStep = 0
            },
            containerColor = Pal.SurfaceAlt,
            titleContentColor = Pal.Label,
            textContentColor = Pal.LabelSecondary,
            title = { Text(if (unfavoriteStep == 0) "取消收藏？" else "再确认一次") },
            text = {
                Text(
                    if (unfavoriteStep == 0) {
                        "「${wallpaper.displayResolution}」会从本地收藏里移除。"
                    } else {
                        "确定不再收藏这张壁纸吗？只影响这台手机上的收藏。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (unfavoriteStep == 0) {
                            unfavoriteStep = 1
                        } else {
                            library.toggleFavorite(wallpaper)
                            pendingUnfavorite = null
                            unfavoriteStep = 0
                        }
                    },
                ) {
                    Text(
                        text = if (unfavoriteStep == 0) "继续" else "取消收藏",
                        color = Pal.Pink,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingUnfavorite = null
                        unfavoriteStep = 0
                    },
                ) {
                    Text("保留", color = Pal.LabelSecondary)
                }
            },
        )
    }

    // 清空本地收藏：三次确认
    if (deleteStep > 0) {
        val total = library.favorites.size
        AlertDialog(
            onDismissRequest = { deleteStep = 0 },
            containerColor = Pal.SurfaceAlt,
            titleContentColor = Pal.Label,
            textContentColor = Pal.LabelSecondary,
            title = {
                Text(
                    when (deleteStep) {
                        1 -> "清空本地收藏？"
                        2 -> "再确认一次"
                        else -> "最后确认"
                    },
                )
            },
            text = {
                Text(
                    when (deleteStep) {
                        1 -> "会从这台手机上移除全部 $total 张收藏。账号里的收藏不受影响。"
                        2 -> "这个操作不能撤销，删掉之后只能重新一张张收藏。确定继续吗？"
                        else -> "真的要删除全部 $total 张收藏吗？"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (deleteStep >= 3) {
                            library.clearFavorites()
                            deleteStep = 0
                        } else {
                            deleteStep += 1
                        }
                    },
                ) {
                    Text(
                        text = if (deleteStep >= 3) "全部删除" else "继续",
                        color = if (deleteStep >= 3) Pal.Pink else Pal.Accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteStep = 0 }) {
                    Text("取消", color = Pal.LabelSecondary)
                }
            },
        )
    }
}

@Composable
private fun LocalFavoritesContent(
    library: LibraryViewModel,
    items: List<Wallpaper>,
    onOpenWallpaper: (String) -> Unit,
    onOpenTag: (String) -> Unit,
    onUnfavorite: (Wallpaper) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (library.favoriteTagStats.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "常用标签",
                    style = MaterialTheme.typography.labelMedium,
                    color = Pal.LabelSecondary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${library.favoritesWithTags()}/${library.favorites.size} 张已识别 · 点击搜索",
                    style = MaterialTheme.typography.labelSmall,
                    color = Pal.LabelTertiary,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                library.favoriteTagStats.forEach { stat ->
                    PillChip(
                        text = "#${stat.name} · ${stat.count}",
                        selected = false,
                        onClick = { onOpenTag(stat.name) },
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        FeedGrid(
            items = items,
            isLoading = false,
            error = null,
            favoriteIds = library.favoriteIds,
            onItemClick = { onOpenWallpaper(it.id) },
            onToggleFavorite = { onUnfavorite(it) },
            onRetry = {},
            onLoadMore = {},
            contentBottomPadding = tabBarContentPadding(),
            modifier = Modifier.weight(1f),
            initialIndex = library.localScrollIndex,
            initialOffset = library.localScrollOffset,
            onScrollChanged = { index, offset -> library.rememberLocalScroll(index, offset) },
            emptyText = if (library.favorites.isNotEmpty() && items.isEmpty()) {
                "当前设置隐藏了 Sketchy / NSFW 内容\n点右上角的眼睛按钮就能显示"
            } else {
                "还没有收藏任何壁纸\n在榜单或搜索里点卡片右上角的小心心就能收藏"
            },
        )
    }
}

@Composable
private fun CloudFavoritesContent(
    library: LibraryViewModel,
    account: AccountViewModel,
    items: List<Wallpaper>,
    onOpenLogin: () -> Unit,
    onOpenWallpaper: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (!account.loggedIn) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState("登录 wallhaven 账号后\n就能读取账号里的收藏，并把本地收藏同步上去")
                    Spacer(Modifier.height(18.dp))
                    IosButton(text = "登录 wallhaven", onClick = onOpenLogin)
                }
            }
            return
        }

        val status = when {
            account.syncing -> "同步中 ${account.syncProgress}"
            account.cloudProgress.isNotBlank() -> account.cloudProgress
            account.syncResult != null -> account.syncResult.orEmpty()
            account.cloudTruncated -> "云端收藏较多，只加载了前 300 张"
            account.cloudSkipped > 0 -> "${account.cloudSkipped} 张读取失败（可能无权限或已删除）"
            else -> ""
        }
        if (status.isNotBlank()) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = Pal.LabelSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }

        val error = account.cloudError
        if (error != null && account.cloudFavorites.isEmpty() && !account.cloudLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorPanel(message = error, onRetry = { account.refreshCloud() })
            }
        } else {
            FeedGrid(
                items = items,
                isLoading = account.cloudLoading,
                error = if (account.cloudFavorites.isEmpty()) null else error,
                favoriteIds = emptySet(),
                onItemClick = { onOpenWallpaper(it.id) },
                onToggleFavorite = {},
                onRetry = { account.refreshCloud() },
                onLoadMore = {},
                contentBottomPadding = tabBarContentPadding(),
                modifier = Modifier.weight(1f),
                showFavorite = false,
                initialIndex = library.cloudScrollIndex,
                initialOffset = library.cloudScrollOffset,
                onScrollChanged = { index, offset -> library.rememberCloudScroll(index, offset) },
                emptyText = if (account.cloudFavorites.isNotEmpty() && items.isEmpty()) {
                    "当前设置隐藏了 Sketchy / NSFW 内容\n点右上角的眼睛按钮就能显示"
                } else {
                    "账号里还没有收藏"
                },
            )
        }
    }
}

@Composable
fun HistoryScreen(
    library: LibraryViewModel,
    onBack: () -> Unit,
    onOpenWallpaper: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        LargeTitleHeader(
            title = "浏览历史",
            subtitle = "共 ${library.history.size} 条 · 最多保留 200 条",
            onBack = onBack,
            actions = {
                if (library.history.isNotEmpty()) {
                    GlassIconButton(
                        icon = Icons.Filled.Delete,
                        contentDescription = "清空历史",
                        onClick = { library.clearHistory() },
                    )
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        if (library.history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState("还没有浏览记录\n看过的壁纸会按时间出现在这里")
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 24.dp + tabBarContentPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(library.history, key = { it.id }) { wallpaper ->
                    HistoryRow(wallpaper = wallpaper, onClick = { onOpenWallpaper(wallpaper.id) })
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(wallpaper: Wallpaper, onClick: () -> Unit) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = if (pressed) Pal.SurfaceAlt else Pal.Surface,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 1800f),
        label = "historyRowBackground",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction, pressedScale = 0.985f, dampingRatio = 0.5f, stiffness = 1200f)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .border(0.8.dp, Pal.Hairline, RoundedCornerShape(18.dp))
            .iosClickable(interaction, true, null, onClick)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(wallpaper.thumbUrl).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xFF1B1B22)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = wallpaper.displayResolution,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Pal.Label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${wallpaper.categoryLabel} · ${formatDate(wallpaper.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = Pal.LabelSecondary,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Pal.LabelTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}
