package com.wallhaven.browser.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wallhaven.browser.ui.components.FeedGrid
import com.wallhaven.browser.ui.components.LargeTitleHeader
import com.wallhaven.browser.ui.components.tabBarContentPadding
import com.wallhaven.browser.ui.theme.Pal
import com.wallhaven.browser.ui.vm.LibraryViewModel
import com.wallhaven.browser.ui.vm.SimilarViewModel

/**
 * 「更多类似」：官网按钮的等效实现（标签 + 主色 + 相关度）。
 */
@Composable
fun SimilarScreen(
    seedId: String,
    library: LibraryViewModel,
    onBack: () -> Unit,
    onOpenWallpaper: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: SimilarViewModel = viewModel(
        key = "similar-$seedId",
        factory = SimilarViewModel.factory(app, seedId),
    )

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        LargeTitleHeader(
            title = "更多类似",
            subtitle = if (vm.total > 0) {
                "找到 ${vm.total} 张 · 基于 ${vm.summary}"
            } else {
                "按标签和主色找相似内容"
            },
            onBack = onBack,
        )
        Spacer(Modifier.height(8.dp))
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
            emptyText = "没有找到更相似的壁纸\n这张图的标签可能比较独特",
        )
        if (vm.basis.isNotBlank() && vm.items.isNotEmpty()) {
            Text(
                text = "匹配条件：${vm.basis}",
                style = MaterialTheme.typography.labelSmall,
                color = Pal.LabelTertiary,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
            )
        }
    }
}
