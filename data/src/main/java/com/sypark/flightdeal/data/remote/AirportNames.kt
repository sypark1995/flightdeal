package com.sypark.flightdeal.data.remote

/**
 * IATA 코드를 한국어 도시명으로. 화면에 "TYO" 대신 "도쿄"가 뜨게 한다.
 * 표에 없으면 코드를 그대로 돌려준다.
 */
object AirportNames {

    private val CITIES = mapOf(
        "ICN" to "서울", "SEL" to "서울", "GMP" to "서울", "PUS" to "부산", "CJU" to "제주",
        "TYO" to "도쿄", "NRT" to "도쿄", "HND" to "도쿄",
        "OSA" to "오사카", "KIX" to "오사카", "FUK" to "후쿠오카", "CTS" to "삿포로",
        "OKA" to "오키나와", "NGO" to "나고야",
        "BKK" to "방콕", "DMK" to "방콕", "HKT" to "푸껫", "CNX" to "치앙마이",
        "DAD" to "다낭", "SGN" to "호치민", "HAN" to "하노이", "PQC" to "푸꾸옥",
        "TPE" to "타이베이", "KHH" to "가오슝",
        "HKG" to "홍콩", "MFM" to "마카오",
        "SIN" to "싱가포르", "KUL" to "쿠알라룸푸르", "CEB" to "세부", "MNL" to "마닐라",
        "DPS" to "발리", "BKI" to "코타키나발루",
        "PEK" to "베이징", "PVG" to "상하이", "SHA" to "상하이", "CAN" to "광저우",
        "GUM" to "괌", "SPN" to "사이판",
    )

    fun cityOf(iata: String): String = CITIES[iata] ?: iata
}
