package me.rerere.rikkahub.ui.hooks

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ProviderSetting
import me.rerere.tts.model.PlaybackState
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.FLOWBEE_PROVIDER_ID
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getSelectedTTSProvider
import me.rerere.rikkahub.utils.stripMarkdown
import me.rerere.tts.model.TTSResponse
import me.rerere.tts.provider.TTSManager
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.controller.TtsController
import org.koin.compose.koinInject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val TAG = "TTS"

/**
 * Composable function to remember and manage custom TTS state.
 * Uses user-configured TTS providers instead of system TTS.
 */
@Composable
fun rememberCustomTtsState(): CustomTtsState {
    val context = LocalContext.current
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    // 配置不全时的提示文案：在 Composable 里取好再往下传 —— TtsState 实现不是
    // Composable，取不到资源；为一句提示把 Context 一路传进去也不值当。
    val missingKeyHint = stringResource(R.string.tts_setup_missing_key)

    // Remember the CustomTtsState instance across recompositions
    val ttsState = remember {
        CustomTtsStateImpl(
            context = context.applicationContext,
        )
    }

    // Update the provider when settings change
    DisposableEffect(
        settings.selectedTTSProviderId,
        settings.ttsProviders,
        settings.defaultTTSPlaybackSpeed,
        // 内置音色的密钥来自 FlowBee 提供商，用户在设置页填/改密钥时也要重新下发 provider，
        // 否则会拿着旧的空 key 去请求（表现为 401，但设置页看着一切正常）。
        settings.flowBeeProvider()?.apiKey,
        missingKeyHint,
    ) {
        ttsState.updateProvider(
            provider = settings.getSelectedTTSProvider(),
            flowBeeProvider = settings.flowBeeProvider(),
            setupIssue = settings.ttsSetupHint(missingKeyHint),
        )
        ttsState.setSpeed(settings.defaultTTSPlaybackSpeed)
        onDispose { }
    }

    // Cleanup resources when the state is disposed
    DisposableEffect(ttsState) {
        onDispose {
            ttsState.cleanup()
        }
    }

    return ttsState
}

/**
 * Interface defining the public API of our custom TTS state holder.
 */
interface CustomTtsState {
    /** Flow indicating if the TTS provider is available and ready. */
    val isAvailable: StateFlow<Boolean>

    /** Flow indicating if the TTS is currently speaking. */
    val isSpeaking: StateFlow<Boolean>

    /** Flow holding any error message. */
    val error: StateFlow<String?>

    /**
     * 朗读配置不完整时的提示文案（目前只有一种：内置音色指向 FlowBee 中转站，
     * 但用户还没填 FlowBee 密钥）。null 表示配置完整。
     */
    val setupIssue: StateFlow<String?>

    /** Flow indicating current chunk being processed (index) */
    val currentChunk: StateFlow<Int>

    /** Flow indicating total chunks in queue */
    val totalChunks: StateFlow<Int>

    /** Unified playback state (status, position, duration, speed, etc.) */
    val playbackState: StateFlow<PlaybackState>

    /**
     * Speaks the given text using the selected TTS provider.
     * Long texts will be automatically chunked and queued.
     */
    fun speak(text: String, flushCalled: Boolean = true)

    /** Stops the current speech and clears the queue. */
    fun stop()

    /** Pauses the current playback. */
    fun pause()

    /** Resumes the paused playback. */
    fun resume()

    /** Skips to the next chunk in the queue. */
    fun skipNext()

    /** Fast forward current playback by [ms]. */
    fun fastForward(ms: Long = 5_000)

    /** Set playback [speed]. */
    fun setSpeed(speed: Float)

    /** Cleanup resources. */
    fun cleanup()
}

/**
 * Internal implementation of CustomTtsState.
 */
