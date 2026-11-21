package com.wallhaven.browser.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wallhaven.browser.data.Wallpaper
import com.wallhaven.browser.ui.theme.AppShapes
import com.wallhaven.browser.ui.theme.Pal

@Composable
fun WallpaperCard(
    wallpaper: Wallpaper,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val previews = LocalPreviewSettings.current
    val fullPreview = previews?.fullPreview ?: true
    val ratio = if (fullPreview) {
        wallpaper.cardAspect
    } else {
        wallpaper.aspect.coerceIn(0.62f, 1.85f)
    }
    val imageUrl = if (fullPreview) {
        wallpaper.thumbs.original.ifBlank { wallpaper.thumbUrl }
    } else {
        wallpaper.thumbUrl
    }
    Box(
        modifier = modifier
            .pressScale(interaction, pressedScale = 0.965f, dampingRatio = 0.5f, stiffness = 950f)
            .aspectRatio(ratio)
            .clip(AppShapes.card)
            .background(Pal.SurfaceAlt)
            .iosClickable(interaction, true, null, onClick),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(180)
                .build(),
            contentDescription = wallpaper.displayResolution,
            contentScale = if (fullPreview) ContentScale.Fit else ContentScale.Crop,
            placeholder = ColorPainter(Color(0xFF1B1B22)),
            error = ColorPainter(Color(0xFF26262E)),
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.5f to Color.Transparent,
                        1f to Color(0xCC000000),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .border(
                    0.7.dp,
                    Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x0DFFFFFF))),
                    AppShapes.card,
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 11.dp, end = 11.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = wallpaper.displayResolution,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xF2FFFFFF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = wallpaper.categoryLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xB3FFFFFF),
            )
        }
        if (wallpaper.purity == "sketchy" || wallpaper.purity == "nsfw") {
            PurityBadge(
                text = if (wallpaper.purity == "nsfw") "NSFW" else "SKETCHY",
                purity = wallpaper.purity,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )
        }
        if (onToggleFavorite != null) {
            FavoriteBubble(
                isFavorite = isFavorite,
                onToggle = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp),
            )
        }
    }
}

/**
 * 分级角标：Sketchy 用黄色、NSFW 用红色，和官网一致；SFW 用主题色。
 */
@Composable
fun PurityBadge(
    text: String,
    purity: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 9.sp,
) {
    val nsfw = purity == "nsfw"
    val sketchy = purity == "sketchy"
    val background = when {
        nsfw -> Color(0xFFE23B3B)
        sketchy -> Color(0xFFF2C11B)
        else -> Pal.Accent.copy(alpha = 0.18f)
    }
    val foreground = when {
        nsfw -> Color.White
        sketchy -> Color(0xFF3A2C00)
        else -> Pal.AccentSoft
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = foreground,
            letterSpacing = 0.5.sp,
            maxLines = 1,
        )
    }
}

