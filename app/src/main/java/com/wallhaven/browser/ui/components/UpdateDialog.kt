package com.wallhaven.browser.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wallhaven.browser.ui.theme.Pal
import com.wallhaven.browser.ui.vm.UpdateState

/**
 * 检查更新的对话框：检查中 / 已是最新 / 有新版本 / 下载中 / 需要安装权限 / 失败。
 */
@Composable
fun UpdateDialog(
    state: UpdateState,
    currentVersion: String,
    needsInstallPermission: Boolean,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenRelease: (String) -> Unit,
    onOpenGithub: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dismissible = state !is UpdateState.Downloading && state !is UpdateState.Checking
    AlertDialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        containerColor = Pal.SurfaceAlt,
        titleContentColor = Pal.Label,
        textContentColor = Pal.LabelSecondary,
        title = {
            Text(
                when (state) {
                    is UpdateState.Checking -> "检查更新"
                    is UpdateState.UpToDate -> "已是最新版本"
                    is UpdateState.Available -> "发现新版本 v${state.info.version}"
                    is UpdateState.Downloading -> "正在下载更新"
                    is UpdateState.Ready -> if (needsInstallPermission) "还需要一步" else "准备安装"
                    is UpdateState.Failed -> "检查更新"
                    UpdateState.Idle -> "检查更新"
                },
            )
        },
        text = {
            Column {
                when (state) {
                    is UpdateState.Checking -> {
                        Text(
                            text = "当前版本 v$currentVersion，正在向 GitHub 查询…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(14.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = Pal.Accent,
                        )
                    }

                    is UpdateState.UpToDate -> {
                        Text(
                            text = "当前 v$currentVersion 已经是最新版本了。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    is UpdateState.Available -> {
                        Text(
                            text = "当前 v$currentVersion → 最新 v${state.info.version}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Pal.Label,
                        )
                        val notes = state.info.notes.trim()
                        if (notes.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = notes.take(320),
                                style = MaterialTheme.typography.labelSmall,
                                color = Pal.LabelTertiary,
                            )
                        }
                        if (state.info.apkUrl.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "这个版本没有附带 APK 安装包，需要打开 GitHub 手动下载。",
                                style = MaterialTheme.typography.labelSmall,
                                color = Pal.Pink,
                            )
                        }
                    }

                    is UpdateState.Downloading -> {
                        Text(
                            text = "v${state.info.version} · ${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = Pal.Accent,
                            trackColor = Pal.Separator,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "下载完成后会自动打开系统安装界面。若系统弹出 Play Protect 提示，点「更多详情 → 仍要安装」即可。",
                            style = MaterialTheme.typography.labelSmall,
                            color = Pal.LabelTertiary,
                        )
                    }

                    is UpdateState.Ready -> {
                        if (needsInstallPermission) {
                            Text(
                                text = "安装包已下载完成。\n需要你在系统里允许本应用「安装未知应用」，再回来点一次安装。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            Text(
                                text = "安装包已下载完成，正在打开系统安装界面…",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    is UpdateState.Failed -> {
                        Text(text = state.message, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "如果系统弹 Play Protect 拦截，需要「仍要安装」，或在系统设置里临时关掉 Play Protect 扫描。也可以到 GitHub 手动下载。",
                            style = MaterialTheme.typography.labelSmall,
                            color = Pal.LabelTertiary,
                        )
                    }

                    UpdateState.Idle -> {
                        Text(text = "当前版本 v$currentVersion", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Available -> {
                    TextButton(
                        onClick = {
                            if (state.info.apkUrl.isNullOrBlank()) {
                                onOpenRelease(state.info.htmlUrl)
                            } else {
                                onDownload()
                            }
                        },
                    ) {
                        Text(
                            text = if (state.info.apkUrl.isNullOrBlank()) "去 GitHub 下载" else "立即更新",
                            color = Pal.Accent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                is UpdateState.Ready -> {
                    if (needsInstallPermission) {
                        TextButton(onClick = onOpenSettings) {
                            Text("去允许安装", color = Pal.Accent, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        TextButton(onClick = onInstall) {
                            Text("安装", color = Pal.Accent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                is UpdateState.Failed -> {
                    TextButton(onClick = onRetry) {
                        Text("重试", color = Pal.Accent, fontWeight = FontWeight.SemiBold)
                    }
                }

                else -> {
                    TextButton(onClick = onDismiss) {
                        Text("好", color = Pal.Accent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        dismissButton = when {
            state is UpdateState.Failed -> {
                {
                    TextButton(onClick = onOpenGithub) {
                        Text("去 GitHub", color = Pal.LabelSecondary)
                    }
                }
            }

            dismissible && state !is UpdateState.UpToDate -> {
                {
                    TextButton(onClick = onDismiss) {
                        Text("稍后", color = Pal.LabelSecondary)
                    }
                }
            }

            else -> null
        },
    )
}
