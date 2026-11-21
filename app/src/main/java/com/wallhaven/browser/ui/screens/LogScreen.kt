package com.wallhaven.browser.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wallhaven.browser.ui.components.EmptyState
import com.wallhaven.browser.ui.components.GlassIconButton
import com.wallhaven.browser.ui.components.GlassSurface
import com.wallhaven.browser.ui.components.LargeTitleHeader
import com.wallhaven.browser.ui.components.PillChip
import com.wallhaven.browser.ui.components.tabBarContentPadding
import com.wallhaven.browser.ui.theme.Pal
import com.wallhaven.browser.util.AppLog
import com.wallhaven.browser.util.LogEntry
import com.wallhaven.browser.util.LogLevel

@Composable
fun LogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var filter by remember { mutableStateOf<LogLevel?>(null) }

    val all = AppLog.entries.toList()
    val shown = remember(all, filter) {
        if (filter == null) all else all.filter { it.level == filter }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        LargeTitleHeader(
            title = "运行日志",
            subtitle = "${all.size} 条 · 保存在本机",
            onBack = onBack,
            actions = {
                GlassIconButton(
                    icon = Icons.Filled.ContentCopy,
                    contentDescription = "复制日志",
                    onClick = {
                        clipboard.setText(AnnotatedString(AppLog.exportText()))
                        Toast.makeText(context, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    },
                )
                Spacer(Modifier.width(8.dp))
                GlassIconButton(
                    icon = Icons.Filled.Share,
                    contentDescription = "分享日志",
                    onClick = { shareLogs(context) },
                )
                Spacer(Modifier.width(8.dp))
                GlassIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "清空日志",
                    onClick = {
                        AppLog.clear()
                        Toast.makeText(context, "日志已清空", Toast.LENGTH_SHORT).show()
                    },
                )
            },
        )

        Spacer(Modifier.size(10.dp))
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(18.dp),
            blurRadius = 24.dp,
            backgroundColor = Pal.GlassBackground,
            overlay = Pal.GlassOverlay,
        ) {
            Text(
                text = AppLog.diagnostic(context),
                style = MaterialTheme.typography.labelSmall,
                color = Pal.LabelSecondary,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }

        Spacer(Modifier.size(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillChip(text = "全部 ${all.size}", selected = filter == null, onClick = { filter = null })
            PillChip(
                text = "错误 ${all.count { it.level == LogLevel.ERROR }}",
                selected = filter == LogLevel.ERROR,
                onClick = { filter = LogLevel.ERROR },
            )
            PillChip(
                text = "警告 ${all.count { it.level == LogLevel.WARN }}",
                selected = filter == LogLevel.WARN,
                onClick = { filter = LogLevel.WARN },
            )
            PillChip(
                text = "信息 ${all.count { it.level == LogLevel.INFO }}",
                selected = filter == LogLevel.INFO,
                onClick = { filter = LogLevel.INFO },
            )
        }

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState("这个级别还没有日志\n用一会儿 App 再回来看看")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 10.dp,
                    bottom = 24.dp + tabBarContentPadding() / 2,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(shown.asReversed()) { entry ->
                    LogRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val levelColor = when (entry.level) {
        LogLevel.ERROR -> Pal.Pink
        LogLevel.WARN -> Pal.Orange
        LogLevel.INFO -> Pal.Accent
        LogLevel.DEBUG -> Pal.LabelTertiary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Pal.Surface.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = entry.timeText(),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = Pal.LabelTertiary,
            modifier = Modifier.width(58.dp),
        )
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(levelColor),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.level.label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = levelColor,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = entry.tag,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Pal.LabelSecondary,
                )
            }
            Spacer(Modifier.size(2.dp))
            Text(
                text = entry.message,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = Pal.Label,
                textAlign = TextAlign.Start,
            )
        }
    }
}

private fun shareLogs(context: Context) {
    val text = buildString {
        appendLine(AppLog.diagnostic(context))
        appendLine()
        append(AppLog.exportText())
    }
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Wallhaven 壁纸运行日志")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            Intent.createChooser(intent, "分享日志").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        Toast.makeText(context, "没有可分享的应用", Toast.LENGTH_SHORT).show()
    }
}
