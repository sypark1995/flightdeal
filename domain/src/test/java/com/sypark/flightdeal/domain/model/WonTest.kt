package com.sypark.flightdeal.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WonTest {

    @Test
    fun `금액을 비교할 수 있다`() {
        assertTrue(Won(189_000) < Won(305_000))
        assertTrue(Won(305_000) > Won(189_000))
        assertEquals(0, Won(189_000).compareTo(Won(189_000)))
    }

    @Test
    fun `기준가 대비 퍼센트를 계산한다`() {
        assertEquals(62, Won(189_000).percentOf(Won(305_000)))
    }

    @Test
    fun `기준가와 같으면 100퍼센트다`() {
        assertEquals(100, Won(189_000).percentOf(Won(189_000)))
    }

    @Test
    fun `기준가가 0이면 100퍼센트로 처리한다`() {
        assertEquals(100, Won(189_000).percentOf(Won(0)))
    }
}
