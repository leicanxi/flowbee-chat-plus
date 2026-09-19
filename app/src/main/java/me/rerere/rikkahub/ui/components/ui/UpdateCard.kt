package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.dokar.sonner.ToastType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Download01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.useThrottle
import me.rerere.rikkahub.ui.pages.chat.ChatVM
import me.rerere.rikkahub.utils.UpdateDownload
import me.rerere.rikkahub.utils.UpdateInfo
import me.rerere.rikkahub.utils.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * 「有更新」badge：替换掉原来占据侧边栏一大块的更新卡片。
 *
 * 设计意图：用户不打开侧边栏、或不在意更新时，不该被整段 changelog 打扰；
 * 真正想看更新内容的人，点这个 badge 会拿到和原来一模一样的详情弹窗。
 */
@Composable
fun UpdateBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = HugeIcons.Download01,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(R.string.update_card_badge),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

/**
 * 更新详情弹窗。内容与改造前的 UpdateCard 点击后弹出的那个完全一致：
 * 版本号 / 发布时间 / changelog / 各架构安装包下载入口。
 *
 * 调用方负责保证 [info] 是"确实有新版本"的那份数据。
 */
@OptIn(ExperimentalTime::class)
@Composable
fun UpdateDetailSheet(
    vm: ChatVM,
    info: UpdateInfo,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val downloadHandler = useThrottle<UpdateDownload>(500) { item ->
        vm.updateChecker.downloadUpdate(context, item)
        onDismissRequest()
        toaster.show(context.getString(R.string.update_card_downloading), type = ToastType.Info)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(horizontal = 16.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = info.version,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = Instant.parse(info.publishedAt).toJavaInstant().toLocalDateTime(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            MarkdownBlock(
                content = info.changelog,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyMedium
            )
            info.downloads.fastForEach { downloadItem ->
                OutlinedCard(
                    onClick = {
                        downloadHandler(downloadItem)
                    },
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = downloadItem.name,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = downloadItem.size
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = HugeIcons.Download01,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }
    }
}
