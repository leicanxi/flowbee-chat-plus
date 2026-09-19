package me.rerere.rikkahub.data.flowbee

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.utils.JsonInstant
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * FlowBee（自部署 NewAPI）站点常量。
 *
 * 站点根地址与 provider 的 `baseUrl` 是两份信息：`baseUrl` 指向 `/v1` 兼容层，
 * 而额度、状态等接口在站点根下。这里统一从 [siteRootOf] 推导，避免两处各写一遍。
 */
object FlowBeeDefaults {
    const val SITE_URL: String = "https://flowbee.top"

    /**
     * 令牌额度接口路径。
     *
     * **注意它挂在站点根下，不在 `/v1` 兼容层里**：provider 的 `BalanceOption` 如果填相对路径，
     * `OpenAIProvider.getBalance` 会把它拼在 `baseUrl`（本站是 `https://flowbee.top/v1`）后面，
     * 请求到 `/v1/api/usage/token/` —— 实测 404。所以对外一律给绝对地址 [TOKEN_USAGE_URL]。
     */
    const val TOKEN_USAGE_PATH: String = "/api/usage/token/"

    /** 拼好的绝对地址，供 provider 的 `BalanceOption.apiPath` 使用。 */
    val TOKEN_USAGE_URL: String = SITE_URL + TOKEN_USAGE_PATH

    /**
     * 控制台（概览页）：未登录会先跳登录页。密钥就在这一页，用量与日志也在。
     *
     * 不带尾部斜杠，直接给具体路由 —— 站点的 `/` 会再兜一次跳转，多一跳没必要。
     */
    const val DASHBOARD_URL: String = "$SITE_URL/dashboard"

    /** 钱包页：余额与充值同一页。 */
    const val WALLET_URL: String = "$SITE_URL/wallet"

    /** 赞助页：`#support` 锚点直接落在「支持 FlowBee」那一块，支付在网页里完成。 */
    const val SPONSORS_URL: String = "$SITE_URL/sponsors#support"

    /** provider 的 `baseUrl` → 站点根地址。`https://flowbee.top/v1` → `https://flowbee.top` */
    fun siteRootOf(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return SITE_URL
        return trimmed.removeSuffix("/v1").ifEmpty { SITE_URL }
    }

    /**
     * 统一成 `sk-` 前缀。
     *
     * NewAPI 服务端两种写法都认（`middleware/auth.go` 先剥 `Bearer ` 再剥 `sk-`），
     * 但用户从网页上抄 key 时可能只复制到 `sk-` 后面那一段。存进 provider 前补全前缀，
     * 后续所有调用方（拉模型列表、发消息）就不必各自再判断一次。
     */
    fun normalizeKey(raw: String): String {
        val key = raw.trim()
        if (key.isEmpty() || key.startsWith(KEY_PREFIX, ignoreCase = true)) return key
        return KEY_PREFIX + key
    }

    private const val KEY_PREFIX = "sk-"
}

/** 密钥校验结果。 */
sealed interface FlowBeeCheck {
    /**
     * 密钥有效。
     *
     * @param balanceText 主数字，已按站点展示口径换算好（如 `¥12.34`）。站点带上账户额度时它是**账户余额**，
     *   否则退回令牌额度口径（令牌不限额度时就是「不限额度」）
     * @param tokenLimitText 令牌自己的剩余额度文案。**只在它比账户余额更小时**才给：这种令牌会先见底，
     *   主数字只显示账户余额会让人以为还能花更多；不限额度或额度更宽裕时为 `null`
     */
    data class Connected(
        val balanceText: String,
        val tokenLimitText: String? = null,
    ) : FlowBeeCheck

    /** 密钥无效、站点不可达或返回异常，[message] 可直接展示给用户。 */
    data class Failed(val message: String) : FlowBeeCheck
}

private class FlowBeeException(message: String) : Exception(message)

