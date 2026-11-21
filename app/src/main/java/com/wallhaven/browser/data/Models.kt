package com.wallhaven.browser.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Wallpaper(
    val id: String = "",
    val url: String = "",
    @SerialName("short_url") val shortUrl: String = "",
    val views: Int = 0,
    val favorites: Int = 0,
    val purity: String = "",
    val category: String = "",
    @SerialName("dimension_x") val dimensionX: Int = 0,
    @SerialName("dimension_y") val dimensionY: Int = 0,
    val resolution: String = "",
    val ratio: String = "",
    @SerialName("file_size") val fileSize: Long = 0,
    @SerialName("file_type") val fileType: String = "",
    @SerialName("created_at") val createdAt: String = "",
    val colors: List<String> = emptyList(),
    val path: String = "",
    val thumbs: Thumbs = Thumbs(),
    val uploader: Uploader? = null,
    val tags: List<Tag>? = null,
) {
    val aspect: Float
        get() = if (dimensionX > 0 && dimensionY > 0) dimensionX.toFloat() / dimensionY.toFloat() else 16f / 9f

    /** 卡片按壁纸真实比例展示，避免竖图被裁掉一截；异常值兜底成 3:4。 */
    val cardAspect: Float
        get() = if (aspect.isFinite() && aspect >= 0.1f) aspect else 0.75f

    val categoryLabel: String
        get() = when (category) {
            "general" -> "综合"
            "anime" -> "动漫"
            "people" -> "人物"
            else -> category
        }

    val purityLabel: String
        get() = when (purity) {
            "sfw" -> "SFW 安全"
            "sketchy" -> "Sketchy 敏感"
            "nsfw" -> "NSFW 成人"
            else -> purity
        }

    val displayResolution: String
        get() = resolution.ifBlank { if (dimensionX > 0) "$dimensionX × $dimensionY" else "未知" }

    val thumbUrl: String
        get() = thumbs.large.ifBlank { thumbs.original.ifBlank { thumbs.small } }

    /** wallhaven 的 file_type 是 MIME（image/jpeg），下载时要换成真正的文件后缀。 */
    val extension: String
        get() = when {
            fileType.contains("png", ignoreCase = true) -> "png"
            fileType.contains("webp", ignoreCase = true) -> "webp"
            fileType.contains("gif", ignoreCase = true) -> "gif"
            fileType.contains("jpeg", ignoreCase = true) || fileType.contains("jpg", ignoreCase = true) -> "jpg"
            else -> path.substringAfterLast('.', "")
                .lowercase()
                .filter { it.isLetterOrDigit() }
                .ifBlank { "jpg" }
        }

    val fileName: String
        get() = "wallhaven-$id.$extension"

    val formatLabel: String
        get() = extension.uppercase()
}

@Serializable
data class Thumbs(
    val large: String = "",
    val original: String = "",
    val small: String = "",
)

@Serializable
data class Uploader(
    val username: String = "",
    val group: String? = null,
)

@Serializable
data class Tag(
    val id: Int = 0,
    val name: String = "",
    val alias: String = "",
    @SerialName("category_id") val categoryId: Int = 0,
    val category: String = "",
    val purity: String = "",
)

@Serializable
data class Meta(
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("last_page") val lastPage: Int = 1,
    @SerialName("per_page") val perPage: Int = 24,
    val total: Int = 0,
    val query: String? = null,
    val seed: String? = null,
)

@Serializable
data class SearchResponse(
    val data: List<Wallpaper> = emptyList(),
    val meta: Meta = Meta(),
)

@Serializable
data class SingleResponse(
    val data: Wallpaper = Wallpaper(),
)
