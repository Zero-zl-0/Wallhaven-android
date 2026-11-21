@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wallhaven.browser.ui.screens

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.wallhaven.browser.data.Wallpaper
import com.wallhaven.browser.ui.components.GlassIconButton
import com.wallhaven.browser.ui.components.GlassSurface
import com.wallhaven.browser.ui.components.IosButton
import com.wallhaven.browser.ui.components.IosButtonStyle
import com.wallhaven.browser.ui.components.PurityBadge
import com.wallhaven.browser.ui.components.iosClickable
import com.wallhaven.browser.ui.components.pressScale
import com.wallhaven.browser.ui.components.rememberHaptics
import com.wallhaven.browser.ui.theme.Pal
import com.wallhaven.browser.ui.vm.DetailViewModel
import com.wallhaven.browser.ui.vm.LibraryViewModel
import com.wallhaven.browser.util.Downloader
import com.wallhaven.browser.util.DownloadRegistry
import com.wallhaven.browser.util.DownloadStatus
import com.wallhaven.browser.util.formatDate
import com.wallhaven.browser.util.formatFileSize
import com.wallhaven.browser.util.openUrl
import com.wallhaven.browser.util.shareWallpaper
import com.wallhaven.browser.util.toCompact

@Composable
fun DetailScreen(
    wallpaperId: String,
    library: LibraryViewModel,
    apiKey: String,
    onBack: () -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSimilar: (String) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: DetailViewModel = viewModel(
        key = "detail-$wallpaperId",
        factory = DetailViewModel.factory(app, wallpaperId),
    )
    val item = vm.item

    var menuOpen by remember { mutableStateOf(false) }
    var showOriginal by remember(wallpaperId) { mutableStateOf(false) }
    var scale by remember(wallpaperId) { mutableFloatStateOf(1f) }
    var offset by remember(wallpaperId) { mutableStateOf(Offset.Zero) }
    var pendingDownload by remember { mutableStateOf<Wallpaper?>(null) }
    var downloadState by remember(wallpaperId) { mutableStateOf(DownloadUi.IDLE) }
    var downloadProgress by remember(wallpaperId) { mutableFloatStateOf(0f) }
    var activeDownloadId by remember(wallpaperId) { mutableStateOf<Long?>(null) }
    var panelVisible by remember(wallpaperId) { mutableStateOf(true) }
    val haptics = rememberHaptics()
    val scrimAlpha by animateFloatAsState(
        targetValue = if (panelVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "detailScrim",
    )

    fun beginDownload(wallpaper: Wallpaper) {
        val id = Downloader.enqueue(context, wallpaper)
        if (id == null) {
            downloadState = DownloadUi.FAILED
            return
        }
        DownloadRegistry.put(context, wallpaper.id, id, wallpaper.fileName)
        downloadProgress = 0f
        downloadState = DownloadUi.RUNNING
        activeDownloadId = id
    }

    // 进入详情页时恢复下载状态（已完成 / 正在下 / 已失效）
    LaunchedEffect(wallpaperId) {
        val entry = DownloadRegistry.entry(context, wallpaperId)
        if (entry == null) {
            downloadState = DownloadUi.IDLE
            return@LaunchedEffect
        }
        if (entry.done) {
            downloadState = DownloadUi.DONE
            downloadProgress = 1f
            return@LaunchedEffect
        }
        when (Downloader.progressOf(context, entry.downloadId)?.status) {
            DownloadStatus.SUCCESS -> {
                DownloadRegistry.markDone(context, wallpaperId)
                downloadState = DownloadUi.DONE
                downloadProgress = 1f
            }
            DownloadStatus.RUNNING -> {
                downloadState = DownloadUi.RUNNING
                activeDownloadId = entry.downloadId
            }
            else -> {
                DownloadRegistry.remove(context, wallpaperId)
                downloadState = DownloadUi.IDLE
            }
        }
    }

    // 轮询系统下载器的进度
    LaunchedEffect(activeDownloadId) {
        val id = activeDownloadId ?: return@LaunchedEffect
        while (true) {
            val progress = Downloader.progressOf(context, id)
            if (progress == null) {
                downloadState = DownloadUi.IDLE
                break
            }
            when (progress.status) {
                DownloadStatus.RUNNING -> downloadProgress = progress.fraction
                DownloadStatus.SUCCESS -> {
                    downloadProgress = 1f
                    downloadState = DownloadUi.DONE
                    DownloadRegistry.markDone(context, wallpaperId)
                    haptics.light()
                    break
                }
                DownloadStatus.FAILED -> {
                    downloadState = DownloadUi.FAILED
                    DownloadRegistry.remove(context, wallpaperId)
                    break
                }
                DownloadStatus.UNKNOWN -> Unit
            }
            kotlinx.coroutines.delay(600)
        }
        activeDownloadId = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val waiting = pendingDownload
        pendingDownload = null
        if (granted && waiting != null) beginDownload(waiting)
    }

    val startDownload: (Wallpaper) -> Unit = { wallpaper ->
        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingDownload = wallpaper
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            beginDownload(wallpaper)
        }
    }

    // API key 晚到时（刚登录/刚启动）重试一次，避免 NSFW 详情停在 401
    LaunchedEffect(apiKey) {
        if (apiKey.isNotBlank() && vm.item == null) vm.load()
    }

    LaunchedEffect(item?.id) {
        item?.let {
            library.recordHistory(it)
            library.updateStoredFavorite(it)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // 全屏图片层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(wallpaperId) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(1f, 5f)
                        scale = next
                        offset = if (next > 1f) offset + pan else Offset.Zero
                    }
                }
                .pointerInput(wallpaperId) {
                    detectTapGestures(
                        onTap = {
                            panelVisible = !panelVisible
                            haptics.light()
                        },
                        onDoubleTap = {
                            if (scale > 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
                item == null && vm.isLoading -> CircularProgressIndicator(color = Pal.Accent)
                item == null -> GlassSurface(
                    modifier = Modifier.padding(28.dp),
                    shape = RoundedCornerShape(26.dp),
                    blurRadius = 30.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = vm.error ?: "图片加载失败",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Pal.LabelSecondary,
                        )
                        Spacer(Modifier.height(14.dp))
                        NarrowButton(text = "重试", onClick = { vm.load() })
                    }
                }
                else -> {
                    val url = if (showOriginal) {
                        item.path.ifBlank { item.thumbs.original }
                    } else {
                        item.thumbs.original.ifBlank { item.path }
                    }
                    // iOS 相册式背景：同一张图放大模糊铺满屏幕
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(item.thumbUrl).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(48.dp),
                    )
                    Box(Modifier.fillMaxSize().background(Color(0x8C000000)))
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                        contentDescription = item.displayResolution,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y,
                            ),
                        loading = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Pal.Accent)
                            }
                        },
                        error = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("图片加载失败，检查网络后重试", color = Color(0xCCFFFFFF))
                            }
                        },
                    )
                    // 底部渐隐：压暗图片，让玻璃卡片上的文字始终可读；信息面板收起时一起淡出
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(520.dp)
                            .graphicsLayer { alpha = scrimAlpha }
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color(0x66000000),
                                        Color(0xD9000000),
                                        Color(0xF2000000),
                                    ),
                                ),
                            ),
                    )
                    if (scale <= 1.05f) {
                        Text(
                            text = if (panelVisible) "轻点隐藏界面 · 双击放大" else "轻点显示界面 · 双击放大",
                            fontSize = 11.sp,
                            color = Color(0x99FFFFFF),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = 58.dp),
                        )
                    } else {
                        val resetInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 300.dp)
                                .pressScale(resetInteraction, pressedScale = 0.9f, dampingRatio = 0.42f)
                                .clip(CircleShape)
                                .background(Color(0x66000000))
                                .border(0.8.dp, Color(0x33FFFFFF), CircleShape)
                                .iosClickable(resetInteraction, true, null) {
                                    scale = 1f
                                    offset = Offset.Zero
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Text("复位", fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        // 顶部悬浮按钮：和底部信息面板一起显示/隐藏
        AnimatedVisibility(
            visible = panelVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            GlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
                tint = Pal.OverlayButtonIcon,
                backgroundColor = Pal.OverlayButtonBackground,
                borderColor = Pal.OverlayButtonBorder,
            )
            Spacer(Modifier.weight(1f))
            val favorite = item != null && library.favoriteIds.contains(item.id)
            FavoriteTopButton(
                favorite = favorite,
                enabled = item != null,
                onClick = { item?.let { library.toggleFavorite(it) } },
            )
            Spacer(Modifier.width(9.dp))
            GlassIconButton(
                icon = Icons.Filled.Download,
                contentDescription = "下载",
                enabled = item != null,
                tint = Pal.OverlayButtonIcon,
                backgroundColor = Pal.OverlayButtonBackground,
                borderColor = Pal.OverlayButtonBorder,
                onClick = { item?.let(startDownload) },
            )
            Spacer(Modifier.width(9.dp))
            Box {
                GlassIconButton(
                    icon = Icons.Filled.MoreHoriz,
                    contentDescription = "更多",
                    enabled = item != null,
                    tint = Pal.OverlayButtonIcon,
                    backgroundColor = Pal.OverlayButtonBackground,
                    borderColor = Pal.OverlayButtonBorder,
                    onClick = { menuOpen = true },
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = Pal.SurfaceAlt,
                ) {
                    DropdownMenuItem(
                        text = { Text("更多类似") },
                        onClick = {
                            menuOpen = false
                            item?.let { onOpenSimilar(it.id) }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("用浏览器打开原站") },
                        onClick = {
                            menuOpen = false
                            item?.let { openUrl(context, it.shortUrl.ifBlank { it.url }) }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("分享链接") },
                        onClick = {
                            menuOpen = false
                            item?.let { shareWallpaper(context, it) }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (showOriginal) "改看压缩预览图" else "加载原始大图") },
                        onClick = {
                            menuOpen = false
                            showOriginal = !showOriginal
                        },
                    )
                }
            }
            }
        }

        // 底部玻璃信息卡：点图片可收起/展开
        item?.let { wallpaper ->
            AnimatedVisibility(
                visible = panelVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                GlassSurface(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .fillMaxWidth()
                        // 面板本身吃掉点击：点空白处不会穿透到图片层去隐藏界面
                        .pointerInput(Unit) { detectTapGestures { } },
                    shape = RoundedCornerShape(30.dp),
                    blurRadius = 40.dp,
                    tint = Color(0x1AFFFFFF),
                    backgroundColor = Pal.GlassCard,
                    overlay = Pal.GlassCardOverlay,
                ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = wallpaper.displayResolution,
                            style = MaterialTheme.typography.titleLarge,
                            color = Pal.Label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(9.dp))
                        Badge(text = wallpaper.purityLabel, purity = wallpaper.purity)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (showOriginal) "预览" else "原图",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Pal.Accent,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { showOriginal = !showOriginal }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${wallpaper.categoryLabel} · ${formatFileSize(wallpaper.fileSize)} · ${wallpaper.formatLabel} · " +
                            "浏览 ${wallpaper.views.toCompact()} · 收藏 ${wallpaper.favorites.toCompact()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Pal.LabelSecondary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            append("上传于 ${formatDate(wallpaper.createdAt)}")
                            val uploader = wallpaper.uploader?.username
                            if (!uploader.isNullOrBlank()) append(" · 作者 $uploader")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Pal.LabelSecondary,
                    )
                    val tags = wallpaper.tags.orEmpty().filter { it.name.isNotBlank() }
                    if (tags.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "标签",
                                style = MaterialTheme.typography.labelSmall,
                                color = Pal.LabelTertiary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "点击按标签搜索",
                                style = MaterialTheme.typography.labelSmall,
                                color = Pal.LabelTertiary,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "更多类似 ›",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Pal.Accent,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onOpenSimilar(wallpaper.id) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            tags.take(12).forEach { tag ->
                                TagPill(name = tag.name, onClick = { onOpenTag(tag.name) })
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            IosButton(
                                text = when (downloadState) {
                                    DownloadUi.IDLE -> "下载壁纸"
                                    DownloadUi.RUNNING -> "下载中 ${(downloadProgress * 100).toInt()}%"
                                    DownloadUi.DONE -> "下载完成"
                                    DownloadUi.FAILED -> "下载失败，点此重试"
                                },
                                icon = if (downloadState == DownloadUi.DONE) {
                                    Icons.Filled.Check
                                } else {
                                    Icons.Filled.Download
                                },
                                onClick = {
                                    if (downloadState == DownloadUi.DONE) {
                                        onOpenDownloads()
                                    } else {
                                        startDownload(wallpaper)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            // 进度条叠在按钮内部底边，不额外占高度
                            if (downloadState == DownloadUi.RUNNING) {
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth(0.8f)
                                        .padding(bottom = 9.dp)
                                        .height(3.dp)
                                        .clip(CircleShape),
                                    color = Color.White,
                                    trackColor = Color(0x40FFFFFF),
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        IosButton(
                            text = "原站",
                            style = IosButtonStyle.Tinted,
                            onClick = { openUrl(context, wallpaper.shortUrl.ifBlank { wallpaper.url }) },
                        )
                    }
                    }
                }
            }

            // 面板收起后只提示「轻点显示」，不再放按钮（点图片即可恢复）
        }
    }
}

