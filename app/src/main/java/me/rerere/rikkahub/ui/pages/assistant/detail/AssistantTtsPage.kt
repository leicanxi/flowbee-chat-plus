package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.VolumeHigh
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 助手级「语音朗读」页。
 *
 * 之所以从「本地工具」里把它单独提出来：语音朗读是给用户用的成品功能，不该藏在
 * 「本地工具」这种要用户先理解"工具调用"概念才敢点的地方。同时这里给出通往全局
 * 语音服务配置的入口，用户想换音色时不用自己去翻「设置 → 语音」。
 */
@Composable
fun AssistantTtsPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    // 朗读配置不全时（典型：内置音色还没填 FlowBee 密钥）提前告知，
    // 别让用户点了朗读按钮才发现不响。
    val setupIssue by LocalTTSState.current.setupIssue.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_tts))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            setupIssue?.let { issue ->
                SetupIssueCard(
                    message = issue,
                    onClick = { navController.navigate(Screen.SettingSpeech) },
                )
            }

            CardGroup {
                item(
                    headlineContent = {
                        Text(stringResource(R.string.assistant_page_local_tools_tts_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.assistant_page_local_tools_tts_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = assistant.localTools.contains(LocalToolOption.Tts),
                            onCheckedChange = { enabled ->
                                val newLocalTools = if (enabled) {
                                    assistant.localTools + LocalToolOption.Tts
                                } else {
                                    assistant.localTools - LocalToolOption.Tts
                                }
                                vm.update(assistant.copy(localTools = newLocalTools))
                            }
                        )
                    }
                )
            }

            CardGroup {
                item(
                    onClick = { navController.navigate(Screen.SettingSpeech) },
                    leadingContent = { Icon(HugeIcons.VolumeHigh, null) },
                    headlineContent = { Text(stringResource(R.string.speech_page_title)) },
                    supportingContent = {
                        Text(stringResource(R.string.assistant_tts_page_provider_desc))
                    },
                    trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                )
            }
        }
    }
}

/** 配置缺失提示卡，点一下直接去语音设置。 */
@Composable
private fun SetupIssueCard(
    message: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = HugeIcons.Alert01,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
