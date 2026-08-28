package com.sypark.flightdeal.feed

import com.sypark.flightdeal.domain.model.Won
import org.junit.Assert.assertEquals
import org.junit.Test

class PriceFormatTest {

    @Test
    fun `천 단위로 끊고 원을 붙인다`() {
        assertEquals("189,000원", formatWon(Won(189_000)))
    }

    @Test
    fun `천 원 미만도 원을 붙인다`() {
        assertEquals("900원", formatWon(Won(900)))
    }

    @Test
    fun `백만 단위도 끊는다`() {
        assertEquals("1,250,000원", formatWon(Won(1_250_000)))
    }
}
