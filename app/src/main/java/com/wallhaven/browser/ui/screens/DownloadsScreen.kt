package com.wallhaven.browser.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.wallhaven.browser.ui.components.EmptyState
import com.wallhaven.browser.ui.components.GlassIconButton
import com.wallhaven.browser.ui.components.IosButton
import com.wallhaven.browser.ui.components.LargeTitleHeader
import com.wallhaven.browser.ui.components.tabBarContentPadding
import com.wallhaven.browser.ui.theme.AppShapes
import com.wallhaven.browser.ui.theme.Pal
import com.wallhaven.browser.util.AppLog
import com.wallhaven.browser.util.DownloadRegistry
import com.wallhaven.browser.util.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class DownloadedItem(
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateAdded: Long,
    val width: Int,
    val height: Int,
) {
    val aspect: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 0.75f
}

private fun readPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private fun hasReadPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, readPermission()) == PackageManager.PERMISSION_GRANTED

/** Android 14+ 可能只给了「部分照片」权限，这时是看不到已下载壁纸的。 */
private fun hasPartialAccess(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        ) == PackageManager.PERMISSION_GRANTED &&
        !hasReadPermission(context)

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { AppLog.warn("Download", "打不开应用设置：${it.message}") }
}

/**
 * 已下载壁纸：直接查相册里 Pictures/Wallhaven 目录，支持删除。
 */
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasReadPermission(context)) }
    var partial by remember { mutableStateOf(hasPartialAccess(context)) }
    var items by remember { mutableStateOf<List<DownloadedItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<DownloadedItem?>(null) }
    var launchedName by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        granted = hasReadPermission(context)
        partial = hasPartialAccess(context)
    }

    // 从系统设置回来时重新检查权限
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = hasReadPermission(context)
                partial = hasPartialAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val name = launchedName
        launchedName = null
        if (result.resultCode == Activity.RESULT_OK && name != null) {
            DownloadRegistry.removeByName(context, name)
            reload++
        }
    }

    LaunchedEffect(Unit) {
        if (!granted && !partial) permissionLauncher.launch(readPermission())
    }

    LaunchedEffect(granted, reload) {
        if (!granted) return@LaunchedEffect
        loading = true
        items = withContext(Dispatchers.IO) { loadDownloads(context) }
        loading = false
    }

    val totalSize = items.sumOf { it.size }
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        LargeTitleHeader(
            title = "已下载",
            subtitle = if (granted) {
                if (items.isEmpty()) {
                    "相册 / Pictures / Wallhaven"
                } else {
                    "共 ${items.size} 张 · ${formatFileSize(totalSize)}"
                }
            } else {
                "需要相册权限才能列出已下载的壁纸"
            },
            onBack = onBack,
            actions = {
                if (granted) {
                    GlassIconButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = "刷新",
                        onClick = { reload++ },
                    )
                }
            },
        )
        Spacer(Modifier.height(10.dp))

        when {
            !granted && partial -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState("现在只授权了「部分照片」\n看不到已下载的壁纸，需要允许访问全部图片")
                    Spacer(Modifier.height(18.dp))
                    IosButton(
                        text = "允许访问全部图片",
                        onClick = { permissionLauncher.launch(readPermission()) },
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "还是不行？去系统设置里改成「允许全部」",
                        style = MaterialTheme.typography.labelMedium,
                        color = Pal.Accent,
                        modifier = Modifier.clickable { openAppSettings(context) },
                    )
                }
            }

            !granted -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState("要查看已下载的壁纸\n需要允许访问手机里的图片")
                    Spacer(Modifier.height(18.dp))
                    IosButton(
                        text = "允许访问图片",
                        onClick = { permissionLauncher.launch(readPermission()) },
                    )
                }
            }

            loading && items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Pal.Accent)
            }

            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState("还没有下载过壁纸\n在壁纸详情页点「下载壁纸」就会出现在这里")
            }

            else -> LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 24.dp + tabBarContentPadding(),
                ),
                verticalItemSpacing = 10.dp,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.uri.toString() }) { item ->
                    DownloadedCard(
                        item = item,
                        onOpen = { openImage(context, item.uri) },
                        onDelete = { pendingDelete = item },
                    )
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = Pal.SurfaceAlt,
            titleContentColor = Pal.Label,
            textContentColor = Pal.LabelSecondary,
            title = { Text("删除这张壁纸？") },
            text = { Text("${target.name}\n${formatFileSize(target.size)} · 删除后不可恢复") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        startDelete(
                            context = context,
                            item = target,
                            launcher = deleteLauncher,
                            onSystemDialog = { launchedName = target.name },
                            onDirectDeleted = { reload++ },
                        )
                    },
                ) {
                    Text("删除", color = Pal.Pink, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消", color = Pal.LabelSecondary)
                }
            },
        )
    }
}

@Composable
private fun DownloadedCard(
    item: DownloadedItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(item.aspect.coerceIn(0.45f, 1.7f))
            .clip(AppShapes.card)
            .background(Pal.SurfaceAlt),
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpen,
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color(0xCC000000),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 9.dp),
        ) {
            Text(
                text = item.name,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xF2FFFFFF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatFileSize(item.size),
                fontSize = 9.sp,
                color = Color(0xB3FFFFFF),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(7.dp)
                .size(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0x99000000))
                .border(0.7.dp, Color(0x33FFFFFF), RoundedCornerShape(15.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDelete,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "删除",
                tint = Color.White,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

private fun startDelete(
    context: Context,
    item: DownloadedItem,
    launcher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>,
    onSystemDialog: () -> Unit,
    onDirectDeleted: () -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val launched = runCatching {
            val request = MediaStore.createDeleteRequest(context.contentResolver, listOf(item.uri))
            onSystemDialog()
            launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }.isSuccess
        if (launched) return
        AppLog.warn("Download", "系统删除请求失败，改为直接删除")
    }
    runCatching { context.contentResolver.delete(item.uri, null, null) }
        .onSuccess {
            DownloadRegistry.removeByName(context, item.name)
            onDirectDeleted()
        }
        .onFailure { AppLog.error("Download", "删除失败：${it.message}") }
}

private fun openImage(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { AppLog.warn("Download", "打开图片失败：${it.message}") }
}

private fun loadDownloads(context: Context): List<DownloadedItem> {
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
    )
    val selection: String
    val args: Array<String>
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        args = arrayOf("%Pictures/Wallhaven%")
    } else {
        selection = "${MediaStore.Images.Media.DATA} LIKE ?"
        args = arrayOf("%/Pictures/Wallhaven/%")
    }

    val result = mutableListOf<DownloadedItem>()
    runCatching {
        context.contentResolver.query(
            collection,
            projection,
            selection,
            args,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val widthIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            while (cursor.moveToNext()) {
                result += DownloadedItem(
                    uri = ContentUris.withAppendedId(collection, cursor.getLong(idIndex)),
                    name = cursor.getString(nameIndex) ?: "未命名",
                    size = cursor.getLong(sizeIndex),
                    dateAdded = cursor.getLong(dateIndex),
                    width = cursor.getInt(widthIndex),
                    height = cursor.getInt(heightIndex),
                )
            }
        }
    }.onFailure { AppLog.warn("Download", "读取已下载列表失败：${it.message}") }
    return result
}
