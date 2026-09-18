package me.rerere.rikkahub.ui.pages.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.registry.ModelRegistry
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.datastore.FLOWBEE_PROVIDER_ID
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.flowbee.FlowBeeCheck
import me.rerere.rikkahub.data.flowbee.FlowBeeClient
import me.rerere.rikkahub.data.flowbee.FlowBeeDefaults

/** 余额项的展示状态。 */
sealed interface FlowBeeBalance {
    /** 还没填 key，或还没查过 */
    data object Unknown : FlowBeeBalance

    data object Loading : FlowBeeBalance

    /** [text] 已按站点展示口径换算，如 `¥12.34` */
    data class Available(val text: String) : FlowBeeBalance

    /** key 存在但校验不通过 / 网络异常 */
    data class Invalid(val message: String) : FlowBeeBalance
}

/** 密钥弹窗内的瞬时状态。 */
sealed interface FlowBeeKeyCheck {
    data object Idle : FlowBeeKeyCheck

    data object Checking : FlowBeeKeyCheck

    data class Failed(val message: String) : FlowBeeKeyCheck
}

/** 模型列表自动导入的进行状态。 */
sealed interface FlowBeeModelImport {
    data object Idle : FlowBeeModelImport

    data object Importing : FlowBeeModelImport

    data object Failed : FlowBeeModelImport
}