/** 详情页下载按钮的四种状态。 */
private enum class DownloadUi { IDLE, RUNNING, DONE, FAILED }

@Composable
private fun TagPill(name: String, onClick: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val haptics = rememberHaptics()
    Box(
        modifier = Modifier
            .pressScale(interaction, pressedScale = 0.92f, dampingRatio = 0.45f)
            .clip(CircleShape)
            .background(Pal.ChipBackground)
            .border(0.7.dp, Pal.ChipBorder, CircleShape)
            .iosClickable(interaction, true, { haptics.light() }, onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = "#$name",
            fontSize = 11.sp,
            color = Pal.LabelSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun Badge(text: String, purity: String) {
    PurityBadge(text = text, purity = purity, fontSize = 10.sp)
}

@Composable
private fun NarrowButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Pal.Accent, contentColor = Color.White),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 9.dp),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/** 顶部收藏按钮：点一下有回弹放大，收藏状态切换时“弹”一下。 */
@Composable
private fun FavoriteTopButton(
    favorite: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val pop = remember { Animatable(1f) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(favorite) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        if (favorite) {
            pop.snapTo(0.72f)
            pop.animateTo(1.3f, spring(dampingRatio = 0.42f, stiffness = 900f))
            pop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 600f))
        } else {
            pop.snapTo(1.18f)
            pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 700f))
        }
    }

    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
        },
    ) {
        GlassIconButton(
            icon = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (favorite) "取消收藏" else "收藏",
            tint = if (favorite) Pal.Pink else Pal.OverlayButtonIcon,
            enabled = enabled,
            backgroundColor = Pal.OverlayButtonBackground,
            borderColor = Pal.OverlayButtonBorder,
            onClick = onClick,
        )
    }
}
