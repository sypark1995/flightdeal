package com.sypark.flightdeal.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItineraryLabelTest {

    @Test
    fun `직항과 시간을 함께 적는다`() {
        assertEquals("직항 · 2시간 30분", itineraryLabel(transfers = 0, outboundMinutes = 150))
    }

    @Test
    fun `경유가 있으면 횟수를 적는다`() {
        assertEquals("경유 1회 · 8시간 10분", itineraryLabel(transfers = 1, outboundMinutes = 490))
    }

    @Test
    fun `둘 다 모르면 아무것도 적지 않는다`() {
        // 모르는 것을 "직항"이라고 말하면 안 된다.
        assertNull(itineraryLabel(transfers = null, outboundMinutes = null))
    }

    @Test
    fun `한 시간 미만은 분만 적는다`() {
        assertEquals("직항 · 50분", itineraryLabel(transfers = 0, outboundMinutes = 50))
    }

    @Test
    fun `정각이면 분을 적지 않는다`() {
        assertEquals("직항 · 3시간", itineraryLabel(transfers = 0, outboundMinutes = 180))
    }
}