class SettingVM(
    private val settingsStore: SettingsStore,
    private val mcpManager: McpManager,
    private val flowBeeClient: FlowBeeClient,
    private val providerManager: ProviderManager,
) :
    ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings(init = true, providers = emptyList()))

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }

    // ------------------------------------------------------------------
    // FlowBee
    //
    // 这里写的就是「内置 FlowBee 提供商」那一份数据，不做第二份存储：
    // 设置页填 key == 提供商详情页填 key，只是入口不同。
    // ------------------------------------------------------------------

    /**
     * 按固定 Uuid 定位内置提供商。
     *
     * 因为走的是 Uuid 而不是「名字叫 FlowBee 的第一个」，用户自己新建的 FlowBee 配置
     * 天然不受设置页影响，两者互不干扰。
     */
    private val flowBeeProvider: ProviderSetting.OpenAI?
        get() = settings.value.flowBeeProviderSetting()

    private fun flowBeeSiteRoot(): String =
        FlowBeeDefaults.siteRootOf(flowBeeProvider?.baseUrl ?: FlowBeeDefaults.SITE_URL)

    private val _flowBeeBalance = MutableStateFlow<FlowBeeBalance>(FlowBeeBalance.Unknown)
    val flowBeeBalance: StateFlow<FlowBeeBalance> = _flowBeeBalance.asStateFlow()

    private val _keyDialogVisible = MutableStateFlow(false)
    val keyDialogVisible: StateFlow<Boolean> = _keyDialogVisible.asStateFlow()

    private val _keyCheck = MutableStateFlow<FlowBeeKeyCheck>(FlowBeeKeyCheck.Idle)
    val keyCheck: StateFlow<FlowBeeKeyCheck> = _keyCheck.asStateFlow()

    private val _modelImport = MutableStateFlow<FlowBeeModelImport>(FlowBeeModelImport.Idle)
    val modelImport: StateFlow<FlowBeeModelImport> = _modelImport.asStateFlow()

    fun openKeyDialog() {
        _keyCheck.value = FlowBeeKeyCheck.Idle
        _keyDialogVisible.value = true
    }

    fun dismissKeyDialog() {
        _keyCheck.value = FlowBeeKeyCheck.Idle
        _keyDialogVisible.value = false
    }

    /**
     * 校验候选密钥 → 落盘 → 自动导入模型。
     *
     * 顺序很重要：先验证再落盘。否则用户输入一个错 key 也会被保存，
     * 界面上就出现了「已连接」却用不了的假阳性。
     *
     * 密钥写回后立刻关掉弹窗，模型导入在后台继续：
     * 拉模型列表比校验密钥慢，而且**失败也不影响密钥生效**——密钥本身是有效的，
     * 用户仍可以在「提供商 → FlowBee」里手动拉取模型。
     */
    fun connectFlowBee(candidateKey: String) {
        viewModelScope.launch {
            _keyCheck.value = FlowBeeKeyCheck.Checking
            val siteRoot = flowBeeSiteRoot()
            when (val result = flowBeeClient.check(candidateKey, siteRoot)) {
                is FlowBeeCheck.Connected -> {
                    val key = FlowBeeDefaults.normalizeKey(candidateKey)
                    // store.update 会同步更新内存里的 settingsFlow，所以下面这次写入
                    // 之后 importFlowBeeModels 读到的已经是带新密钥的最新值。
                    settingsStore.update(
                        settingsStore.settingsFlow.value.withFlowBee(key = key, imported = emptyList())
                    )
                    _keyCheck.value = FlowBeeKeyCheck.Idle
                    _keyDialogVisible.value = false
                    importFlowBeeModels(key)
                }

                is FlowBeeCheck.Failed -> {
                    _keyCheck.value = FlowBeeKeyCheck.Failed(result.message)
                }
            }
        }
    }

    /**
     * 进入设置页时自动跑一次，让「已连接」反映真实可用性，顺带把余额带出来。
     *
     * 顺带补一次模型导入：覆盖「装了新版本但模型还是空的」老用户，
     * 以及上次导入失败的情况。已有模型时不重复拉取，避免每次进设置都多打一次请求。
     */
    fun refreshFlowBeeBalance() {
        val key = flowBeeProvider?.apiKey.orEmpty()
        if (key.isBlank()) {
            _flowBeeBalance.value = FlowBeeBalance.Unknown
            return
        }
        viewModelScope.launch {
            _flowBeeBalance.value = FlowBeeBalance.Loading
            val siteRoot = flowBeeSiteRoot()
            _flowBeeBalance.value = when (val result = flowBeeClient.check(key, siteRoot)) {
                is FlowBeeCheck.Connected -> FlowBeeBalance.Available(result.balanceText)
                is FlowBeeCheck.Failed -> FlowBeeBalance.Invalid(result.message)
            }
            val hasModels = flowBeeProvider?.models?.isNotEmpty() == true
            if (_flowBeeBalance.value is FlowBeeBalance.Available && !hasModels) {
                importFlowBeeModels(key)
            }
        }
    }

    /**
     * 把默认模型切到 FlowBee。
     *
     * 只动**全局默认模型**（`chatModelId`），不碰任何助手上单独指定的模型 ——
     * 那是用户自己的配置，替他改属于越界，而且影响范围不可预期。
     *
     * 挑哪个模型复用导入时同一套规则（[pickDefaultChatModel]），避免两处规则各自演化。
     */
    fun adoptFlowBeeDefaultModel() {
        viewModelScope.launch {
            val current = settingsStore.settingsFlow.value
            val model = current.flowBeeModels().pickDefaultChatModel() ?: return@launch
            settingsStore.update(current.copy(chatModelId = model.id))
        }
    }

    /**
     * 用户明确表示「我知道 FlowBee 能用，但我就是要用别的当默认」，之后不再提醒。
     *
     * 这是个合理诉求（比如两家并用），所以给出口而不是反复提示。
     */
    fun dismissFlowBeeDefaultHint() {
        viewModelScope.launch {
            val current = settingsStore.settingsFlow.value
            if (!current.flowBeeDefaultHintDismissed) {
                settingsStore.update(current.copy(flowBeeDefaultHintDismissed = true))
            }
        }
    }

    /**
     * 把站点模型列表合并进内置 FlowBee 提供商。
     *
     * 两条路径都走这里：连接成功后自动导入、以及「密钥已经在设置里但模型还是空的」补导入。
     * 只改 `models`（和必要时补的默认模型），其余字段沿用当前设置里的值。
     */
    private suspend fun importFlowBeeModels(key: String) {
        _modelImport.value = FlowBeeModelImport.Importing
        val remote = runCatching { fetchRemoteModels(key) }.getOrElse {
            _modelImport.value = FlowBeeModelImport.Failed
            return
        }
        val current = settingsStore.settingsFlow.value
        val updated = current.withFlowBee(key = null, imported = remote)
        if (updated.flowBeeModels() != current.flowBeeModels()) {
            settingsStore.update(updated)
        }
        _modelImport.value = FlowBeeModelImport.Idle
    }

    /**
     * 用候选密钥拉一次 `/v1/models`。
     *
     * 刻意复用 [ProviderManager]：这条链路和「提供商详情页 → 拉取模型列表」完全一致，
     * 不会出现「自动导入能拉到、手动拉取拉不到」这种两套逻辑各自演化的问题。
     */
    private suspend fun fetchRemoteModels(key: String): List<Model> {
        val probe = flowBeeProvider?.copy(apiKey = key) ?: return emptyList()
        return providerManager.getProviderByType(probe).listModels(probe)
    }
}

