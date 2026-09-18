package me.rerere.rikkahub.ui.theme.presets

import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.theme.PresetTheme

/**
 * Claude 风格：象牙白/米色的暖中性底色，Slate 深色（浅色）/ 米白（深色）作为前景强调色，
 * 米黄（#F2EBDE）作为强调块底色，牛皮棕与马尼拉黄作为点缀色。
 *
 * 底色分工（浅色）：页面用 [surfaceContainer]/[background] 的象牙白，卡片与输入框用纯白；
 * 深色则反过来：页面用最暗的 #141413，卡片/面板抬到 #262624，输入框再压回 #0A0A09。
 *
 * 前景强调色刻意保持无彩色：Anthropic 色板里 Kraft/Manilla/Cloud 系列对米色底的对比度都在
 * 3:1 以下，只能当填充色，当链接与图标色读不清；他们自己的界面对文字色也是用 Slate。
 */
val ClaudeThemePreset by lazy {
    PresetTheme(
        id = "claude",
        name = {
            Text(stringResource(id = R.string.theme_name_claude))
        },
        standardLight = lightScheme,
        standardDark = darkScheme,
    )
}

//region 浅色：底色 #FAF9F5，卡片/输入框 #FFFFFF，强调块 #F2EBDE
private val primaryLight = Color(0xFF191919)
private val onPrimaryLight = Color(0xFFFFFFFF)
private val primaryContainerLight = Color(0xFFF2EBDE)
private val onPrimaryContainerLight = Color(0xFF3D3929)
private val secondaryLight = Color(0xFF6F675C)
private val onSecondaryLight = Color(0xFFFFFFFF)
private val secondaryContainerLight = Color(0xFFE9E6DC)
private val onSecondaryContainerLight = Color(0xFF3D3929)
private val tertiaryLight = Color(0xFF9A6C3E)
private val onTertiaryLight = Color(0xFFFFFFFF)
private val tertiaryContainerLight = Color(0xFFEBDBBC)
private val onTertiaryContainerLight = Color(0xFF5A4526)
private val errorLight = Color(0xFFB03D2E)
private val onErrorLight = Color(0xFFFFFFFF)
private val errorContainerLight = Color(0xFFF8E1DC)
private val onErrorContainerLight = Color(0xFF7E2418)
private val backgroundLight = Color(0xFFFAF9F5)
private val onBackgroundLight = Color(0xFF262624)
private val surfaceLight = Color(0xFFFAF9F5)
private val onSurfaceLight = Color(0xFF262624)
private val surfaceVariantLight = Color(0xFFEDEAE0)
private val onSurfaceVariantLight = Color(0xFF6F675C)
private val outlineLight = Color(0xFFBDB7A9)
private val outlineVariantLight = Color(0xFFC7C1B3)
private val scrimLight = Color(0xFF000000)
private val inverseSurfaceLight = Color(0xFF33312C)
private val inverseOnSurfaceLight = Color(0xFFF7F5EF)
private val inversePrimaryLight = Color(0xFFF2EBDE)
private val surfaceDimLight = Color(0xFFF0EEE6)
private val surfaceBrightLight = Color(0xFFFFFFFF)
private val surfaceContainerLowestLight = Color(0xFFFFFFFF)
private val surfaceContainerLowLight = Color(0xFFFFFFFF)
private val surfaceContainerLight = Color(0xFFFAF9F5)
private val surfaceContainerHighLight = Color(0xFFFFFFFF)
private val surfaceContainerHighestLight = Color(0xFFF0EEE6)
//endregion

//region 深色：底色 #141413，卡片/面板 #262624，输入框 #0A0A09，强调色 #F2EBDE
private val primaryDark = Color(0xFFF2EBDE)
private val onPrimaryDark = Color(0xFF262624)
private val primaryContainerDark = Color(0xFF423E35)
private val onPrimaryContainerDark = Color(0xFFF2EBDE)
private val secondaryDark = Color(0xFFD3CCBF)
private val onSecondaryDark = Color(0xFF383429)
private val secondaryContainerDark = Color(0xFF4A453A)
private val onSecondaryContainerDark = Color(0xFFEDE7DA)
private val tertiaryDark = Color(0xFFE0BE8C)
private val onTertiaryDark = Color(0xFF422C0C)
private val tertiaryContainerDark = Color(0xFF5C4525)
private val onTertiaryContainerDark = Color(0xFFFFDDB0)
private val errorDark = Color(0xFFFFB4A6)
private val onErrorDark = Color(0xFF5F1508)
private val errorContainerDark = Color(0xFF82271A)
private val onErrorContainerDark = Color(0xFFFFDAD3)
private val backgroundDark = Color(0xFF141413)
private val onBackgroundDark = Color(0xFFEDEAE3)
private val surfaceDark = Color(0xFF141413)
private val onSurfaceDark = Color(0xFFEDEAE3)
private val surfaceVariantDark = Color(0xFF3A372F)
private val onSurfaceVariantDark = Color(0xFFCBC5B8)
private val outlineDark = Color(0xFF5A564C)
private val outlineVariantDark = Color(0xFF48443A)
private val scrimDark = Color(0xFF000000)
private val inverseSurfaceDark = Color(0xFFEDEAE3)
private val inverseOnSurfaceDark = Color(0xFF33312C)
private val inversePrimaryDark = Color(0xFF191919)
private val surfaceDimDark = Color(0xFF141413)
private val surfaceBrightDark = Color(0xFF262624)
private val surfaceContainerLowestDark = Color(0xFF0A0A09)
private val surfaceContainerLowDark = Color(0xFF0A0A09)
private val surfaceContainerDark = Color(0xFF262624)
private val surfaceContainerHighDark = Color(0xFF262624)
private val surfaceContainerHighestDark = Color(0xFF2E2E2C)
//endregion

private val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

private val darkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)
