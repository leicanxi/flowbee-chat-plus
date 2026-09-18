package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.context.LocalSettings

/**
 * 「应用图标样式」的加载指示器。
 *
 * 上游这里放的是一段兔子奔跑的 [android.graphics.drawable.AnimatedVectorDrawable]，
 * 是最后一块**默认就在用**的上游品牌资产（`useAppIconStyleLoadingIndicator` 默认 true）。
 * fork 里换成自家图标的呼吸动画：直接复用关于页那张方图，
 * 省掉一个需要单独维护的矢量动画资源。
 *
 * 想调节奏改 [PULSE_MILLIS]，想换图改 [LOGO_RES]。
 */
private const val PULSE_MILLIS = 720
private val LOGO_RES = R.drawable.ic_about_logo

@Composable
fun AppIconLoadingIndicator(modifier: Modifier = Modifier) {
    val useAppIconStyleLoadingIndicator =
        LocalSettings.current.displaySetting.useAppIconStyleLoadingIndicator

    if (useAppIconStyleLoadingIndicator) {
        val transition = rememberInfiniteTransition(label = "appIconLoading")
        val scale by transition.animateFloat(
            initialValue = 0.80f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(PULSE_MILLIS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "appIconLoadingScale",
        )
        Box(modifier, contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(LOGO_RES),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize(0.72f)
                    .scale(scale),
            )
        }
    } else {
        ContainedLoadingIndicator(modifier = modifier)
    }
}