/**
 * 只做两件事：校验令牌、把额度换算成站点展示口径。
 *
 * 用两个接口，账户余额那一项依赖站点补的一个字段（见下）：
 * - `GET /api/status`（公开）→ 额度单位 / 展示类型 / 汇率，决定额度怎么换算
 * - `GET /api/usage/token/`（令牌认证）→ 令牌剩余额度，以及站点补的 `data.user_quota`（账户剩余额度）
 *
 * **余额取的是账户额度而不是令牌额度**：令牌表单默认就是不限额度，这类令牌的
 * `total_available` 恒为 0，照着显示只会得到「不限额度」或者一个假的 0。
 * 账户额度是钱包页那个数字，也是真正卡住请求的上限。站点还没补 `user_quota` 时退回令牌口径。
 */
class FlowBeeClient(
    private val client: OkHttpClient,
    private val json: Json = JsonInstant,
) {
    @Volatile
    private var cachedDisplay: Display? = null

    @Volatile
    private var cachedDisplayAt: Long = 0L

    /**
     * 校验密钥并顺带取回余额。
     *
     * 一次请求同时回答两个问题——「这个 key 能用吗」和「还剩多少」——避免只判断 key 非空
     * 导致的假阳性（填了错的 key 也显示"已连接"）。
     */
    suspend fun check(apiKey: String, siteRoot: String = FlowBeeDefaults.SITE_URL): FlowBeeCheck =
        withContext(Dispatchers.IO) {
            val key = apiKey.trim()
            if (key.isEmpty()) {
                return@withContext FlowBeeCheck.Failed("请先填写密钥")
            }

            val usage = try {
                fetchTokenUsage(siteRoot, key)
            } catch (e: Exception) {
                return@withContext FlowBeeCheck.Failed(e.toFriendlyMessage())
            }

            // 账户余额优先；站点是没补 user_quota 的老版本时，才退回下面的令牌口径。
            val userRemain = usage.userRemainQuota
            if (userRemain != null) {
                val display = loadDisplay(siteRoot)
                return@withContext FlowBeeCheck.Connected(
                    balanceText = formatQuota(userRemain, display),
                    tokenLimitText = tokenLimitOf(usage, userRemain, display),
                )
            }

            if (usage.unlimited) {
                return@withContext FlowBeeCheck.Connected(balanceText = "不限额度")
            }

            FlowBeeCheck.Connected(balanceText = formatQuota(usage.remainQuota, loadDisplay(siteRoot)))
        }

    /**
     * 令牌自己还能用多少；不限额度、或额度比账户余额更宽裕时都不提示。
     *
     * 只有「令牌先见底」才值得多占一句：那时账户里钱再多也花不出去，只显示账户余额是误导。
     */
    private fun tokenLimitOf(usage: TokenUsage, userRemain: Double, display: Display?): String? {
        if (usage.unlimited) return null
        if (usage.remainQuota >= userRemain) return null
        return formatQuota(usage.remainQuota, display)
    }

    private fun fetchTokenUsage(siteRoot: String, key: String): TokenUsage {
        val normalizedKey = FlowBeeDefaults.normalizeKey(key)
        val request = Request.Builder()
            .url("${siteRoot.trimEnd('/')}${FlowBeeDefaults.TOKEN_USAGE_PATH}")
            .addHeader("Authorization", "Bearer $normalizedKey")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (response.code == 401 || response.code == 403) {
                throw FlowBeeException("密钥无效或已被禁用")
            }
            if (!response.isSuccessful) {
                throw FlowBeeException("请求失败（HTTP ${response.code}）")
            }

            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrElse {
                throw FlowBeeException("站点返回内容无法解析")
            }
            // NewAPI 的失败分支是 HTTP 200 + {success:false, message:"..."}
            if (root["success"]?.jsonPrimitive?.booleanOrNull == false) {
                throw FlowBeeException(root["message"]?.jsonPrimitive?.contentOrNull ?: "密钥无效")
            }

            val data = root["data"]?.jsonObject ?: throw FlowBeeException("站点返回数据格式异常")
            return TokenUsage(
                remainQuota = data["total_available"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                unlimited = data["unlimited_quota"]?.jsonPrimitive?.booleanOrNull ?: false,
                // 站点补的字段，老版本直接缺键 → 读出来是 null，调用方据此退回令牌口径。
                userRemainQuota = data["user_quota"]?.jsonPrimitive?.doubleOrNull,
            )
        }
    }

    /** 站点展示配置基本不变，缓存 10 分钟，失败则不过期旧值之外直接回落。 */
    private fun loadDisplay(siteRoot: String): Display? {
        val now = System.currentTimeMillis()
        cachedDisplay?.let { if (now - cachedDisplayAt < DISPLAY_TTL_MS) return it }
        return runCatching { fetchDisplay(siteRoot) }
            .onSuccess {
                cachedDisplay = it
                cachedDisplayAt = now
            }
            .getOrNull()
            ?: cachedDisplay
    }

    private fun fetchDisplay(siteRoot: String): Display {
        val request = Request.Builder()
            .url("${siteRoot.trimEnd('/')}/api/status")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw FlowBeeException("HTTP ${response.code}")
            val data = runCatching {
                json.parseToJsonElement(response.body.string()).jsonObject["data"]!!.jsonObject
            }.getOrElse { throw FlowBeeException("站点返回内容无法解析") }

            return Display(
                quotaPerUnit = data["quota_per_unit"]?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0 }
                    ?: DEFAULT_QUOTA_PER_UNIT,
                displayType = data["quota_display_type"]?.jsonPrimitive?.contentOrNull ?: TYPE_USD,
                usdExchangeRate = data["usd_exchange_rate"]?.jsonPrimitive?.doubleOrNull
                    ?: DEFAULT_USD_EXCHANGE_RATE,
                customSymbol = data["custom_currency_symbol"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                customExchangeRate = data["custom_currency_exchange_rate"]?.jsonPrimitive?.doubleOrNull
                    ?: 1.0,
            )
        }
    }

    /**
     * 复刻 NewAPI `controller/billing.go` 的换算口径，保证 App 显示的金额和后端一致：
     * USD → 除以额度单位；CNY → 再乘汇率；CUSTOM → 乘自定义汇率；TOKENS → 原值。
     */
    private fun formatQuota(quota: Double, display: Display?): String {
        if (display == null) return "%.0f".format(quota)
        if (display.displayType == TYPE_TOKENS) return "%.0f tokens".format(quota)
        val amount = quota / display.quotaPerUnit * display.rate
        return "${display.symbol}%.2f".format(amount)
    }

    private data class TokenUsage(
        val remainQuota: Double,
        val unlimited: Boolean,
        /** 账户剩余额度（原始单位）。站点没带 `user_quota` 时为 `null`，调用方据此退回令牌口径。 */
        val userRemainQuota: Double?,
    )

    private data class Display(
        val quotaPerUnit: Double,
        val displayType: String,
        val usdExchangeRate: Double,
        val customSymbol: String,
        val customExchangeRate: Double,
    ) {
        val symbol: String
            get() = when (displayType) {
                TYPE_USD -> "$"
                TYPE_CNY -> "¥"
                TYPE_CUSTOM -> customSymbol.ifEmpty { "¤" }
                else -> ""
            }

        val rate: Double
            get() = when (displayType) {
                TYPE_USD -> 1.0
                TYPE_CNY -> usdExchangeRate
                TYPE_CUSTOM -> customExchangeRate.takeIf { it > 0 } ?: 1.0
                else -> 1.0
            }
    }

    private companion object {
        const val TYPE_USD = "USD"
        const val TYPE_CNY = "CNY"
        const val TYPE_TOKENS = "TOKENS"
        const val TYPE_CUSTOM = "CUSTOM"
        const val DEFAULT_QUOTA_PER_UNIT = 500000.0
        const val DEFAULT_USD_EXCHANGE_RATE = 1.0
        const val DISPLAY_TTL_MS = 10 * 60 * 1000L
    }
}

private fun Exception.toFriendlyMessage(): String = when (this) {
    is FlowBeeException -> message ?: "校验失败"
    is IOException -> "网络连接失败，请检查网络后重试"
    else -> message ?: "校验失败"
}
