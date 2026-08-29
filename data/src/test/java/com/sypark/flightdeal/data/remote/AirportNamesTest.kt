package com.sypark.flightdeal.data.remote

import com.sypark.flightdeal.domain.model.Airport
import org.junit.Assert.assertEquals
import org.junit.Test

class AirportNamesTest {

    @Test
    fun `출발 공항 이름이 Airport와 어긋나지 않는다`() {
        Airport.ORIGINS.forEach { assertEquals(it.cityKo, AirportNames.cityOf(it.iata)) }
    }

    @Test
    fun `도시 코드 SEL은 폴백으로 남아있다`() {
        assertEquals("서울", AirportNames.cityOf("SEL"))
    }

    @Test
    fun `표에 없는 코드는 코드를 그대로 돌려준다`() {
        assertEquals("ZZZ", AirportNames.cityOf("ZZZ"))
    }
}
