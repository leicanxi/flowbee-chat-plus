package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.ai.prompts.LEARNING_MODE_PROMPT
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.tts.provider.TTSProviderSetting
import kotlin.uuid.Uuid

internal val DEFAULT_ASSISTANT_ID = Uuid.parse("0950e2dc-9bd5-4801-afa3-aa887aa36b4e")
internal val DEFAULT_ASSISTANTS = listOf(
    Assistant(
        id = DEFAULT_ASSISTANT_ID,
        name = "",
        systemPrompt = ""
    ),
    Assistant(
        id = Uuid.parse("3d47790c-c415-4b90-9388-751128adb0a0"),
        name = "",
        systemPrompt = """
            You are a helpful assistant, called {{char}}, based on model {{model_name}}.

            ## Info
            - Date: {{cur_date}}
            - Locale: {{locale}}
            - Timezone: {{timezone}}
            - Device Info: {{device_info}}
            - System Version: {{system_version}}
            - User Nickname: {{user}}

            ## Hint
            - If the user does not specify a language, reply in the user's primary language.
            - Remember to use Markdown syntax for formatting, and use latex for mathematical expressions.
        """.trimIndent()
    ),
)

val DEFAULT_SYSTEM_TTS_ID = Uuid.parse("026a01a2-c3a0-4fd5-8075-80e03bdef200")

/**
 * 内置 Moss TTS 提供商（音色库音色）。
 *
 * - baseUrl 固定指向 FlowBee 中转站，key 由 app 层在运行时回退（见 TTS.kt
 *   withFlowBeeApiKeyFallback）：apiKey 留空，用户只需在设置页填一次 FlowBee key。
 * - 用固定 Uuid 让「缺哪个补哪个」的内置补全逻辑（见 SettingsNormalization 的
 *   withBuiltInDefaults）对老用户升级也能生效，且不会重复插入。
 * - voice 是 Moss 音色库的 voice_id（`voice` 是 `voice_id` 的兼容别名，已实测）。
 * - 每个内置音色一条固定 Uuid 的条目；新增内置音色 = 照抄一条改 voice_id 与名字。
 *   设置页顶部的圆形试听图标（见 SettingSpeechPage）按「baseUrl 与 FlowBee 中转站
 *   同源」识别内置音色，新加的会自动出现在那一排。
 */
val DEFAULT_MOSS_TTS_ID = Uuid.parse("6f1d2c4e-8a3b-4f5c-9d7e-2b4a6c8d0e1f")

/** 第二个内置音色「冷静理性温柔帅哥」的固定 Uuid。 */
val DEFAULT_COOL_TTS_ID = Uuid.parse("39099106-049c-43ec-b8bd-0519afb4ce50")

internal val DEFAULT_TTS_PROVIDERS = listOf(
    TTSProviderSetting.SystemTTS(
        id = DEFAULT_SYSTEM_TTS_ID,
        name = "",
    ),
    TTSProviderSetting.OpenAI(
        id = DEFAULT_MOSS_TTS_ID,
        name = "哄睡daddy",
        apiKey = "",
        baseUrl = "https://flowbee.top/v1",
        model = "moss-tts-1.5-flash",
        voice = "187b0c9b-612c-4019-98fe-c7bb59eb8074",
    ),
    TTSProviderSetting.OpenAI(
        id = DEFAULT_COOL_TTS_ID,
        name = "冷静理性温柔帅哥",
        apiKey = "",
        baseUrl = "https://flowbee.top/v1",
        model = "moss-tts-1.5-flash",
        voice = "b1dde58c-3610-460e-b318-af08821dcfc6",
    ),
)

internal val DEFAULT_ASSISTANTS_IDS = DEFAULT_ASSISTANTS.map { it.id }

val DEFAULT_MODE_INJECTIONS = listOf(
    PromptInjection.ModeInjection(
        id = Uuid.parse("b87eaf16-f5cd-4ac1-9e4f-b11ae3a61d74"),
        content = LEARNING_MODE_PROMPT,
        position = InjectionPosition.AFTER_SYSTEM_PROMPT,
        name = "Learning Mode"
    )
)
