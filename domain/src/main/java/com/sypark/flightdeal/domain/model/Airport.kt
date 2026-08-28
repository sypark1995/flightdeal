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
        /** 기본 출발지. 설정 화면이 생기면 DataStore에서 읽어온다. */
        val INCHEON = Airport("ICN", "서울", "대한민국")
    }
}

data class Route(
    val origin: Airport,
    val destination: Airport,
)
