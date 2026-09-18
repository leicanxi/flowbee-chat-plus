package me.rerere.rikkahub.ui.pages.setting

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiMagic
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Book01
import me.rerere.hugeicons.stroke.Book03
import me.rerere.hugeicons.stroke.Bookshelf01
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Clapping01
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.GlobalSearch
import me.rerere.hugeicons.stroke.ImageUpload
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.InformationCircle
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.McpServer
import me.rerere.hugeicons.stroke.Megaphone01
import me.rerere.hugeicons.stroke.Package
import me.rerere.hugeicons.stroke.ServerStack01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Share04
import me.rerere.hugeicons.stroke.Sun01
import me.rerere.hugeicons.stroke.Tiktok
import me.rerere.hugeicons.stroke.Wallet01
import me.rerere.hugeicons.stroke.WavingHand01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.FLOWBEE_PROVIDER_ID
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.isNotConfigured
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.flowbee.FlowBeeDefaults
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.icons.DiscordIcon
import me.rerere.rikkahub.ui.components.ui.icons.TencentQQIcon
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.rememberColorMode
import me.rerere.rikkahub.ui.theme.ColorMode
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.utils.joinQQGroup
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.utils.plus
import me.rerere.rikkahub.utils.writeClipboardText
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun SettingPage(vm: SettingVM = koinViewModel()) {
    val navController = LocalNavController.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val filesManager: FilesManager = koinInject()
    var colorMode by rememberColorMode()

    // 内置 FlowBee 提供商：设置页只是它的一个快捷入口，读写的是同一份 Settings。
    val flowBeeProvider = remember(settings.providers) {
        settings.providers
            .filterIsInstance<ProviderSetting.OpenAI>()
            .firstOrNull { it.id == FLOWBEE_PROVIDER_ID }
    }
    val flowBeeApiKey = flowBeeProvider?.apiKey.orEmpty()
    val flowBeeBalance by vm.flowBeeBalance.collectAsStateWithLifecycle()
    val flowBeeModelImport by vm.modelImport.collectAsStateWithLifecycle()
    val keyDialogVisible by vm.keyDialogVisible.collectAsStateWithLifecycle()
    val keyCheck by vm.keyCheck.collectAsStateWithLifecycle()

    // 「FlowBee 已经能用了，但默认模型还是别家的」这个中间态才提示。
    //
    // 条件是状态驱动而不是「弹过一次」：用户一旦把默认切到 FlowBee，或明确点过「不用了」，
    // 这里就自动不成立，卡片自己消失，不需要额外的清理逻辑。
    val flowBeeModels = flowBeeProvider?.models.orEmpty()
    val showFlowBeeDefaultHint = flowBeeModels.isNotEmpty() &&
        !settings.flowBeeDefaultHintDismissed &&
        flowBeeModels.none { it.id == settings.chatModelId }

    // 提示里点名当前默认模型，用户能立刻对上「现在聊天用的是哪个」。
    // 解析不出模型时（模型被删、或默认值还是个哨兵）退回一个中性说法，避免句子读不通。
    val flowBeeDefaultModelName = settings.findModelById(settings.chatModelId)?.displayName
        ?: stringResource(R.string.setting_page_flowbee_default_hint_unknown_model)

    // 进入设置页、或密钥被别处改过（比如「提供商」详情页）时重新校验一次。
    // 「已连接」因此反映的是真实可用性，而不是「这里填过字」。
    LaunchedEffect(flowBeeApiKey, flowBeeProvider?.baseUrl) {
        vm.refreshFlowBeeBalance()
    }

    if (settings.launchCount > 100 && (settings.launchCount - settings.sponsorAlertDismissedAt) >= 50) {
        AlertDialog(
            onDismissRequest = {
                vm.updateSettings(settings.copy(sponsorAlertDismissedAt = settings.launchCount))
            },
            icon = { Icon(HugeIcons.WavingHand01, null) },
            title = { Text(stringResource(R.string.setting_page_sponsor_alert_title)) },
            text = { Text(stringResource(R.string.setting_page_sponsor_alert_desc)) },
            confirmButton = {
                Button(onClick = {
                    vm.updateSettings(settings.copy(sponsorAlertDismissedAt = settings.launchCount))
                    navController.navigate(Screen.SettingDonate)
                }) {
                    Text(stringResource(R.string.setting_page_sponsor_alert_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.updateSettings(settings.copy(sponsorAlertDismissedAt = settings.launchCount))
                }) {
                    Text(stringResource(R.string.setting_page_sponsor_alert_dismiss))
                }
            },
        )
    }

    if (keyDialogVisible) {
        FlowBeeKeyDialog(
            initialKey = flowBeeApiKey,
            checking = keyCheck is FlowBeeKeyCheck.Checking,
            errorMessage = (keyCheck as? FlowBeeKeyCheck.Failed)?.message,
            onDismiss = { vm.dismissKeyDialog() },
            onConfirm = { vm.connectFlowBee(it) },
        )
    }

    SettingPageContent(
        settings = settings,
        colorMode = colorMode,
        flowBeeHasKey = flowBeeApiKey.isNotBlank(),
        flowBeeBalance = flowBeeBalance,
        flowBeeModelImport = flowBeeModelImport,
        flowBeeModelCount = flowBeeProvider?.models?.size ?: 0,
        showFlowBeeDefaultHint = showFlowBeeDefaultHint,
        flowBeeDefaultModelName = flowBeeDefaultModelName,
        onFlowBeeInputKey = { vm.openKeyDialog() },
        onFlowBeeRefreshBalance = { vm.refreshFlowBeeBalance() },
        onFlowBeeAdoptDefault = { vm.adoptFlowBeeDefaultModel() },
        onFlowBeeDismissHint = { vm.dismissFlowBeeDefaultHint() },
        onColorModeSelected = { selected ->
            colorMode = selected
            // 配色改变后重建页面，让整棵树的主题立即生效
            navController.navigate(Screen.Setting) {
                popUpTo(Screen.Setting) {
                    inclusive = true
                }
            }
        },
        onNavigate = { navController.navigate(it) },
        countChatFiles = { filesManager.countChatFiles() },
    )
}

@Composable
private fun SettingPageContent(
    settings: Settings,
    colorMode: ColorMode,
    flowBeeHasKey: Boolean,
    flowBeeBalance: FlowBeeBalance,
    flowBeeModelImport: FlowBeeModelImport,
    flowBeeModelCount: Int,
    showFlowBeeDefaultHint: Boolean,
    flowBeeDefaultModelName: String,
    onFlowBeeInputKey: () -> Unit,
    onFlowBeeRefreshBalance: () -> Unit,
    onFlowBeeAdoptDefault: () -> Unit,
    onFlowBeeDismissHint: () -> Unit,
    onColorModeSelected: (ColorMode) -> Unit,
    onNavigate: (Screen) -> Unit,
    countChatFiles: suspend () -> Pair<Int, Long>,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(R.string.settings))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (settings.isNotConfigured()) {
                item {
                    ProviderConfigWarningCard(onNavigate)
                }
            }

            item("flowBeeService") {
                FlowBeeCard(
                    hasKey = flowBeeHasKey,
                    balance = flowBeeBalance,
                    modelImport = flowBeeModelImport,
                    modelCount = flowBeeModelCount,
                    onInputKey = onFlowBeeInputKey,
                    onOpenSite = { onNavigate(Screen.WebView(url = FlowBeeDefaults.HOME_URL)) },
                    onOpenWallet = { onNavigate(Screen.WebView(url = FlowBeeDefaults.WALLET_URL)) },
                    onRefreshBalance = onFlowBeeRefreshBalance,
                )
            }

            if (showFlowBeeDefaultHint) {
                item("flowBeeDefaultHint") {
                    FlowBeeDefaultHintCard(
                        modelName = flowBeeDefaultModelName,
                        onAdopt = onFlowBeeAdoptDefault,
                        onDismiss = onFlowBeeDismissHint,
                    )
                }
            }

            item("generalSettings") {
                val selectedColorModeText = when (colorMode) {
                    ColorMode.SYSTEM -> stringResource(R.string.setting_page_color_mode_system)
                    ColorMode.LIGHT -> stringResource(R.string.setting_page_color_mode_light)
                    ColorMode.DARK -> stringResource(R.string.setting_page_color_mode_dark)
                }
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_general_settings)) },
                ) {
                    item(
                        leadingContent = { Icon(HugeIcons.Sun01, null) },
                        trailingContent = {
                            Select(
                                options = ColorMode.entries,
                                selectedOption = colorMode,
                                onOptionSelected = onColorModeSelected,
                                optionToString = {
                                    when (it) {
                                        ColorMode.SYSTEM -> stringResource(R.string.setting_page_color_mode_system)
                                        ColorMode.LIGHT -> stringResource(R.string.setting_page_color_mode_light)
                                        ColorMode.DARK -> stringResource(R.string.setting_page_color_mode_dark)
                                    }
                                },
                                modifier = Modifier.width(150.dp)
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.setting_page_color_mode)) },
                        supportingContent = { Text(selectedColorModeText) },
                    )
                    item(
                        onClick = { onNavigate(Screen.SettingPreferences) },
                        leadingContent = { Icon(HugeIcons.Settings03, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences)) },
                    )
                    item(
                        onClick = { onNavigate(Screen.Assistant) },
                        leadingContent = { Icon(HugeIcons.LookTop, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_assistant_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_assistant)) },
                    )
                    item(
                        onClick = { onNavigate(Screen.Extensions) },
                        leadingContent = { Icon(HugeIcons.Package, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_extensions_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_extensions)) },
                    )
                }
            }

            item("modelServices") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_model_and_services)) },
                ) {
                    item(
                        onClick = { onNavigate(Screen.SettingModels) },
                        leadingContent = { Icon(HugeIcons.AiMagic, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_default_model_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_default_model)) },
                    )
                    item(
                        onClick = { onNavigate(Screen.SettingProvider) },
                        leadingContent = { Icon(HugeIcons.Brain02, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_providers_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_providers)) },
                    )
                    item(
                        onClick = { onNavigate(Screen.SettingSearch) },
                        leadingContent = { Icon(HugeIcons.GlobalSearch, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_search_service_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_search_service)) },
                    )
                    item(
                        onClick = { onNavigate(Screen.SettingSpeech) },
                        leadingContent = { Icon(HugeIcons.Megaphone01, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_tts_service_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_tts_service)) },
                    )
                    item(
                        onClick = { onNavigate(Screen.SettingMcp) },
                        leadingContent = { Icon(HugeIcons.McpServer, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_mcp_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_mcp)) },
                    )
                    item(
                        onClick = { onNavigate(Screen.SettingWeb) },
                        leadingContent = { Icon(HugeIcons.ServerStack01, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_web_server_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_web_server)) },
                    )
                }
            }

            item("dataSettings") {
                val storageState by produceState(-1 to 0L) {
                    value = countChatFiles()
                }
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_data_settings)) },
                ) {
                    item(
                        onClick = { onNavigate(Screen.Backup) },
                        leadingContent = { Icon(HugeIcons.Database02, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_data_backup_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_data_backup)) },
                    )
                    item(
                        onClick = { onNavigate(Screen.SettingFiles) },
                        leadingContent = { Icon(HugeIcons.ImageUpload, null) },
                        supportingContent = {
                            if (storageState.first == -1) {
                                Text(stringResource(R.string.calculating))
                            } else {
                                Text(
                                    stringResource(
                                        R.string.setting_page_chat_storage_desc,
                                        storageState.first,
                                        storageState.second / 1024 / 1024.0
                                    )
                                )
                            }
                        },
                        headlineContent = { Text(stringResource(R.string.setting_page_chat_storage)) },
                    )
                }
            }

            item("aboutSettings") {
                val context = LocalContext.current
                val shareText = stringResource(R.string.setting_page_share_text)
                val share = stringResource(R.string.setting_page_share)
                val noShareApp = stringResource(R.string.setting_page_no_share_app)
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_about)) },
                ) {
                    item(
                        onClick = { onNavigate(Screen.SettingAbout) },
                        leadingContent = { Icon(HugeIcons.Clapping01, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_about_desc)) },
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                var showQQGroupSheet by remember { mutableStateOf(false) }
                                IconButton(
                                    onClick = { showQQGroupSheet = true }
                                ) {
                                    Icon(
                                        imageVector = TencentQQIcon,
                                        contentDescription = "QQ",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                if (showQQGroupSheet) {
                                    QQGroupBottomSheet(
                                        onDismiss = { showQQGroupSheet = false }
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        context.openUrl("https://discord.gg/9weBqxe5c4")
                                    }
                                ) {
                                    Icon(
                                        imageVector = DiscordIcon,
                                        contentDescription = "Discord",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        },
                        headlineContent = { Text(stringResource(R.string.setting_page_about)) },
                    )
                    item(
                        onClick = {
                            val docUrl = if (java.util.Locale.getDefault().language == "zh") {
                                "https://docs.rikka-ai.com/zh/introduction"
                            } else {
                                "https://docs.rikka-ai.com/introduction"
                            }
                            context.openUrl(docUrl)
                        },
                        leadingContent = { Icon(HugeIcons.Book01, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_documentation_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_documentation)) },
                    )
                    item(
                        onClick = { onNavigate(Screen.Log) },
                        leadingContent = { Icon(HugeIcons.Bookshelf01, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_request_logs_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_request_logs)) },
                    )
                    item(
                        onClick = { onNavigate(Screen.SettingDonate) },
                        leadingContent = { Icon(HugeIcons.InLove, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_donate_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_donate)) },
                    )
                    item(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND)
                            intent.type = "text/plain"
                            intent.putExtra(Intent.EXTRA_TEXT, shareText)
                            try {
                                context.startActivity(Intent.createChooser(intent, share))
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(context, noShareApp, Toast.LENGTH_SHORT).show()
                            }
                        },
                        leadingContent = { Icon(HugeIcons.Share04, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_share_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_share)) },
                    )
                }
            }
        }
    }
}

