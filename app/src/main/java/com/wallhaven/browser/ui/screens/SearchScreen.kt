@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wallhaven.browser.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wallhaven.browser.data.TagStat
import com.wallhaven.browser.ui.components.FeedGrid
import com.wallhaven.browser.ui.components.IosButton
import com.wallhaven.browser.ui.components.IosButtonStyle
import com.wallhaven.browser.ui.components.LargeTitleHeader
import com.wallhaven.browser.ui.components.PillChip
import com.wallhaven.browser.ui.components.tabBarContentPadding
import com.wallhaven.browser.ui.theme.Pal
import com.wallhaven.browser.ui.vm.AccountViewModel
import com.wallhaven.browser.ui.vm.LibraryViewModel
import com.wallhaven.browser.ui.vm.SearchViewModel
import com.wallhaven.browser.util.DeviceProfile

private val categoryOptions = listOf(
    "general" to "综合",
    "anime" to "动漫",
    "people" to "人物",
)

private val sortingOptions = listOf(
    "date_added" to "最新上传",
    "toplist" to "热门排行",
    "relevance" to "关键词相关",
    "random" to "随机",
    "views" to "浏览最多",
    "favorites" to "收藏最多",
)

private val topRangeOptions = listOf(
    "1d" to "1 天",
    "3d" to "3 天",
    "1w" to "1 周",
    "1M" to "1 个月",
    "3M" to "3 个月",
    "6M" to "6 个月",
    "1y" to "1 年",
)

private val orientationOptions = listOf(
    "any" to "全部",
    "portrait" to "竖屏",
    "landscape" to "横屏",
    "square" to "方形",
)
private val ratioOptions = listOf(
    "16x9", "16x10", "21x9", "9x16", "10x16", "9x20", "1x1", "3x4",
)

private val resolutionOptions = listOf(
    "" to "不限",
    "1920x1080" to "1080P",
    "2560x1440" to "2K",
    "3840x2160" to "4K",
)

private val fileSizeOptions = listOf(
    0 to "关闭",
    1 to "≥1 MB",
    2 to "≥2 MB",
    5 to "≥5 MB",
    10 to "≥10 MB",
)

private val ageOptions = listOf(
    0 to "不限",
    1 to "近 1 天",
    7 to "近 1 周",
    30 to "近 1 月",
    365 to "近 1 年",
)

private val colorPalette = listOf(
    "660000", "990000", "cc0000", "663300", "996600", "cc9900",
    "336600", "669900", "99cc00", "003366", "336699", "0066cc",
    "663366", "993399", "9900cc", "006666", "009999", "00cccc",
    "000000", "333333", "666666", "999999", "cccccc", "ffffff",
)

