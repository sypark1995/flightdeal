package com.sypark.flightdeal.domain.model

data class Airport(
    val iata: String,
    val cityKo: String,
    val countryKo: String,
) {
    companion object {
        /** 기본 출발지. 설정 화면이 생기면 DataStore에서 읽어온다. */
        val INCHEON = Airport("ICN", "서울", "대한민국")
    }
}

data class Route(
    val origin: Airport,
    val destination: Airport,
)
