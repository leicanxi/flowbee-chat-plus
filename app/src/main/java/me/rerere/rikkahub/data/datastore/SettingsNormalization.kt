package me.rerere.rikkahub.data.datastore

/** 读盘后和每次修改后都要过一遍，保证内存里的设置始终是规整的。 */
internal fun Settings.normalized(): Settings =
    withBuiltInDefaults().withoutInvalidReferences().withValuesInRange()

// 补齐缺失的内置提供商/助手/TTS，内置提供商和推荐提供商的描述信息始终以代码里的为准
internal fun Settings.withBuiltInDefaults(): Settings {
    var providers = this.providers.ifEmpty { DEFAULT_PROVIDERS }.toMutableList()
    // 下架的内置提供商（老版本已写进 DataStore 且 builtIn 导致用户删不掉）在这里剔除；
    // 填过密钥的保留，不当着用户的面删他自己的配置。
    providers.removeAll { provider ->
        provider.id in REMOVED_BUILT_IN_PROVIDER_IDS && !provider.hasUserKey
    }
    DEFAULT_PROVIDERS.forEach { defaultProvider ->
        if (providers.none { it.id == defaultProvider.id }) {
            providers.add(defaultProvider.copyProvider())
        }
    }
    providers = providers.map { provider ->
        val defaultProvider = DEFAULT_PROVIDERS.find { it.id == provider.id }
        val recommendedProvider = RECOMMENDED_PROVIDERS.find { it.id == provider.id }
        when {
            defaultProvider != null -> provider.copyProvider(
                builtIn = defaultProvider.builtIn,
                description = defaultProvider.description,
                shortDescription = defaultProvider.shortDescription,
            )

            // 描述不落盘，从推荐列表添加的提供商读盘后按 id 补回
            recommendedProvider != null -> provider.copyProvider(
                description = recommendedProvider.description,
                shortDescription = recommendedProvider.shortDescription,
            )

            else -> provider
        }
    }.toMutableList()

    // 上面新增的内置 provider 是追加到列表末尾的，而 FlowBee 是这个版本的主入口，
    // 排在首位才容易被找到。sortedBy 是稳定排序，其余 provider 的相对顺序不受影响。
    providers = providers.sortedBy { if (it.id == FLOWBEE_PROVIDER_ID) 0 else 1 }.toMutableList()
    val assistants = this.assistants.ifEmpty { DEFAULT_ASSISTANTS }.toMutableList()
    DEFAULT_ASSISTANTS.forEach { defaultAssistant ->
        if (assistants.none { it.id == defaultAssistant.id }) {
            assistants.add(defaultAssistant.copy())
        }
    }
    val ttsProviders = this.ttsProviders.ifEmpty { DEFAULT_TTS_PROVIDERS }.toMutableList()
    DEFAULT_TTS_PROVIDERS.forEach { defaultTTSProvider ->
        if (ttsProviders.none { provider -> provider.id == defaultTTSProvider.id }) {
            ttsProviders.add(defaultTTSProvider.copyProvider())
        }
    }
    // 内置音色改名的存量纠偏：老版本 DataStore 里存的是「Moss TTS」，就地改成新名字。
    // 只精确匹配旧名字，用户自己改过名的不会被覆盖，也不会反复触发。
    val mossIndex = ttsProviders.indexOfFirst { provider ->
        provider.id == DEFAULT_MOSS_TTS_ID && provider.name == "Moss TTS"
    }
    if (mossIndex >= 0) {
        ttsProviders[mossIndex] = ttsProviders[mossIndex].copyProvider(name = "哄睡daddy")
    }
    return copy(
        providers = providers,
        assistants = assistants,
        ttsProviders = ttsProviders,
    )
}

// 去重并清理无效引用
internal fun Settings.withoutInvalidReferences(): Settings {
    val settings = this
    val validMcpServerIds = settings.mcpServers.map { it.id }.toSet()
    val validModeInjectionIds = settings.modeInjections.map { it.id }.toSet()
    val validLorebookIds = settings.lorebooks.map { it.id }.toSet()
    val validQuickMessageIds = settings.quickMessages.map { it.id }.toSet()
    val validModelIds by lazy { settings.providers.flatMap { it.models }.mapTo(HashSet()) { it.id } }
    val asrProviders = settings.asrProviders.distinctBy { it.id }
    return settings.copy(
        providers = settings.providers.distinctBy { it.id }.map { provider ->
            provider.copyProvider(models = provider.models.distinctBy { model -> model.id })
        },
        assistants = settings.assistants.distinctBy { it.id }.map { assistant ->
            assistant.copy(
                // 过滤掉不存在的 MCP 服务器 ID
                mcpServers = assistant.mcpServers.filter { serverId ->
                    serverId in validMcpServerIds
                }.toSet(),
                // 过滤掉不存在的模式注入 ID
                modeInjectionIds = assistant.modeInjectionIds.filter { id ->
                    id in validModeInjectionIds
                }.toSet(),
                // 过滤掉不存在的 Lorebook ID
                lorebookIds = assistant.lorebookIds.filter { id ->
                    id in validLorebookIds
                }.toSet(),
                // 过滤掉不存在的快捷消息 ID
                quickMessageIds = assistant.quickMessageIds.filter { id ->
                    id in validQuickMessageIds
                }.toSet()
            )
        },
        ttsProviders = settings.ttsProviders.distinctBy { it.id },
        asrProviders = asrProviders,
        selectedASRProviderId = settings.selectedASRProviderId
            ?.takeIf { id -> asrProviders.any { provider -> provider.id == id } }
            ?: asrProviders.firstOrNull()?.id,
        favoriteModels = settings.favoriteModels.filter { it in validModelIds }.distinct(),
        modeInjections = settings.modeInjections.distinctBy { it.id },
        lorebooks = settings.lorebooks.distinctBy { it.id },
        quickMessages = settings.quickMessages.distinctBy { it.id },
    )
}

// 把取值收回合法范围，比如删掉搜索服务后选中的下标可能越界
private fun Settings.withValuesInRange(): Settings = copy(
    searchServiceSelected = searchServiceSelected.coerceIn(0, (searchServices.size - 1).coerceAtLeast(0)),
    defaultTTSPlaybackSpeed = defaultTTSPlaybackSpeed.coerceIn(0.5f, 2.0f),
    autoCompressThreshold = autoCompressThreshold.coerceIn(AUTO_COMPRESS_THRESHOLD_RANGE),
)

/** 自动压缩触发比例的可选范围，设置页的滑块也用它 */
val AUTO_COMPRESS_THRESHOLD_RANGE = 0.5f..0.95f