private class CustomTtsStateImpl(
    private val context: Context,
) : CustomTtsState, KoinComponent {

    private val ttsManager by inject<TTSManager>()
    private val controller by lazy { me.rerere.tts.controller.TtsController(context, ttsManager) }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentJob: Job? = null

    override val isAvailable: StateFlow<Boolean> get() = controller.isAvailable
    override val isSpeaking: StateFlow<Boolean> get() = controller.isSpeaking

    /**
     * 报错有两条来源：speech 模块 controller 的合成/播放错误，以及本层拦下的配置问题。
     * 合并成一条流，展示层（TTSController）就不必区分来源，沿用同一套提示。
     */
    private val _setupInput = MutableStateFlow<String?>(null)
    private val _setupError = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> =
        combine(controller.error, _setupError) { fromController, fromSetup ->
            fromSetup ?: fromController
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /** 本地化后的成品文案，由 Composable 侧算好传进来。 */
    override val setupIssue: StateFlow<String?> get() = _setupInput
    override val currentChunk: StateFlow<Int> get() = controller.currentChunk
    override val totalChunks: StateFlow<Int> get() = controller.totalChunks
    override val playbackState: StateFlow<PlaybackState> get() = controller.playbackState

    fun updateProvider(
        provider: TTSProviderSetting?,
        flowBeeProvider: ProviderSetting.OpenAI?,
        setupIssue: String?,
    ) {
        _setupInput.value = setupIssue
        // 配置修好了就把旧提示收掉，别让报错一直挂着。
        if (setupIssue == null) _setupError.value = null
        // 内置 TTS provider（如 Moss 音色）apiKey 留空，运行时回退用 FlowBee 的 key：
        // 用户只在设置页填一次 newapi token，聊天与 TTS 共用，换 key 也只改一处。
        // 判定按 baseUrl 是否指向 FlowBee 中转站，不按 provider 类型 —— 以后新增
        // 任何类型的 Moss 音色（MiniMax/Step 等）只要走同一中转站同样自动生效。
        controller.setProvider(
            provider?.withFlowBeeApiKeyFallback(
                flowBeeBaseUrl = flowBeeProvider?.baseUrl.orEmpty(),
                flowBeeKey = flowBeeProvider?.apiKey.orEmpty(),
            )
        )
    }

    override fun speak(text: String, flushCalled: Boolean) {
        val issue = _setupInput.value
        if (issue != null) {
            // 配置不全（典型：内置音色还没填 FlowBee 密钥）时就地拦下并给出可操作提示。
            // 真发出去只会换回一个 401，用户既看不懂，也不知道该改哪儿。
            _setupError.value = issue
            return
        }
        _setupError.value = null
        val processed = text.stripMarkdown()
        controller.speak(processed, flushCalled)
    }

    override fun stop() {
        controller.stop()
    }

    override fun pause() {
        controller.pause()
        Log.d("CustomTtsState", "TTS paused")
    }

    override fun resume() {
        controller.resume()
        Log.d("CustomTtsState", "TTS resumed")
    }

    override fun skipNext() {
        controller.skipNext()
    }

    override fun fastForward(ms: Long) {
        controller.fastForward(ms)
    }

    override fun setSpeed(speed: Float) {
        controller.setSpeed(speed)
    }

    override fun cleanup() {
        controller.dispose()
        currentJob = null
    }
}

/**
 * 内置的 FlowBee 提供商 —— 用户在设置页填密钥的那一条。
 *
 * 设置页的 FlowBee 卡片与提供商详情页读写的是同一份数据（见 FLOWBEE_PROVIDER_ID 的注释），
 * 所以读它就能拿到用户填的中转站地址与密钥，不需要第二份存储。
 */
private fun Settings.flowBeeProvider(): ProviderSetting.OpenAI? =
    providers
        .filterIsInstance<ProviderSetting.OpenAI>()
        .firstOrNull { it.id == FLOWBEE_PROVIDER_ID }

/**
 * 给内置 TTS provider 补上 FlowBee 的密钥，让用户只需在设置页填一次密钥。
 *
 * 两条同时满足才补：
 * - `apiKey` 为空 —— 用户自己填过密钥的 provider 一律不动；
 * - `baseUrl` 与 FlowBee 提供商**同源** —— 说明它走的是同一个中转站。
 *
 * 刻意按「baseUrl 同源」而不是 provider 类型来判断：以后新增别的类型的内置音色
 * （MiniMax / Step / …），只要走同一个中转站就自动生效，不必回来改这个函数。
 * 反过来，将来若有内置音色直连上游，baseUrl 不同源，不会被误填成用户的密钥。
 */
private fun TTSProviderSetting.withFlowBeeApiKeyFallback(
    flowBeeBaseUrl: String,
    flowBeeKey: String,
): TTSProviderSetting {
    if (flowBeeKey.isBlank()) return this
    val relayOrigin = flowBeeBaseUrl.relayOrigin() ?: return this

    fun shouldFill(apiKey: String, baseUrl: String): Boolean =
        apiKey.isBlank() && baseUrl.relayOrigin() == relayOrigin

    return when (this) {
        is TTSProviderSetting.OpenAI ->
            if (shouldFill(apiKey, baseUrl)) copy(apiKey = flowBeeKey) else this
        is TTSProviderSetting.Gemini ->
            if (shouldFill(apiKey, baseUrl)) copy(apiKey = flowBeeKey) else this
        is TTSProviderSetting.MiniMax ->
            if (shouldFill(apiKey, baseUrl)) copy(apiKey = flowBeeKey) else this
        is TTSProviderSetting.Qwen ->
            if (shouldFill(apiKey, baseUrl)) copy(apiKey = flowBeeKey) else this
        is TTSProviderSetting.Groq ->
            if (shouldFill(apiKey, baseUrl)) copy(apiKey = flowBeeKey) else this
        is TTSProviderSetting.XAI ->
            if (shouldFill(apiKey, baseUrl)) copy(apiKey = flowBeeKey) else this
        is TTSProviderSetting.MiMo ->
            if (shouldFill(apiKey, baseUrl)) copy(apiKey = flowBeeKey) else this
        is TTSProviderSetting.ElevenLabs ->
            if (shouldFill(apiKey, baseUrl)) copy(apiKey = flowBeeKey) else this
        is TTSProviderSetting.Step ->
            if (shouldFill(apiKey, baseUrl)) copy(apiKey = flowBeeKey) else this
        is TTSProviderSetting.FishAudio ->
            if (shouldFill(apiKey, baseUrl)) copy(apiKey = flowBeeKey) else this
        // 系统 TTS 不联网，没有密钥可说。
        is TTSProviderSetting.SystemTTS -> this
    }
}

/** `https://flowbee.top/v1` → `https://flowbee.top`；解析不出（含空串）返回 null。 */
private fun String.relayOrigin(): String? {
    if (isBlank()) return null
    return try {
        val uri = java.net.URI(this)
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        "$scheme://$host".lowercase()
    } catch (_: Exception) {
        null
    }
}

/**
 * 当前选中的朗读服务是否缺必要配置 —— 目前只有一种情况：选中项指向 FlowBee 中转站，
 * 但用户还没填 FlowBee 密钥（密钥本应由 [withFlowBeeApiKeyFallback] 回退过来）。
 *
 * 配置完整返回 null，否则返回可直接展示给用户的提示文案 [hint]。
 */
fun Settings.ttsSetupHint(hint: String): String? {
    val provider = getSelectedTTSProvider() ?: return null
    val flowBee = flowBeeProvider() ?: return null
    return if (provider.needsFlowBeeKey(flowBee.baseUrl, flowBee.apiKey)) hint else null
}

/** 选中项与 FlowBee 中转站同源，且既没自带密钥、也拿不到回退密钥。 */
private fun TTSProviderSetting.needsFlowBeeKey(
    flowBeeBaseUrl: String,
    flowBeeKey: String,
): Boolean {
    if (this is TTSProviderSetting.SystemTTS) return false // 系统 TTS 不联网，不需要密钥
    if (flowBeeKey.isNotBlank()) return false // 有密钥，回退逻辑就够了
    val relayOrigin = flowBeeBaseUrl.relayOrigin() ?: return false
    return apiKeyOrEmpty.isBlank() && baseUrlOrEmpty.relayOrigin() == relayOrigin
}

/** 各 provider 的密钥字段名不统一（有的叫 apiKey，有的没有），这里统一取一下。 */
private val TTSProviderSetting.apiKeyOrEmpty: String
    get() = when (this) {
        is TTSProviderSetting.OpenAI -> apiKey
        is TTSProviderSetting.Gemini -> apiKey
        is TTSProviderSetting.MiniMax -> apiKey
        is TTSProviderSetting.Qwen -> apiKey
        is TTSProviderSetting.Groq -> apiKey
        is TTSProviderSetting.XAI -> apiKey
        is TTSProviderSetting.MiMo -> apiKey
        is TTSProviderSetting.ElevenLabs -> apiKey
        is TTSProviderSetting.Step -> apiKey
        is TTSProviderSetting.FishAudio -> apiKey
        is TTSProviderSetting.SystemTTS -> ""
    }

private val TTSProviderSetting.baseUrlOrEmpty: String
    get() = when (this) {
        is TTSProviderSetting.OpenAI -> baseUrl
        is TTSProviderSetting.Gemini -> baseUrl
        is TTSProviderSetting.MiniMax -> baseUrl
        is TTSProviderSetting.Qwen -> baseUrl
        is TTSProviderSetting.Groq -> baseUrl
        is TTSProviderSetting.XAI -> baseUrl
        is TTSProviderSetting.MiMo -> baseUrl
        is TTSProviderSetting.ElevenLabs -> baseUrl
        is TTSProviderSetting.Step -> baseUrl
        is TTSProviderSetting.FishAudio -> baseUrl
        is TTSProviderSetting.SystemTTS -> ""
    }