/** 卡片右上角的收藏按钮：按下缩小、收藏成功“弹一下”。 */
@Composable
private fun FavoriteBubble(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    val pop = remember { Animatable(1f) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(isFavorite) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        if (isFavorite) {
            pop.snapTo(0.7f)
            pop.animateTo(1.28f, spring(dampingRatio = 0.42f, stiffness = 900f))
            pop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 600f))
        } else {
            pop.snapTo(1.2f)
            pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 700f))
        }
    }

    Box(
        modifier = modifier
            .pressScale(interaction, pressedScale = 0.82f, dampingRatio = 0.4f, stiffness = 900f)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .size(30.dp)
            .clip(CircleShape)
            .background(Color(0x66000000))
            .border(0.7.dp, Color(0x33FFFFFF), CircleShape)
            .iosClickable(
                interactionSource = interaction,
                haptics = { haptics.light() },
                onClick = onToggle,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (isFavorite) "取消收藏" else "收藏",
            tint = if (isFavorite) Pal.Pink else Color.White,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
fun FeedGrid(
    items: List<Wallpaper>,
    isLoading: Boolean,
    error: String?,
    favoriteIds: Set<String>,
    onItemClick: (Wallpaper) -> Unit,
    onToggleFavorite: (Wallpaper) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    contentBottomPadding: Dp = 24.dp,
    emptyText: String = "这里还没有内容",
    showFavorite: Boolean = true,
    initialIndex: Int = 0,
    initialOffset: Int = 0,
    onScrollChanged: ((Int, Int) -> Unit)? = null,
) {
    if (items.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                isLoading -> LoadingState()
                error != null -> ErrorPanel(error, onRetry)
                else -> EmptyState(emptyText)
            }
        }
        return
    }

    val gridState = rememberLazyStaggeredGridState(
        initialFirstVisibleItemIndex = initialIndex,
        initialFirstVisibleItemScrollOffset = initialOffset,
    )
    if (onScrollChanged != null) {
        LaunchedEffect(gridState) {
            snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
                .collect { (index, offset) -> onScrollChanged(index, offset) }
        }
    }
    val shouldLoadMore by remember {
        androidx.compose.runtime.derivedStateOf {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(shouldLoadMore, items.size) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyVerticalStaggeredGrid(
        state = gridState,
        columns = StaggeredGridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = contentBottomPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp,
        modifier = modifier.fillMaxSize(),
    ) {
        items(items, key = { it.id }) { wallpaper ->
            WallpaperCard(
                wallpaper = wallpaper,
                isFavorite = favoriteIds.contains(wallpaper.id),
                onClick = { onItemClick(wallpaper) },
                onToggleFavorite = if (showFavorite) {
                    { onToggleFavorite(wallpaper) }
                } else {
                    null
                },
            )
        }
        if (isLoading) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Pal.Accent)
                    Spacer(Modifier.width(10.dp))
                    Text("加载更多…", style = MaterialTheme.typography.bodySmall, color = Pal.LabelSecondary)
                }
            }
        }
        if (error != null) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onRetry) { Text("重试") }
                }
            }
        }
    }
}

@Composable
fun LoadingState() {
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Pal.Accent, strokeWidth = 2.5.dp)
    }
}

@Composable
fun EmptyState(text: String) {
    Column(
        modifier = Modifier.padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Pal.LabelSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )
    }
}

@Composable
fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(Pal.ChipBackground)
                .border(0.8.dp, Pal.Hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.WifiOff,
                contentDescription = null,
                tint = Pal.LabelSecondary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Pal.LabelSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(18.dp))
        IosButton(text = "重试", onClick = onRetry, height = 44.dp)    }
}

/* ---------- iOS 设置页风格的列表 ---------- */

@Composable
fun ListGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Pal.Surface)
            .border(0.8.dp, Pal.Separator, RoundedCornerShape(22.dp)),
        content = content,
    )
}

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = Pal.Accent,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = rememberHaptics()
    val pressedBackground by animateColorAsState(
        targetValue = if (pressed) Pal.ChipBackground else Color.Transparent,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 1600f),
        label = "rowPress",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(pressedBackground)
            .defaultMinSize(minHeight = 52.dp)
            .then(
                if (onClick != null) {
                    Modifier
                        // 列表行不参与键盘焦点：避免残留的 Tab/Enter 误触发（例如刚登录返回时误点「退出登录」）
                        .focusProperties { canFocus = false }
                        .iosClickable(
                            interactionSource = interaction,
                            haptics = { haptics.light() },
                            onClick = onClick,
                        )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(iconTint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(13.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Pal.Label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Pal.LabelSecondary,
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = Pal.LabelSecondary,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Pal.LabelTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

@Composable
fun ListDivider(startPadding: Dp = 16.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = startPadding)
            .height(0.7.dp)
            .background(Pal.Separator),
    )
}

@Composable
fun SectionCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Pal.LabelTertiary,
        modifier = modifier.padding(start = 20.dp, bottom = 8.dp, top = 4.dp),
    )
}
