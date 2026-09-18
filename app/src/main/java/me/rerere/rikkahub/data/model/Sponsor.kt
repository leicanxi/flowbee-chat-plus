package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * FlowBee 赞助接口 `GET /api/sponsors` 的响应体。
 *
 * 站点把结果包了一层 `{success, data}`，而且**失败时也是 HTTP 200**（和 `/api/usage/token/` 同一个坑），
 * 所以调用方不能只看状态码，必须读 [success]。其余字段（`options`、`custom_min_money` 等）用不到，
 * 由 `JsonInstant` 的 `ignoreUnknownKeys` 丢弃。
 *
 * 支付本身只能在网页上完成，App 侧只负责展示名单 + 一个跳转到网页的入口。
 */
@Serializable
data class SponsorResponse(
    val success: Boolean = false,
    val data: SponsorData? = null,
)

@Serializable
data class SponsorData(
    /** 站点可以整体关闭赞助展示。关闭时按「没有名单」处理即可。 */
    val enabled: Boolean = false,
    val sponsors: List<Sponsor> = emptyList(),
)

/**
 * 一位支持者。同一个人多次支持只会出现一次，带的是他**最近一次**的寄语，
 * 所以名单不会被刷屏，App 侧不需要去重。
 */
@Serializable
data class Sponsor(
    /** 匿名时是**空字符串**，不是缺字段。渲染前先看 [anonymous]。 */
    val name: String = "",
    val anonymous: Boolean = false,
    /** 纯文本寄语，当文本渲染，不要当 HTML 插。 */
    val message: String = "",
    /** 徽章 **id**（见 [SponsorBadge]），中文文案由 App 映射。 */
    val badges: List<String> = emptyList(),
    /** Unix **秒**（不是毫秒）。列表已按最近支持时间倒序返回。 */
    @SerialName("last_time") val lastTime: Long = 0L,
)

/** 徽章 id → 展示文案。站点将来加新徽章时，未知 id 直接不显示，不会渲染成裸 id。 */
enum class SponsorBadge(val id: String) {
    EARLY_SUPPORTER("early_supporter"),
    CONTINUOUS_SUPPORTER("continuous_supporter");

    companion object {
        fun fromId(id: String): SponsorBadge? = entries.firstOrNull { it.id == id }
    }
}
