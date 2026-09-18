package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.HeartHandshake
import me.rerere.hugeicons.stroke.UserCircle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.api.SponsorAPI
import me.rerere.rikkahub.data.flowbee.FlowBeeDefaults
import me.rerere.rikkahub.data.model.Sponsor
import me.rerere.rikkahub.data.model.SponsorBadge
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.onError
import me.rerere.rikkahub.utils.onLoading
import me.rerere.rikkahub.utils.onSuccess
import org.koin.compose.koinInject

/**
 * 赞助页。
 *
 * 支付能力只在站点网页上，所以这里不做任何支付 UI：卡片跳转到 FlowBee 的赞助页，
 * 名单单独从 `GET /api/sponsors` 拉。
 */
@Composable
fun SettingDonatePage() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(R.string.donate_page_title))
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
    ) { paddings ->
        Column(
            modifier = Modifier
                .padding(paddings)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CardGroup(
                modifier = Modifier.fillMaxWidth(),
                title = { Text(stringResource(R.string.donate_page_donation_methods)) },
            ) {
                item(
                    onClick = {
                        navController.navigate(Screen.WebView(url = FlowBeeDefaults.SPONSORS_URL))
                    },
                    leadingContent = {
                        Icon(
                            imageVector = HugeIcons.HeartHandshake,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    supportingContent = { Text(stringResource(R.string.donate_page_support_desc)) },
                    headlineContent = { Text(stringResource(R.string.donate_page_support_flowbee)) },
                )
            }

            Text(
                text = stringResource(R.string.donate_page_sponsor_list),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Sponsors(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun Sponsors(modifier: Modifier = Modifier) {
    val sponsorAPI = koinInject<SponsorAPI>()
    val failedText = stringResource(R.string.donate_page_sponsor_failed)
    val emptyText = stringResource(R.string.donate_page_sponsor_empty)

    val sponsors by produceState<UiState<List<Sponsor>>>(UiState.Idle) {
        value = UiState.Loading
        value = runCatching {
            val response = sponsorAPI.getSponsors()
            val data = response.data
            // 与 `/api/usage/token/` 同一个坑：失败时站点也返回 HTTP 200，必须读 success。
            if (!response.success || data == null) error(failedText)
            // 服务端已按最近支持时间倒序，这里再兜一次，免得将来接口调整顺序。
            data.sponsors.sortedByDescending { it.lastTime }
        }.fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it) },
        )
    }

    Box(modifier = modifier) {
        sponsors.onSuccess { list ->
            if (list.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(list) { sponsor ->
                        SponsorRow(sponsor)
                    }
                }
            }
        }.onLoading {
            CircularWavyProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }.onError {
            Text(
                text = failedText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun SponsorRow(sponsor: Sponsor) {
    // 匿名时 name 是空字符串（不是缺字段），统一看这个标志，别只看名字空不空。
    val isAnonymous = sponsor.anonymous || sponsor.name.isBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SponsorAvatar(
            text = if (isAnonymous) "" else sponsor.name,
            anonymous = isAnonymous,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (isAnonymous) {
                        stringResource(R.string.donate_page_sponsor_anonymous)
                    } else {
                        sponsor.name
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // badges 是 id，不是中文；未知 id 直接不显示，免得露出裸 id。
                sponsor.badges.forEach { id ->
                    val badge = SponsorBadge.fromId(id) ?: return@forEach
                    SponsorBadgeChip(badge)
                }
            }

            if (sponsor.message.isNotBlank()) {
                // 纯文本，不要当 HTML 渲染。
                Text(
                    text = sponsor.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SponsorAvatar(text: String, anonymous: Boolean) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (anonymous) {
            Icon(
                imageVector = HugeIcons.UserCircle,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        } else {
            Text(
                text = text.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SponsorBadgeChip(badge: SponsorBadge) {
    val label = when (badge) {
        SponsorBadge.EARLY_SUPPORTER -> stringResource(R.string.donate_page_sponsor_badge_early)
        SponsorBadge.CONTINUOUS_SUPPORTER -> stringResource(R.string.donate_page_sponsor_badge_continuous)
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
