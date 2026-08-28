package com.sypark.flightdeal.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 앱 고유 팔레트. Material의 `colorScheme`으로 다 표현되지 않아 따로 둔다 —
 * `PriceDown`/`PriceUp`은 Material 색 역할에 자리가 없고, 이 앱에서는
 * "값이 내렸다/올랐다"라는 뜻을 나르는 핵심 색이다.
 */
@Immutable
data class FlightDealColors(
    val indigo: Color,
    val indigoSubtle: Color,
    val background: Color,
    val surface: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val priceDown: Color,
    val priceUp: Color,
)

val LightPalette = FlightDealColors(
    indigo = Color(0xFF4338E0),
    indigoSubtle = Color(0xFFEDEBFF),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF4F5F9),
    outline = Color(0xFFEAECF3),
    textPrimary = Color(0xFF0F1115),
    textSecondary = Color(0xFF8A8FA3),
    priceDown = Color(0xFF0E9E6E),
    priceUp = Color(0xFFD93A3A),
)

/**
 * 다크에서 라이트 색을 그대로 뒤집지 않는다.
 *
 * 인디고를 어두운 배경에 그대로 쓰면 대비가 모자라 글자가 뭉갠다. 밝게 올린다.
 * 초록·빨강도 마찬가지다 — 어두운 배경에서는 채도를 낮추고 명도를 올려야
 * 같은 세기로 읽힌다.
 */
val DarkPalette = FlightDealColors(
    indigo = Color(0xFF9A93FF),
    indigoSubtle = Color(0xFF262247),
    background = Color(0xFF101114),
    surface = Color(0xFF1A1C21),
    outline = Color(0xFF2A2D34),
    textPrimary = Color(0xFFF2F3F5),
    textSecondary = Color(0xFF9199AB),
    priceDown = Color(0xFF3DD9A0),
    priceUp = Color(0xFFFF7B7B),
)
