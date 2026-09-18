package me.rerere.rikkahub.data.datastore

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import me.rerere.ai.provider.BalanceOption
import me.rerere.ai.provider.ProviderSetting

/**
 * 推荐的提供商列表，在提供商设置页右上角的推荐 Sheet 中展示。
 *
 * 只放官方 FlowBee 一条：用户点「+」会**新建一个独立副本**（随机 Uuid），
 * 用另一把密钥，和内置的那条互不影响。
 */
val RECOMMENDED_PROVIDERS: List<ProviderSetting> = listOf(
    ProviderSetting.OpenAI(
        id = FLOWBEE_PROVIDER_ID,
        name = "FlowBee",
        baseUrl = "https://flowbee.top/v1",
        apiKey = "",
        enabled = true,
        balanceOption = BalanceOption(
            enabled = true,
            apiPath = "/api/usage/token/",
            resultPath = "data.total_available / 500000",
        ),
        description = {
            Text(
                text = buildAnnotatedString {
                    append("官方聚合站，一把密钥即可调用 DeepSeek、Qwen、GLM、Claude、GPT 等主流模型，免费额度优先。")
                    appendLine()
                    append("注册后在站点创建令牌，把密钥填进来即可使用：")
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
)
