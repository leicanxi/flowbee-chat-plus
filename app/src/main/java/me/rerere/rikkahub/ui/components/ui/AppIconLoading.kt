package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import me.rerere.rikkahub.ui.context.LocalSettings
import kotlin.math.cos
import kotlin.math.sin

/**
 * 「应用图标样式」的加载指示器 —— FlowBee 三颗暖金星。
 *
 * 上游这里是一段兔子奔跑的 [android.graphics.drawable.AnimatedVectorDrawable]，是最后一块
 * **默认就在用**的上游品牌资产（`useAppIconStyleLoadingIndicator` 默认 true）。fork 里换成
 * 自家动画：三颗四角星（内凹边），中间那颗原地脉冲，外侧两颗绕着中心走一整圈椭圆再回到原位，
 * 同时整体明暗呼吸 —— 参数是从用户给的 Lottie 逐帧实测出来再重写的。
 *
 * 手写而不是塞个 Lottie 运行时，是因为真实使用处只有 28dp（聊天列表）和 32dp（压缩对话框）：
 * 那颗星在这里只有 6dp 左右，Lottie 才有的细节根本显示不出来，白背 1MB 装包。
 *
 * 想调节奏改 [LOADER_PERIOD_MILLIS]，想整体放大缩小改 [DESIGN_SPAN]（越小越大）。
 */
private const val LOADER_PERIOD_MILLIS = 2200

/** 设计坐标系边长 600，内容实际只占 530 → 这里让 560 铺满方框，视觉上更满一点。 */
private const val DESIGN_SPAN = 560f

/** 设计坐标系里的中心点。 */
private const val DESIGN_CENTER = 300f

private val STAR_DARK = Color(0xFFB58B43)
private val STAR_PALE = Color(0xFFF2DEA7)

/** 四角星顶点 + 两侧控制柄（Lottie 的 v / i / o，单位是设计坐标，原点在星心）。 */
private val STAR_V = listOf(
    Offset(0f, -65.167f), Offset(65.167f, 0f), Offset(0f, 65.167f), Offset(-65.167f, 0f),
)
private val STAR_O = listOf(
    Offset(12.343f, 29.363f), Offset(-29.363f, 12.343f),
    Offset(-12.343f, -29.363f), Offset(29.363f, -12.343f),
)
private val STAR_I = listOf(
    Offset(-12.343f, 29.44f), Offset(-29.44f, -12.343f),
    Offset(12.343f, -29.44f), Offset(29.44f, 12.343f),
)

/** 中心星的比例曲线：静止 → 收 → 顶到最大 → 收 → 静止。 */
private val CENTER_SCALE = listOf(0f to 1.00f, 0.30f to 0.80f, 0.52f to 1.30f, 0.74f to 0.80f, 1f to 1.00f)

/** 外侧两颗转圈时收下去（把视觉让给中心星）。 */
private val OUTER_SCALE = listOf(0f to 1.00f, 0.30f to 0.72f, 0.52f to 0.62f, 0.78f to 0.88f, 1f to 1.00f)

/** 明暗呼吸：转到一半时最亮。 */
private val COLOR_KEY = listOf(0f to 0f, 0.52f to 1f, 0.92f to 0f, 1f to 0f)

/** 外侧星绕中心走的椭圆轨道半径（实测原版是 200 → 127，这里取规整椭圆）。 */
private const val ORBIT_RX = 200f
private const val ORBIT_RY = 140f

/** 头尾各留 14% 的「定格」，中间一口气转完 —— 原版就是这个停顿感。 */
private const val ORBIT_DWELL = 0.14f

@Composable
fun AppIconLoadingIndicator(modifier: Modifier = Modifier) {
    val useAppIconStyleLoadingIndicator =
        LocalSettings.current.displaySetting.useAppIconStyleLoadingIndicator

    if (!useAppIconStyleLoadingIndicator) {
        ContainedLoadingIndicator(modifier = modifier)
        return
    }

    val transition = rememberInfiniteTransition(label = "flowBeeLoading")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        // 缓动全部在下面的曲线函数里做，这里必须线性，否则会叠一层。
        animationSpec = infiniteRepeatable(
            animation = tween(LOADER_PERIOD_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "flowBeeLoadingPhase",
    )
    val star = remember { starPath() }

    Canvas(modifier) {
        val unit = size.minDimension / DESIGN_SPAN
        val tint = lerp(STAR_DARK, STAR_PALE, keyed(phase, COLOR_KEY))
        val spin = 180f * phase
        val orbit = easeOrbit(phase)

        // 外侧两颗：绕中心走一整圈椭圆，头尾正好停在左右两个原位（天然可循环）
        for (startDeg in floatArrayOf(180f, 0f)) {
            val rad = Math.toRadians((startDeg + 360f * orbit).toDouble())
            drawStar(
                path = star,
                cx = DESIGN_CENTER + ORBIT_RX * cos(rad).toFloat(),
                cy = DESIGN_CENTER + ORBIT_RY * sin(rad).toFloat(),
                scale = keyed(phase, OUTER_SCALE),
                rotation = spin,
                color = lerp(tint, STAR_DARK, 0.15f),
                unit = unit,
            )
        }
        // 中心星最后画，压在两个外侧星上面
        drawStar(
            path = star,
            cx = DESIGN_CENTER,
            cy = DESIGN_CENTER,
            scale = keyed(phase, CENTER_SCALE),
            rotation = spin + 180f,
            color = tint,
            unit = unit,
        )
    }
}

private fun DrawScope.drawStar(
    path: Path,
    cx: Float,
    cy: Float,
    scale: Float,
    rotation: Float,
    color: Color,
    unit: Float,
) {
    withTransform({
        translate(
            (cx - DESIGN_CENTER) * unit + size.width / 2f,
            (cy - DESIGN_CENTER) * unit + size.height / 2f,
        )
        rotate(rotation, pivot = Offset.Zero)
        scale(scale * unit, scale * unit, pivot = Offset.Zero)
    }) {
        drawPath(path, color)
    }
}

private fun starPath(): Path = Path().apply {
    moveTo(STAR_V[0].x, STAR_V[0].y)
    for (k in STAR_V.indices) {
        val end = STAR_V[(k + 1) % STAR_V.size]
        val c1 = STAR_V[k] + STAR_O[k]
        val c2 = end + STAR_I[(k + 1) % STAR_V.size]
        cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
    }
    close()
}

private fun easeOrbit(u: Float): Float =
    smoothStep(((u - ORBIT_DWELL) / (1f - 2f * ORBIT_DWELL)).coerceIn(0f, 1f))

private fun smoothStep(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

/** 在 [(相位, 值)] 关键帧之间平滑插值，相位环绕到 0..1。 */
private fun keyed(u: Float, keys: List<Pair<Float, Float>>): Float {
    val p = ((u % 1f) + 1f) % 1f
    for (i in 0 until keys.size - 1) {
        val (u0, v0) = keys[i]
        val (u1, v1) = keys[i + 1]
        if (p in u0..u1) {
            val t = if (u1 > u0) smoothStep((p - u0) / (u1 - u0)) else 0f
            return v0 + (v1 - v0) * t
        }
    }
    return keys.last().second
}
