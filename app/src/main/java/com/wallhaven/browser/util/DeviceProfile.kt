package com.wallhaven.browser.util

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 本机屏幕的宽高比（用 dp 计算，比例与像素一致）。
 */
data class DeviceProfile(
    val width: Int,
    val height: Int,
) {
    val aspect: Float
        get() = if (height > 0) width.toFloat() / height.toFloat() else 9f / 16f

    val isPortrait: Boolean
        get() = height >= width

    /** 约分后的比例文字，例如 1080×2400 → “9:20”。 */
    val label: String
        get() = DeviceRatio.nearestLabel(aspect)

    /** 距离本机比例有多远，越小越贴合。 */
    fun distanceOf(wallpaperAspect: Float): Float = abs(wallpaperAspect - aspect)

    companion object {
        fun fromPixels(widthPx: Int, heightPx: Int): DeviceProfile =
            DeviceProfile(width = widthPx.coerceAtLeast(1), height = heightPx.coerceAtLeast(1))

        private fun gcd(a: Int, b: Int): Int {
            var x = a
            var y = b
            while (y != 0) {
                val t = y
                y = x % y
                x = t
            }
            return if (x == 0) 1 else x
        }
    }
}

/**
 * wallhaven 的 ratios 参数是精确匹配（例如 9x16 只匹配 0.56），
 * 所以“适配本机比例”实际是按屏幕方向取一组接近手机屏幕的比例，
 * 再由客户端按与屏幕比例的差值排序，让最贴合的排在前面。
 */
object DeviceRatio {
    private const val PORTRAIT =
        "9x16,10x16,9x18,9x19,9x20,9x21,9x22,9x24,3x4,2x3,3x5,7x10"
    private const val LANDSCAPE =
        "16x9,16x10,21x9,32x9,4x3,3x2,2x1"

    fun filterFor(aspect: Float): String = if (aspect < 1f) PORTRAIT else LANDSCAPE

    /** 常见屏幕比例，用来把 0.4497 这种数字显示成 “9:20”。 */
    private val commonRatios = listOf(
        1 to 1, 4 to 3, 3 to 2, 16 to 10, 16 to 9, 21 to 9, 32 to 9,
        3 to 4, 2 to 3, 9 to 16, 9 to 18, 9 to 19, 9 to 20, 9 to 21, 9 to 22, 9 to 24,
    )

    fun nearestLabel(aspect: Float): String {
        if (aspect <= 0f) return "-"
        val best = commonRatios.minByOrNull { (w, h) -> abs(w.toFloat() / h - aspect) }
            ?: return prettyRatio(aspect)
        return "${best.first}:${best.second}"
    }

    /** 用于界面提示，例如 “9:20”。 */
    fun prettyRatio(aspect: Float): String {
        if (aspect <= 0f) return "-"
        val w = (aspect * 1000).roundToInt()
        val h = 1000
        val divisor = gcd(w, h)
        return "${w / divisor}:${h / divisor}"
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val t = y
            y = x % y
            x = t
        }
        return if (x == 0) 1 else x
    }
}