@Composable
fun SearchScreen(
    library: LibraryViewModel,
    account: AccountViewModel,
    onOpenWallpaper: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val configuration = LocalConfiguration.current
    val profile = remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        val metrics = context.resources.displayMetrics
        DeviceProfile.fromPixels(metrics.widthPixels, metrics.heightPixels)
    }
    val vm: SearchViewModel = viewModel(
        key = "search-${if (profile.isPortrait) "p" else "l"}",
        factory = SearchViewModel.factory(app, profile.aspect),
    )
    val focusManager = LocalFocusManager.current
    var text by rememberSaveable { mutableStateOf(vm.query) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var initialQueryApplied by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialQuery) {
        // 只处理一次：从详情页返回时不要重新提交，否则列表会被清空、滚动位置回到顶部
        if (initialQuery.isNotBlank() && !initialQueryApplied) {
            initialQueryApplied = true
            text = initialQuery
            vm.submit(initialQuery)
        }
    }

    // 登录状态 / API key 变化时同步给搜索逻辑（NSFW 只有登录后可用）
    LaunchedEffect(account.loggedIn, account.apiKey) {
        vm.updateAccount(account.loggedIn, account.apiKey)
    }

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        LargeTitleHeader(
            title = "搜索",
            subtitle = if (vm.total > 0) "找到 ${vm.total} 张" else "关键词、标签都能搜",
        )
        Spacer(Modifier.height(12.dp))
        IosSearchField(
            value = text,
            onValueChange = { newValue ->
                val hadQuery = text.isNotBlank()
                text = newValue
                // 把关键词删空（或点 ×）时立刻回到默认结果，不用再点一次搜索
                if (hadQuery && newValue.isBlank() && vm.query.isNotBlank()) {
                    focusManager.clearFocus()
                    vm.submit("")
                }
            },
            onSearch = {
                focusManager.clearFocus()
                vm.submit(text)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        if (vm.searchHistory.isNotEmpty() && text.isBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "最近",
                    style = MaterialTheme.typography.labelSmall,
                    color = Pal.LabelTertiary,
                )
                vm.searchHistory.take(8).forEach { keyword ->
                    PillChip(
                        text = keyword,
                        selected = false,
                        onClick = {
                            text = keyword
                            focusManager.clearFocus()
                            vm.submit(keyword)
                        },
                        leadingIcon = Icons.Filled.History,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Pal.ChipBackground)
                        .clickable { vm.clearSearchHistory() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "清空搜索历史",
                        tint = Pal.LabelSecondary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            categoryOptions.forEach { (key, label) ->
                PillChip(
                    text = label,
                    selected = vm.categories.contains(key),
                    onClick = { vm.toggleCategory(key) },
                )
            }
            PillChip(
                text = if (vm.activeFilterCount > 0) "高级 ${vm.activeFilterCount}" else "高级筛选",
                selected = vm.activeFilterCount > 0,
                onClick = { showFilters = true },
                leadingIcon = Icons.Filled.Tune,
            )
        }
        Spacer(Modifier.height(6.dp))
        if (vm.summaryText.isNotBlank()) {
            Text(
                text = vm.summaryText,
                style = MaterialTheme.typography.labelSmall,
                color = Pal.LabelTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 4.dp),
            )
        }
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
            emptyText = "没有匹配的壁纸\n换个关键词，或者放宽筛选条件",
        )
    }

    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Pal.Sheet,
            contentColor = Pal.Label,
            dragHandle = { SheetHandle() },
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        ) {
            AdvancedFilterSheet(
                vm = vm,
                account = account,
                favoriteTags = library.favoriteTagStats,
                tagsRefreshing = library.tagsRefreshing,
                onDone = { showFilters = false },
            )
        }
    }
}

@Composable
private fun IosSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(CircleShape)
            .background(Pal.Field)
            .border(0.8.dp, Pal.Hairline, CircleShape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = Pal.LabelTertiary,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Pal.Label),
            cursorBrush = SolidColor(Pal.Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = "搜索标签或关键词，例如 cyberpunk",
                            fontSize = 15.sp,
                            color = Pal.LabelTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Pal.ChipBackground)
                    .clickable { onValueChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "清空",
                    tint = Pal.LabelSecondary,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(
        Modifier
            .padding(top = 10.dp, bottom = 6.dp)
            .size(width = 38.dp, height = 5.dp)
            .clip(CircleShape)
            .background(Pal.Hairline),
    )
}

