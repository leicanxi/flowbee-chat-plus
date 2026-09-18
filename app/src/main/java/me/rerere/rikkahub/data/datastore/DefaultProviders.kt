package me.rerere.rikkahub.data.datastore

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import me.rerere.ai.provider.BalanceOption
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import kotlin.uuid.Uuid

val DEFAULT_AUTO_MODEL_ID = Uuid.parse("b7055fb4-39f9-4042-a88a-0d80ed76cf08")

/**
 * 内置 FlowBee 提供商。
 *
 * 用固定 Uuid 是为了让「设置页的 FlowBee 卡片」和「提供商详情页」指向**同一份数据**：
 * 设置页填密钥 = 给这个条目写 `apiKey`，不会产生第二份存储。
 */
val FLOWBEE_PROVIDER_ID: Uuid = Uuid.parse("7f9c1a52-3b0d-4e6a-9c21-5d8b0f4a7c31")

/**
 * 已下架的内置提供商：AiHubMix / 小马算力 / 302.AI / 随想AI网关 / APIMart / MaruCode。
 *
 * 这些条目在旧版本里已经写进用户的 DataStore，而 `builtIn = true` 让用户在 UI 上
 * **无法删除**它们 —— 只从 DEFAULT_PROVIDERS 里删掉是不够的，装过旧版的机器上会永久残留。
 * 加载设置时会把它们剔除，但**已填过密钥的不动**，避免无提示地扔掉用户自己的配置。
 */
val REMOVED_BUILT_IN_PROVIDER_IDS: Set<Uuid> = setOf(
    Uuid.parse("1b1395ed-b702-4aeb-8bc1-b681c4456953"), // AiHubMix
    Uuid.parse("da020a90-f7b3-4c29-b90e-c511a0630630"), // 小马算力
    Uuid.parse("da93779f-3956-48cc-82ef-67bb482eaaf7"), // 302.AI
    Uuid.parse("aecf04fd-cb5c-4582-aed2-e8bf393923fd"), // 随想AI网关
    Uuid.parse("2a05506f-3a59-450a-a493-33a82bc85a81"), // APIMart
    Uuid.parse("afbc54ad-807e-4455-9594-7d7a546356ad"), // MaruCode
)

/** 该提供商是否已被用户配置过（填过密钥）。 */
val ProviderSetting.hasUserKey: Boolean
    get() = when (this) {
        is ProviderSetting.OpenAI -> apiKey.isNotBlank()
        is ProviderSetting.Google -> apiKey.isNotBlank()
        is ProviderSetting.Claude -> apiKey.isNotBlank()
    }

