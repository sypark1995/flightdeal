package com.sypark.flightdeal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 기본값을 라이트로 둔다. 프리뷰나 테스트가 테마 밖에서 Composable을 그려도
 * 색이 없어 죽지 않게 한다.
 */
val LocalFlightDealColors = staticCompositionLocalOf { LightPalette }

/** `FlightDealTheme.colors.background` 꼴로 쓰기 위한 접근자. */
object FlightDealTheme {
    val colors: FlightDealColors
        @Composable get() = LocalFlightDealColors.current
}

/**
 * Material 컴포넌트(`AlertDialog`, `Snackbar`, `NavigationBar`, `TextButton`)는
 * 앱 고유 팔레트가 아니라 `MaterialTheme.colorScheme`을 읽는다. 여기서 팔레트 값을
 * `darkColorScheme`/`lightColorScheme`에 채워 넣지 않으면 다이얼로그와 스낵바만
 * 다크에서도 밝은 채로 남는다.
 */
private fun FlightDealColors.toColorScheme(darkTheme: Boolean): ColorScheme {
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = indigo,
        onPrimary = if (darkTheme) Color.Black else Color.White,
        primaryContainer = indigoSubtle,
        onPrimaryContainer = indigo,
        background = background,
        onBackground = textPrimary,
        surface = background,
        onSurface = textPrimary,
        surfaceVariant = surface,
        onSurfaceVariant = textSecondary,
        outline = outline,
        outlineVariant = outline,
    )
}

@Composable
fun FlightDealTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(LocalFlightDealColors provides palette) {
        MaterialTheme(colorScheme = palette.toColorScheme(darkTheme), content = content)
    }
}
