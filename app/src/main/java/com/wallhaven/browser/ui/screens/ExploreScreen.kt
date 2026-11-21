package com.wallhaven.browser.ui.screens

import android.app.Application
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wallhaven.browser.data.LocalStore
import com.wallhaven.browser.ui.components.FeedGrid
import com.wallhaven.browser.ui.components.GlassIconButton
import com.wallhaven.browser.ui.components.LargeTitleHeader
import com.wallhaven.browser.ui.components.PillChip
import com.wallhaven.browser.ui.components.tabBarContentPadding
import com.wallhaven.browser.ui.vm.FeedKind
import com.wallhaven.browser.ui.vm.FeedViewModel
import com.wallhaven.browser.ui.vm.LibraryViewModel
import com.wallhaven.browser.util.DeviceProfile

@Composable
fun ExploreScreen(
    library: LibraryViewModel,
    onOpenWallpaper: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val configuration = LocalConfiguration.current
    val profile = remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        val metrics = context.resources.displayMetrics
        DeviceProfile.fromPixels(metrics.widthPixels, metrics.heightPixels)
    }
    val store = remember(app) { LocalStore(app) }
    var kind by rememberSaveable { mutableStateOf(FeedKind.Top) }

    val vm: FeedViewModel = viewModel(
        key = "feed-${kind.name}-${if (profile.isPortrait) "p" else "l"}",
        factory = FeedViewModel.factory(
            app = app,
            kind = kind,
            deviceAspect = profile.aspect,
            matchDeviceInitially = store.matchDeviceRatio,
            store = store,
        ),
    )

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        LargeTitleHeader(
            title = kind.title,
            subtitle = buildString {
                if (vm.total > 0) {
                    append("已加载 ${vm.items.size} / ${vm.total} 张")
                } else {
                    append("wallhaven.cc")
                }
                if (vm.matchDevice) append(" · ${profile.label} 适配中")
            },
            actions = {
                GlassIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "刷新",
                    onClick = { vm.refresh() },
                )
            },
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillChip(
                text = if (vm.matchDevice) "适配本机 ${profile.label}" else "全部比例",
                selected = vm.matchDevice,
                onClick = { vm.toggleMatchDevice() },
                leadingIcon = Icons.Filled.Smartphone,
            )
            FeedKind.entries.forEach { entry ->
                PillChip(
                    text = entry.title,
                    selected = entry == kind,
                    onClick = { kind = entry },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        FeedGrid(
            items = vm.items,
            isLoading = vm.isLoading,
            error = vm.error,
            favoriteIds = library.favoriteIds,
            onItemClick = { onOpenWallpaper(it.id) },
            onToggleFavorite = { library.toggleFavorite(it) },
            onRetry = { vm.retry() },
            onLoadMore = { vm.loadMore() },
            contentBottomPadding = tabBarContentPadding(),
            modifier = Modifier.weight(1f),
            initialIndex = vm.savedScrollIndex,
            initialOffset = vm.savedScrollOffset,
            onScrollChanged = { index, offset -> vm.rememberScroll(index, offset) },
            emptyText = "这个榜单据暂时没有适配 ${profile.label} 的壁纸\n点上面的「全部比例」看看全部内容",
        )
    }
}
