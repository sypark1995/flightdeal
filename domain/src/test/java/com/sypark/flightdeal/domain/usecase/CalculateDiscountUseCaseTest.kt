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
}
