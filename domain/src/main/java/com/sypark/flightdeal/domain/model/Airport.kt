package com.sypark.flightdeal.domain.model

/**
 * 공항의 정체성은 IATA 코드다. [cityKo]와 [countryKo]는 화면에 쓰는 표시용이며
 * 출처에 따라 다르게 채워진다 — 매퍼는 국가명을 비우고, 상수는 채운다.
 * 그 차이로 같은 공항이 서로 다른 것이 되면 저장된 추적 노선을 영영 찾지 못한다.
 */
class Airport(
    val iata: String,
    val cityKo: String,
    val countryKo: String,
) {
    override fun equals(other: Any?): Boolean = other is Airport && other.iata == iata

    override fun hashCode(): Int = iata.hashCode()

    override fun toString(): String = "Airport($iata, $cityKo)"

    companion object {
        /** 기본 출발지. */
        val INCHEON = Airport("ICN", "인천", "대한민국")

        /**
         * 고를 수 있는 출발 공항.
         *
         * **도시 코드(`SEL`)를 넣지 마라.** 그건 인천과 김포를 함께 가리켜서,
         * 조회는 `SEL`로 하고 저장은 행마다 `ICN`/`GMP`로 갈린다. 같은 노선이
         * 두 줄로 나뉘고 할인 기준선도 카드마다 다른 분포에서 계산된다.
         */
        val ORIGINS = listOf(
            INCHEON,
            Airport("GMP", "김포", "대한민국"),
            Airport("PUS", "부산", "대한민국"),
            Airport("CJU", "제주", "대한민국"),
        )

        /**
         * 이 앱이 다루는 인천 출발 목적지.
         *
         * 데이터 소스가 아니라 제품이 정하는 목록이라 도메인에 둔다.
         * `:data`의 조회도, 캘린더 화면의 선택지도 여기 하나를 본다 —
         * 두 군데에 두면 화면에는 있는데 조회는 안 되는 목적지가 생긴다.
         */
        val DESTINATIONS = listOf(
            Airport("TYO", "도쿄", "일본"),
            Airport("BKK", "방콕", "태국"),
            Airport("DAD", "다낭", "베트남"),
            Airport("TPE", "타이베이", "대만"),
            Airport("HKG", "홍콩", "홍콩"),
            Airport("SIN", "싱가포르", "싱가포르"),
        )
    }
}

data class Route(
    val origin: Airport,
    val destination: Airport,
)

/**
 * 지금으로부터 몇 달 뒤를 기본으로 보여줄지.
 *
 * 데이터 소스가 실사용자 검색 기록 캐시라 가까운 날짜는 듬성듬성하고 두 달쯤 뒤가
 * 가장 촘촘하다. 딜 피드의 조회와 캘린더의 첫 화면이 **같은 값을 봐야 한다** —
 * 다르면 같은 노선인데 두 화면의 가격이 안 맞고, 사용자는 어느 쪽이 맞는지 알 수 없다.
 */
const val DEFAULT_LEAD_MONTHS = 2L
