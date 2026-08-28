package com.sypark.flightdeal.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceStatsTest {

    @Test
    fun `홀수 개 가격의 중앙값을 구한다`() {
        val stats = PriceStats.from(listOf(Won(100), Won(300), Won(200)))!!
        assertEquals(Won(200), stats.median)
        assertEquals(Won(100), stats.min)
        assertEquals(Won(300), stats.max)
        assertEquals(3, stats.sampleCount)
    }

    @Test
    fun `짝수 개 가격은 가운데 두 값의 평균을 중앙값으로 삼는다`() {
        val stats = PriceStats.from(listOf(Won(100), Won(200), Won(300), Won(500)))!!
        assertEquals(Won(250), stats.median)
    }

    @Test
    fun `가격이 하나면 그 값이 중앙값이자 최소이자 최대다`() {
        val stats = PriceStats.from(listOf(Won(189_000)))!!
        assertEquals(Won(189_000), stats.median)
        assertEquals(Won(189_000), stats.min)
        assertEquals(Won(189_000), stats.max)
    }

    @Test
    fun `빈 목록이면 null이다`() {
        assertNull(PriceStats.from(emptyList()))
    }
}