/**
 * FlowBee 服务卡片。
 *
 * 两个子项都保持固定行高、不展开：账号项点击弹窗输入密钥，余额项点击刷新。
 * 右侧的小字跳转分别去站点首页（拿密钥）和钱包页（充值）。
 *
 * 账号项的副标题承担「连接状态 + 模型数量 + 导入进度」三种信息，
 * 顺序就是用户关心的顺序：先知道通没通，再知道有多少模型可用。
 */
@Composable
private fun FlowBeeCard(
    hasKey: Boolean,
    balance: FlowBeeBalance,
    modelImport: FlowBeeModelImport,
    modelCount: Int,
    onInputKey: () -> Unit,
    onOpenSite: () -> Unit,
    onOpenWallet: () -> Unit,
    onRefreshBalance: () -> Unit,
) {
    CardGroup(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = { Text("FlowBee") },
    ) {
        item(
            onClick = onInputKey,
            leadingContent = {
                Image(
                    painter = painterResource(R.drawable.ic_flowbee),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            headlineContent = { Text(stringResource(R.string.setting_page_flowbee_account)) },
            supportingContent = {
                Text(
                    text = flowBeeAccountStatus(hasKey, balance, modelImport, modelCount),
                    color = if (hasKey && balance is FlowBeeBalance.Invalid) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.Unspecified
                    },
                )
            },
            trailingContent = {
                FlowBeeTrailingAction(
                    text = stringResource(R.string.setting_page_flowbee_open),
                    onClick = onOpenSite,
                )
            },
        )
        item(
            onClick = onRefreshBalance,
            leadingContent = { Icon(HugeIcons.Wallet01, null) },
            headlineContent = { Text(stringResource(R.string.setting_page_flowbee_balance)) },
            supportingContent = { Text(flowBeeBalanceText(hasKey, balance)) },
            trailingContent = {
                FlowBeeTrailingAction(
                    text = stringResource(R.string.setting_page_flowbee_topup),
                    onClick = onOpenWallet,
                )
            },
        )
    }
}

/**
 * 设置项右侧的小号跳转文字。
 *
 * 用 Text + clickable 而不是 TextButton：后者自带 40dp 最小高度，会把整行撑高，
 * 而这里要求子项高度保持一致。
 */
@Composable
private fun FlowBeeTrailingAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun flowBeeAccountStatus(
    hasKey: Boolean,
    balance: FlowBeeBalance,
    modelImport: FlowBeeModelImport,
    modelCount: Int,
): String {
    val connected = stringResource(R.string.setting_page_flowbee_connected)
    return when {
        !hasKey -> stringResource(R.string.setting_page_flowbee_disconnected)
        balance is FlowBeeBalance.Invalid -> balance.message
        balance is FlowBeeBalance.Loading -> stringResource(R.string.setting_page_flowbee_checking)
        modelImport is FlowBeeModelImport.Importing ->
            "$connected · ${stringResource(R.string.setting_page_flowbee_importing)}"

        modelImport is FlowBeeModelImport.Failed ->
            "$connected · ${stringResource(R.string.setting_page_flowbee_import_failed)}"

        modelCount > 0 ->
            "$connected · ${stringResource(R.string.setting_page_flowbee_model_count, modelCount)}"

        balance is FlowBeeBalance.Available -> connected
        else -> stringResource(R.string.setting_page_flowbee_disconnected)
    }
}

@Composable
private fun flowBeeBalanceText(hasKey: Boolean, balance: FlowBeeBalance): String = when {
    !hasKey -> stringResource(R.string.setting_page_flowbee_balance_unknown)
    balance is FlowBeeBalance.Available -> balance.text
    balance is FlowBeeBalance.Loading -> stringResource(R.string.setting_page_flowbee_balance_loading)
    balance is FlowBeeBalance.Invalid -> "—"
    else -> stringResource(R.string.setting_page_flowbee_balance_unknown)
}

/**
 * 「FlowBee 已经能用了，但默认模型还是别家的」提示卡。
 *
 * 卡片的显隐完全由状态决定（见 `SettingPage` 里的 `showFlowBeeDefaultHint`），
 * 所以这里不需要自己管理「弹过了」之类的标记：
 * 用户切过去 → 条件不成立 → 卡片消失；用户点「不用了」→ 落一个 dismissed 标记 → 也不再出现。
 *
 * 用 `tertiaryContainer` 而不是 `errorContainer`：这不是错误，只是一个建议，
 * 视觉上要和「未配置任何提供商」那种真错误区分开。
 */
@Composable
private fun FlowBeeDefaultHintCard(
    modelName: String,
    onAdopt: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier.padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = HugeIcons.InformationCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.setting_page_flowbee_default_hint_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.setting_page_flowbee_default_hint_desc, modelName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.setting_page_flowbee_default_hint_dismiss))
                }
                Button(onClick = onAdopt) {
                    Text(stringResource(R.string.setting_page_flowbee_default_hint_confirm))
                }
            }
        }
    }
}

