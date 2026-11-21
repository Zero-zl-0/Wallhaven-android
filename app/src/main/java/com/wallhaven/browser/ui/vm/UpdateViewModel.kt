package com.wallhaven.browser.ui.vm

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wallhaven.browser.data.UpdateChecker
import com.wallhaven.browser.util.ApkInstaller
import com.wallhaven.browser.util.AppLog
import kotlinx.coroutines.launch
import java.io.File

/** 检查更新的界面状态。 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Available(val info: UpdateChecker.ReleaseInfo) : UpdateState
    data class Downloading(val info: UpdateChecker.ReleaseInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateChecker.ReleaseInfo, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /** 需要用户先去系统里允许「安装未知应用」。 */
    var needsInstallPermission by mutableStateOf(false)
        private set

    val currentVersion: String = UpdateChecker.currentVersion

    fun check() {
        if (state is UpdateState.Checking || state is UpdateState.Downloading) return
        state = UpdateState.Checking
        viewModelScope.launch {
            try {
                val info = UpdateChecker.fetchLatest()
                AppLog.info("Update", "最新版本 ${info.version}，当前 $currentVersion")
                state = if (UpdateChecker.isNewer(info.version, currentVersion)) {
                    UpdateState.Available(info)
                } else {
                    UpdateState.UpToDate(info.version)
                }
            } catch (e: Exception) {
                state = UpdateState.Failed(e.message ?: "检查更新失败")
            }
        }
    }

    fun download() {
        val info = (state as? UpdateState.Available)?.info ?: return
        val url = info.apkUrl
        if (url.isNullOrBlank()) {
            state = UpdateState.Ready(info, File("")) // 没有安装包时不应调用，兜底
            return
        }
        state = UpdateState.Downloading(info, 0f)
        viewModelScope.launch {
            try {
                val file = UpdateChecker.download(getApplication(), url, info.version) { progress ->
                    state = UpdateState.Downloading(info, progress)
                }
                val context = getApplication<Application>()
                if (!ApkInstaller.isTrustedApk(context, file)) {
                    state = UpdateState.Failed("下载的安装包签名不匹配，已取消安装（可能是仓库被替换）")
                    return@launch
                }
                state = UpdateState.Ready(info, file)
                installNow()
            } catch (e: Exception) {
                AppLog.error("Update", "下载 APK 失败：${e.message}")
                state = UpdateState.Failed("下载失败：${e.message ?: "未知原因"}")
            }
        }
    }

    fun installNow() {
        val ready = state as? UpdateState.Ready ?: return
        val context = getApplication<Application>()
        if (!ApkInstaller.canInstall(context)) {
            needsInstallPermission = true
            return
        }
        needsInstallPermission = false
        if (!ApkInstaller.install(context, ready.file)) {
            state = UpdateState.Failed("打不开系统安装器，请手动到 设置 → 应用 → 允许安装未知应用")
        }
    }

    /** 从系统设置返回时再看一眼权限。 */
    fun refreshInstallPermission() {
        if (!needsInstallPermission) return
        val context = getApplication<Application>()
        if (ApkInstaller.canInstall(context)) {
            needsInstallPermission = false
            installNow()
        }
    }

    fun reset() {
        state = UpdateState.Idle
        needsInstallPermission = false
    }

    companion object {
        fun factory(app: Application) = viewModelFactory {
            initializer { UpdateViewModel(app) }
        }
    }
}