val DEFAULT_PROVIDERS = listOf(
    ProviderSetting.OpenAI(
        id = FLOWBEE_PROVIDER_ID,
        name = "FlowBee",
        baseUrl = "https://flowbee.top/v1",
        apiKey = "",
        enabled = true,
        builtIn = true,
        balanceOption = BalanceOption(
            enabled = true,
            apiPath = "/api/usage/token/",
            // 站点额度单位 500000 = ¥1（`/api/status` 的 quota_per_unit）。
            // 这里只保证「提供商详情页」显示的数字量级正确；设置页的余额走 FlowBeeClient，
            // 以 `/api/status` 的实时配置为准，站点改了额度单位不用改这里。
            resultPath = "data.total_available / 500000",
        ),
        description = {
            Text(
                text = buildAnnotatedString {
                    append("FlowBee 官方接入：在设置页填入密钥即可使用，无需自行配置接口地址。")
                    appendLine()
                    append("密钥获取：")
                    withLink(LinkAnnotation.Url("https://flowbee.top/")) {
                        withStyle(SpanStyle(MaterialTheme.colorScheme.primary)) {
                            append("https://flowbee.top")
                        }
                    }
                }
            )
        },
        shortDescription = {
            Text("官方直连，填入密钥即可使用")
        },
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("1eeea727-9ee5-4cae-93e6-6fb01a4d051e"),
        name = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        apiKey = "",
        builtIn = true
    ),
    ProviderSetting.Google(
        id = Uuid.parse("6ab18148-c138-4394-a46f-1cd8c8ceaa6d"),
        name = "Gemini",
        apiKey = "",
        enabled = true,
        builtIn = true
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("56a94d29-c88b-41c5-8e09-38a7612d6cf8"),
        name = "硅基流动",
        baseUrl = "https://api.siliconflow.cn/v1",
        apiKey = "",
        builtIn = true,
        description = {
            MarkdownBlock(
                content = """
                    ${stringResource(R.string.silicon_flow_description)}
                    ${stringResource(R.string.silicon_flow_website)}
                """.trimIndent()
            )
        },
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("f099ad5b-ef03-446d-8e78-7e36787f780b"),
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        apiKey = "",
        builtIn = true,
        balanceOption = BalanceOption(
            enabled = true,
            apiPath = "/user/balance",
            resultPath = "balance_infos[0].total_balance"
        )
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("d6c4d8c6-3f62-4ca9-a6f3-7ade6b15ecc3"),
        name = "月之暗面",
        baseUrl = "https://api.moonshot.cn/v1",
        apiKey = "",
        enabled = true,
        builtIn = true,
        balanceOption = BalanceOption(
            enabled = true,
            apiPath = "/users/me/balance",
            resultPath = "data.available_balance"
        )
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("d5734028-d39b-4d41-9841-fd648d65440e"),
        name = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        apiKey = "",
        builtIn = true,
        balanceOption = BalanceOption(
            enabled = true,
            apiPath = "/credits",
            resultPath = "data.total_credits - data.total_usage",
        )
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("386e0f29-8228-4512-affe-8fd8add82d88"),
        name = "Vercel AI Gateway",
        baseUrl = "https://ai-gateway.vercel.sh/v1",
        apiKey = "",
        enabled = false,
        builtIn = true,
        balanceOption = BalanceOption(
            enabled = true,
            apiPath = "/credits",
            resultPath = "balance",
        )
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("f76cae46-069a-4334-ab8e-224e4979e58c"),
        name = "阿里云百炼",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        apiKey = "",
        enabled = false,
        builtIn = true
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("3dfd6f9b-f9d9-417f-80c1-ff8d77184191"),
        name = "火山引擎",
        baseUrl = "https://ark.cn-beijing.volces.com/api/v3",
        apiKey = "",
        enabled = false,
        builtIn = true
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("3bc40dc1-b11a-46fa-863b-6306971223be"),
        name = "智谱AI开放平台",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        apiKey = "",
        enabled = false,
        builtIn = true
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("f4f8870e-82d3-495b-9b64-d58e508b3b2c"),
        name = "阶跃星辰",
        baseUrl = "https://api.stepfun.com/v1",
        apiKey = "",
        enabled = false,
        builtIn = true
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("ef5d149b-8e34-404b-818c-6ec242e5c3c5"),
        name = "腾讯Hunyuan",
        baseUrl = "https://api.hunyuan.cloud.tencent.com/v1",
        apiKey = "",
        enabled = false,
        builtIn = true
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("ff3cde7e-0f65-43d7-8fb2-6475c99f5990"),
        name = "xAI",
        baseUrl = "https://api.x.ai/v1",
        apiKey = "",
        enabled = false,
        builtIn = true,
        useResponseApi = true,
    ),
    ProviderSetting.Claude(
        id = Uuid.parse("b4deabea-20fb-4101-a74c-65679c7e4754"),
        name = "MiniMax",
        baseUrl = "https://api.minimaxi.com/anthropic/v1",
        apiKey = "",
        enabled = false,
        builtIn = true,
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("a2bafe83-eaf8-47bf-a8c7-3dd82d89f637"),
        name = "MIMO",
        baseUrl = "https://api.xiaomimimo.com/v1",
        apiKey = "",
        enabled = false,
        builtIn = true,
    ),
)