/**
 * 密钥输入弹窗。
 *
 * 回车（软键盘 Done）即开始校验；**校验通过才写入设置**，避免把错 key 存进提供商后
 * 界面出现「已连接」却用不了的假阳性。
 */
@Composable
private fun FlowBeeKeyDialog(
    initialKey: String,
    checking: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var key by remember { mutableStateOf(initialKey) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = { if (!checking) onDismiss() },
        icon = {
            Image(
                painter = painterResource(R.drawable.ic_flowbee),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        },
        title = { Text(stringResource(R.string.setting_page_flowbee_key_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.setting_page_flowbee_key_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    enabled = !checking,
                    singleLine = true,
                    isError = errorMessage != null,
                    placeholder = { Text(stringResource(R.string.setting_page_flowbee_key_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (!checking && key.isNotBlank()) onConfirm(key)
                        }
                    ),
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (checking) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(R.string.setting_page_flowbee_checking),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(key) },
                enabled = !checking && key.isNotBlank(),
            ) {
                Text(stringResource(R.string.setting_page_flowbee_connect))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !checking) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ProviderConfigWarningCard(onNavigate: (Screen) -> Unit) {
    Card(
        modifier = Modifier.padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            ListItem(
                supportingContent = {
                    Text(stringResource(R.string.setting_page_config_api_desc))
                },
                leadingContent = {
                    Icon(HugeIcons.Alert01, null)
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent
                )
            ) {
                Text(stringResource(R.string.setting_page_config_api_title))
            }

            TextButton(
                onClick = {
                    onNavigate(Screen.SettingProvider)
                }
            ) {
                Text(stringResource(R.string.setting_page_config))
            }
        }
    }
}

private data class QQGroup(
    val name: String,
    val key: String? = null,
    val number: String? = null,
    val icon: ImageVector = TencentQQIcon,
)

private val QQ_GROUPS = listOf(
    QQGroup("RikkaHub 一群", "4POE46u9e_zoy1TkNfWdCvueR9CKFJdk"),
    QQGroup("RikkaHub 二群", "Qsm0whzbPsm1UyNpR683ulLyMZ2Pqrw0"),
    QQGroup("RikkaHub 三群", "Qc9oP-9tXioZeQEvEvI2_owWtBAIx3lS"),
    QQGroup("抖音一群", number = "569655479852", icon = HugeIcons.Tiktok),
)

@Composable
private fun QQGroupBottomSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            QQ_GROUPS.forEach { group ->
                ListItem(
                    onClick = {
                        if (group.number != null) {
                            context.writeClipboardText(group.number)
                            Toast.makeText(context, "群号已复制", Toast.LENGTH_SHORT).show()
                        } else {
                            context.joinQQGroup(group.key)
                        }
                        onDismiss()
                    },
                    supportingContent = group.number?.let { number ->
                        { Text(number) }
                    },
                    leadingContent = {
                        Icon(
                            imageVector = group.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    },
                ) {
                    Text(group.name)
                }
            }
        }
    }
}

/**
 * 设置页预览。使用 [RikkahubTheme] 的预览安全重载还原真实配色，并提供一个空的
 * [Navigator] 让顶栏的返回按钮能够渲染。微调本页样式时看这里即可，不必编译装机。
 */
@Preview(name = "浅色", showBackground = true, heightDp = 900)
@Preview(
    name = "深色",
    showBackground = true,
    heightDp = 900,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun SettingPagePreview() {
    RikkahubTheme(darkTheme = isSystemInDarkTheme()) {
        CompositionLocalProvider(
            LocalNavController provides remember { Navigator(mutableListOf()) }
        ) {
            SettingPageContent(
                settings = Settings.dummy(),
                colorMode = ColorMode.SYSTEM,
                flowBeeHasKey = false,
                flowBeeBalance = FlowBeeBalance.Unknown,
                flowBeeModelImport = FlowBeeModelImport.Idle,
                flowBeeModelCount = 0,
                showFlowBeeDefaultHint = false,
                flowBeeDefaultModelName = "GPT-4o",
                onFlowBeeInputKey = {},
                onFlowBeeRefreshBalance = {},
                onFlowBeeAdoptDefault = {},
                onFlowBeeDismissHint = {},
                onColorModeSelected = {},
                onNavigate = {},
                countChatFiles = { 128 to 512L * 1024 * 1024 },
            )
        }
    }
}