/**
 * 把远端模型合并进已有列表：只补新 modelId，不动用户已经改过参数的老条目。
 *
 * 与「提供商详情页 → 全选」保持一致，同样用 [ModelRegistry] 猜测模型的模态与能力，
 * 否则新导入的模型会缺少「视觉输入 / 思考」这类标记，在聊天页表现为功能突然消失。
 */
private fun List<Model>.mergeRemoteModels(remote: List<Model>): List<Model> =
    this + remote
        .filter { it.modelId.isNotBlank() }
        .distinctBy { it.modelId }
        .filter { model -> this.none { it.modelId == model.modelId } }
        .map { model ->
            model.copy(
                displayName = model.displayName.ifBlank { model.modelId },
                inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(model.modelId),
                outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(model.modelId),
                abilities = ModelRegistry.MODEL_ABILITIES.getData(model.modelId),
            )
        }

/**
 * 写回内置 FlowBee 提供商。
 *
 * 只改这一个 Uuid 的条目，其余 provider（包括用户自建的）原样保留。
 *
 * @param key 新密钥；传 `null` 表示密钥不变（补模型时走这条）
 * @param imported 本次要合并进来的远端模型
 */
private fun Settings.withFlowBee(key: String?, imported: List<Model>): Settings {
    val target = flowBeeProviderSetting() ?: return this
    val merged = target.models.mergeRemoteModels(imported)
    val updatedProviders = providers.map { provider ->
        if (provider.id == FLOWBEE_PROVIDER_ID && provider is ProviderSetting.OpenAI) {
            provider.copy(
                apiKey = key ?: provider.apiKey,
                // 只在「填入新密钥」时顺手启用，补模型时不要动用户手动关掉的开关
                enabled = if (key != null) true else provider.enabled,
                models = merged,
            )
        } else {
            provider
        }
    }
    // 只有「当前选中的模型根本不存在」时才顺手设一个默认模型，让用户贴完密钥就能直接开聊。
    // 已经有可用选择时一律不碰 —— 否则会把用户挑好的模型顶掉。
    val needDefault = findModelById(chatModelId) == null
    return copy(
        providers = updatedProviders,
        chatModelId = if (needDefault) merged.pickDefaultChatModel()?.id ?: chatModelId else chatModelId,
    )
}

/**
 * 挑一个适合当默认的聊天模型。
 *
 * 站点返回的顺序不代表可用性，第一个可能是向量 / 语音 / 绘图模型，选中后聊天会直接报错。
 * 所以先跳过名字一看就不是聊天的，全被跳过时再退回第一个。
 */
private fun List<Model>.pickDefaultChatModel(): Model? =
    firstOrNull { model -> !NON_CHAT_MODEL_HINT.containsMatchIn(model.modelId) } ?: firstOrNull()

private val NON_CHAT_MODEL_HINT =
    Regex("embed|rerank|tts|whisper|speech|audio|image|video|moderation", RegexOption.IGNORE_CASE)

/** 取内置 FlowBee 提供商的模型列表；不存在时返回空列表。 */
private fun Settings.flowBeeModels(): List<Model> = flowBeeProviderSetting()?.models.orEmpty()

private fun Settings.flowBeeProviderSetting(): ProviderSetting.OpenAI? = providers
    .filterIsInstance<ProviderSetting.OpenAI>()
    .firstOrNull { it.id == FLOWBEE_PROVIDER_ID }
