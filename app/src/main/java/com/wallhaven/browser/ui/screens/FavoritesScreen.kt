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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
                    "$who · ${account.cloudFavorites.size} 张"
                } else {
                    "登录后可读取 wallhaven 账号里的收藏"
                }
            } else {
                "共 ${library.favorites.size} 张 · 只保存在这台手机上"
            },
            actions = {
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
                        onClick = { library.clearFavorites() },
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
                onOpenLogin = onOpenLogin,
                onOpenWallpaper = onOpenWallpaper,
                modifier = Modifier.weight(1f),
            )
        } else {
            LocalFavoritesContent(
                library = library,
                onOpenWallpaper = onOpenWallpaper,
                onOpenTag = onOpenTag,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LocalFavoritesContent(
    library: LibraryViewModel,
    onOpenWallpaper: (String) -> Unit,
    onOpenTag: (String) -> Unit,
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
            items = library.favorites,
            isLoading = false,
            error = null,
            favoriteIds = library.favoriteIds,
            onItemClick = { onOpenWallpaper(it.id) },
            onToggleFavorite = { library.toggleFavorite(it) },
            onRetry = {},
            onLoadMore = {},
            contentBottomPadding = tabBarContentPadding(),
            modifier = Modifier.weight(1f),
            initialIndex = library.localScrollIndex,
            initialOffset = library.localScrollOffset,
            onScrollChanged = { index, offset -> library.rememberLocalScroll(index, offset) },
            emptyText = "还没有收藏任何壁纸\n在榜单或搜索里点卡片右上角的小心心就能收藏",
        )
    }
}

@Composable
private fun CloudFavoritesContent(
    library: LibraryViewModel,
    account: AccountViewModel,
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
                items = account.cloudFavorites,
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
                emptyText = "账号里还没有收藏",
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