@Composable
private fun AdvancedFilterSheet(
    vm: SearchViewModel,
    account: AccountViewModel,
    favoriteTags: List<TagStat>,
    tagsRefreshing: Boolean,
    onDone: () -> Unit,
) {
    var includeText by remember { mutableStateOf(vm.includeWords) }
    var excludeText by remember { mutableStateOf(vm.excludeWords) }
    var customWidth by remember { mutableStateOf(vm.customWidth) }
    var customHeight by remember { mutableStateOf(vm.customHeight) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("高级筛选", style = MaterialTheme.typography.titleLarge, color = Pal.Label)
                Text(
                    text = if (vm.activeFilterCount > 0) "已启用 ${vm.activeFilterCount} 项" else "比官网更多的组合条件",
                    style = MaterialTheme.typography.labelSmall,
                    color = Pal.LabelTertiary,
                )
            }
            IosButton(
                text = "重置",
                onClick = { vm.resetFilters() },
                style = IosButtonStyle.Plain,
                height = 36.dp,
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("关键词语法")
        SheetField(
            value = includeText,
            onValueChange = { includeText = it },
            placeholder = "必须包含，例如 cyberpunk, neon（逗号或空格分隔）",
        )
        Spacer(Modifier.height(8.dp))
        SheetField(
            value = excludeText,
            onValueChange = { excludeText = it },
            placeholder = "必须排除，例如 text, watermark",
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "会转换成 wallhaven 的 +标签 / -标签 语法，点「完成」生效",
            style = MaterialTheme.typography.labelSmall,
            color = Pal.LabelTertiary,
        )

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = Pal.Separator)
        Spacer(Modifier.height(16.dp))

        SectionTitle("收藏常用标签（点选 = 必须包含）")
        if (favoriteTags.isEmpty()) {
            Text(
                text = if (tagsRefreshing) {
                    "正在从收藏里补全标签…"
                } else {
                    "收藏里还没有标签：打开几张已收藏壁纸的详情页会自动补全"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Pal.LabelTertiary,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                favoriteTags.forEach { stat ->
                    PillChip(
                        text = "#${stat.name} · ${stat.count}",
                        selected = vm.tagInclude.contains(stat.name),
                        onClick = { vm.toggleTagInclude(stat.name) },
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = Pal.Separator)
        Spacer(Modifier.height(16.dp))

        SectionTitle("排序方式")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sortingOptions.forEach { (value, label) ->
                PillChip(text = label, selected = vm.sorting == value, onClick = { vm.updateSorting(value) })
            }
        }

        if (vm.sorting == "toplist") {
            Spacer(Modifier.height(14.dp))
            SectionTitle("统计时间范围")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                topRangeOptions.forEach { (value, label) ->
                    PillChip(text = label, selected = vm.topRange == value, onClick = { vm.updateTopRange(value) })
                }
            }
        }

        if (vm.sorting != "random") {
            Spacer(Modifier.height(14.dp))
            SectionTitle("排序方向")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("desc" to "降序", "asc" to "升序").forEach { (value, label) ->
                    PillChip(text = label, selected = vm.order == value, onClick = { vm.updateOrder(value) })
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = Pal.Separator)
        Spacer(Modifier.height(16.dp))

        SectionTitle("画面方向")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillChip(
                text = "本机 ${vm.deviceLabel}",
                selected = vm.orientation == "device",
                onClick = { vm.updateOrientation("device") },
            )
            orientationOptions.forEach { (value, label) ->
                PillChip(text = label, selected = vm.orientation == value, onClick = { vm.updateOrientation(value) })
            }
        }

        if (vm.orientation == "any") {
            Spacer(Modifier.height(14.dp))
            SectionTitle("画面比例（可多选）")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ratioOptions.forEach { ratio ->
                    PillChip(
                        text = ratio.replace("x", ":"),
                        selected = vm.ratios.contains(ratio),
                        onClick = { vm.toggleRatio(ratio) },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("最低分辨率")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            resolutionOptions.forEach { (value, label) ->
                PillChip(
                    text = label,
                    selected = vm.atleast == value && vm.customWidth.isBlank(),
                    onClick = { vm.updateAtleast(value) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumberField(
                value = customWidth,
                onValueChange = { customWidth = it.filter { c -> c.isDigit() }.take(5) },
                placeholder = "宽",
                modifier = Modifier.weight(1f),
            )
            Text(" × ", color = Pal.LabelTertiary, fontSize = 13.sp)
            NumberField(
                value = customHeight,
                onValueChange = { customHeight = it.filter { c -> c.isDigit() }.take(5) },
                placeholder = "高",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "留空则用上面的预设；填了宽高就以自定义为准",
            style = MaterialTheme.typography.labelSmall,
            color = Pal.LabelTertiary,
        )

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = Pal.Separator)
        Spacer(Modifier.height(16.dp))

        SectionTitle("主色调（可多选）")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .border(
                        width = if (vm.colors.isEmpty()) 2.5.dp else 1.dp,
                        color = if (vm.colors.isEmpty()) Pal.Accent else Pal.ChipBorder,
                        shape = CircleShape,
                    )
                    .clickable { vm.clearColors() },
                contentAlignment = Alignment.Center,
            ) {
                Text("×", fontSize = 13.sp, color = Pal.LabelSecondary)
            }
            colorPalette.forEach { hex ->
                val selected = vm.colors.contains(hex)
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(parseColor(hex), CircleShape)
                        .border(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = when {
                                selected -> Pal.AccentSoft
                                isLightColor(hex) -> Color(0x40000000)
                                else -> Pal.ChipBorder
                            },
                            shape = CircleShape,
                        )
                        .clickable { vm.toggleColor(hex) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = Pal.Separator)
        Spacer(Modifier.height(16.dp))

        SectionTitle("本地筛选（只作用于已加载的图片）")
        Text("文件大小", style = MaterialTheme.typography.labelSmall, color = Pal.LabelTertiary)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fileSizeOptions.forEach { (mb, label) ->
                PillChip(text = label, selected = vm.minFileSizeMb == mb, onClick = { vm.updateMinFileSize(mb) })
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("上传时间", style = MaterialTheme.typography.labelSmall, color = Pal.LabelTertiary)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ageOptions.forEach { (days, label) ->
                PillChip(text = label, selected = vm.maxAgeDays == days, onClick = { vm.updateMaxAge(days) })
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("分级筛选（至少开一级）", style = MaterialTheme.typography.labelSmall, color = Pal.LabelTertiary)
        Spacer(Modifier.height(10.dp))
        PuritySwitchRow(
            title = "SFW 安全",
            subtitle = "正常内容，默认开启",
            checked = vm.includeSfw,
            accent = Pal.Accent,
            onCheckedChange = { vm.updateSfw(it) },
        )
        Spacer(Modifier.height(12.dp))
        PuritySwitchRow(
            title = "Sketchy 敏感",
            subtitle = "擦边内容，不需要账号",
            checked = vm.includeSketchy,
            accent = Color(0xFFE0A800),
            onCheckedChange = { vm.updateSketchy(it) },
        )

        // NSFW 只有登录 wallhaven 账号后才出现（需要账号 API key 才能拉到内容）
        if (account.loggedIn) {
            Spacer(Modifier.height(12.dp))
            PuritySwitchRow(
                title = "NSFW 成人",
                subtitle = when {
                    vm.nsfwAllowed -> "成人内容，用你账号的 API key 请求，仅本机可见"
                    account.apiKey.isBlank() -> "没读取到账号 API key，重新登录试试"
                    else -> "正在读取账号 API key…"
                },
                checked = vm.includeNsfw,
                enabled = vm.nsfwAllowed,
                accent = Color(0xFFE23B3B),
                onCheckedChange = { vm.updateNsfw(it) },
            )
        }

        Spacer(Modifier.height(22.dp))
        IosButton(
            text = "完成",
            onClick = {
                vm.applyAdvanced(includeText, excludeText, customWidth, customHeight)
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
            height = 50.dp,
        )
    }
}

@Composable
private fun PuritySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Pal.Label)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Pal.LabelTertiary,
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                uncheckedThumbColor = Color(0xFF8E8E93),
                uncheckedTrackColor = Pal.SwitchTrack,
            ),
        )
    }
}

@Composable
private fun SheetField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, fontSize = 13.sp, color = Pal.LabelTertiary) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Pal.Label),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Pal.Accent,
            unfocusedBorderColor = Pal.Hairline,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            cursorColor = Pal.Accent,
        ),
    )
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(placeholder, fontSize = 13.sp, color = Pal.LabelTertiary) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Pal.Label),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Pal.Accent,
            unfocusedBorderColor = Pal.Hairline,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            cursorColor = Pal.Accent,
        ),
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = Pal.LabelTertiary,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

private fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor("#$hex"))
} catch (e: Exception) {
    Color.Gray
}

private fun isLightColor(hex: String): Boolean = try {
    val c = android.graphics.Color.parseColor("#$hex")
    val luminance = (0.299 * android.graphics.Color.red(c) +
        0.587 * android.graphics.Color.green(c) +
        0.114 * android.graphics.Color.blue(c)) / 255.0
    luminance > 0.8
} catch (e: Exception) {
    false
}
