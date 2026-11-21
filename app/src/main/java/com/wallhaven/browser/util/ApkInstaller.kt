package com.wallhaven.browser.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * 安装下载好的 APK。
 * 走系统安装器（ACTION_VIEW + FileProvider），不需要读写存储权限；
 * 但需要用户在系统里允许本应用「安装未知应用」。
 */
object ApkInstaller {

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { AppLog.warn("Update", "打不开安装权限设置：${it.message}") }
    }

    /**
     * 校验下载下来的包是不是同一个应用、同一个签名。
     * 防止仓库被替换成别人的包后一路装上去。
     */
    fun isTrustedApk(context: Context, apk: File): Boolean {
        if (!apk.exists() || apk.length() <= 0) return false
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = runCatching {
            context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
        }.getOrNull() ?: return false

        if (archive.packageName != context.packageName) {
            AppLog.warn("Update", "下载的 APK 包名不匹配：${archive.packageName}")
            return false
        }
        val remote = archive.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        if (remote.isEmpty()) return false
        val self = runCatching {
            context.packageManager.getPackageInfo(context.packageName, flags)
        }.getOrNull()?.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        if (self.isEmpty()) return false
        return remote.any { r -> self.any { s -> r.contentEquals(s) } }
    }

    fun install(context: Context, apk: File): Boolean {
        if (!canInstall(context)) return false
        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrElse {
            AppLog.error("Update", "打开系统安装器失败：${it.javaClass.simpleName}: ${it.message}")
            false
        }
    }
}
