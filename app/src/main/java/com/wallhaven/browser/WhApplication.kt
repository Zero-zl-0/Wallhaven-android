package com.wallhaven.browser

import android.app.Application
import com.wallhaven.browser.util.AppLog

/**
 * 只做两件事：启动日志、把崩溃写进日志文件，方便之后在「设置 → 运行日志」里查看。
 */
class WhApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppLog.info(
            "App",
            "启动 v${runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()} / " +
                "Android ${android.os.Build.VERSION.SDK_INT} / ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
        )

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                AppLog.error(
                    "Crash",
                    "未捕获异常（线程 ${thread.name}）：${throwable.javaClass.simpleName}: ${throwable.message}",
                    throwable,
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
