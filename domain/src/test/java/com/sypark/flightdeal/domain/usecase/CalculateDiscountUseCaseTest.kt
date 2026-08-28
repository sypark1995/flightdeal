package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.PriceStats
import com.sypark.flightdeal.domain.model.Won
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculateDiscountUseCaseTest {

    private val useCase = CalculateDiscountUseCase()

    private fun stats(median: Int) = PriceStats(
        median = Won(median), min = Won(median), max = Won(median), sampleCount = 10,
    )

    @Test
    fun `중앙값보다 싸면 할인율을 돌려준다`() {
        assertEquals(38, useCase(Won(189_000), stats(305_000)))
    }

    @Test
    fun `중앙값과 같으면 null이다`() {
        assertNull(useCase(Won(305_000), stats(305_000)))
    }

    @Test
    fun `중앙값보다 비싸면 null이다`() {
        assertNull(useCase(Won(400_000), stats(305_000)))
    }

    @Test
    fun `표본이 3개 미만이면 신뢰할 수 없으므로 null이다`() {
        val thinStats = PriceStats(Won(305_000), Won(305_000), Won(305_000), sampleCount = 2)
        assertNull(useCase(Won(189_000), thinStats))
    }

    @Test
    fun `할인율이 5퍼센트 미만이면 배지를 달지 않는다`() {
        assertEquals(null, useCase(Won(295_000), stats(305_000)))
    }

    @Test
    fun `표본이 정확히 3개면 경계값이므로 신뢰해 할인율을 돌려준다`() {
        // MIN_SAMPLE_COUNT(3) 경계값. 189,000 / 305,000 = 61.97%를 반올림하면 62%이므로
        // 할인율은 100 - 62 = 38.
        val boundaryStats = PriceStats(Won(305_000), Won(189_000), Won(305_000), sampleCount = 3)
        assertEquals(38, useCase(Won(189_000), boundaryStats))
    }

    @Test
    fun `할인율이 정확히 5퍼센트면 경계값이므로 배지를 단다`() {
        // MIN_DISCOUNT_PERCENT(5) 경계값.
        // percentOf: (190,000 * 100 + 200,000 / 2) / 200,000 = 19,100,000 / 200,000 = 95(버림).
        // 할인율은 100 - 95 = 5로 MIN_DISCOUNT_PERCENT(5)와 같아 배지를 단다.
        assertEquals(5, useCase(Won(190_000), stats(200_000)))
    }
}
