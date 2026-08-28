package com.sypark.flightdeal.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AirportTest {

    @Test
    fun `IATA가 같으면 표시 이름이 달라도 같은 공항이다`() {
        // 매퍼는 국가명을 채우지 않고, 상수는 채운다. 그래도 같은 인천이다.
        assertEquals(Airport("ICN", "서울", ""), Airport("ICN", "서울", "대한민국"))
    }

    @Test
    fun `IATA가 같으면 해시도 같다`() {
        assertEquals(
            Airport("ICN", "서울", "").hashCode(),
            Airport("ICN", "인천", "대한민국").hashCode(),
        )
    }

    @Test
    fun `IATA가 다르면 다른 공항이다`() {
        assertNotEquals(Airport("ICN", "서울", ""), Airport("GMP", "서울", ""))
    }

    @Test
    fun `Map 키로 쓸 수 있다`() {
        val map = mapOf(Airport("ICN", "서울", "") to 1)

        // 워커가 조회한 공항으로 저장된 추적 노선을 찾을 수 있어야 한다.
        assertEquals(1, map[Airport.INCHEON])
    }

    @Test
    fun `노선도 IATA로만 비교된다`() {
        val a = Route(Airport("ICN", "서울", ""), Airport("TYO", "도쿄", ""))
        val b = Route(Airport.INCHEON, Airport("TYO", "동경", "일본"))

        assertEquals(a, b)
    }
}
