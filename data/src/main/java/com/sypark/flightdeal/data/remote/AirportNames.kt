package com.sypark.flightdeal.data.remote

import com.sypark.flightdeal.domain.model.Airport

/**
 * IATA 코드를 한국어 도시명으로. 화면에 "TYO" 대신 "도쿄"가 뜨게 한다.
 * 표에 없으면 코드를 그대로 돌려준다.
 */
object AirportNames {

    /**
     * 이 앱이 실제로 다루는 목적지(와 인천)의 도시명은 여기서 다시 적지 않는다.
     * `Airport.DESTINATIONS`/`Airport.INCHEON`이 이미 그 값을 들고 있다 — 여기에도
     * "TYO" to "도쿄"를 따로 적으면 두 표가 같은 사실을 각자 말하게 되고, `Airport.kt`가
     * 나중에 바뀌어도 이 표는 조용히 그대로 남아 둘이 어긋난다. 그래서 이 표에는
     * `Airport`가 모르는 보조 코드(같은 도시의 다른 공항, 국내선, 이 앱이 목적지로
     * 다루지 않는 도시)만 남긴다.
     */
    private val ADDITIONAL_CITIES = mapOf(
        "SEL" to "서울", "GMP" to "서울", "PUS" to "부산", "CJU" to "제주",
        "NRT" to "도쿄", "HND" to "도쿄",
        "OSA" to "오사카", "KIX" to "오사카", "FUK" to "후쿠오카", "CTS" to "삿포로",
        "OKA" to "오키나와", "NGO" to "나고야",
        "DMK" to "방콕", "HKT" to "푸껫", "CNX" to "치앙마이",
        "SGN" to "호치민", "HAN" to "하노이", "PQC" to "푸꾸옥",
        "KHH" to "가오슝",
        "MFM" to "마카오",
        "KUL" to "쿠알라룸푸르", "CEB" to "세부", "MNL" to "마닐라",
        "DPS" to "발리", "BKI" to "코타키나발루",
        "PEK" to "베이징", "PVG" to "상하이", "SHA" to "상하이", "CAN" to "광저우",
        "GUM" to "괌", "SPN" to "사이판",
    )

    private val CITIES: Map<String, String> =
        (Airport.DESTINATIONS + Airport.INCHEON).associate { it.iata to it.cityKo } + ADDITIONAL_CITIES

    fun cityOf(iata: String): String = CITIES[iata] ?: iata
}
