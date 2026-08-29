package com.sypark.flightdeal.alerts

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class RelativeTimeTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val now = Instant.parse("2026-08-28T12:00:00Z") // 21:00 KST

    @Test
    fun `1분 미만은 방금이다`() {
        val then = now.minusSeconds(30)

        assertEquals("방금", relativeTimeKo(then, now, zone))
    }

    @Test
    fun `1시간 미만은 분으로 적는다`() {
        val then = now.minusSeconds(60 * 5)

        assertEquals("5분 전", relativeTimeKo(then, now, zone))
    }

    @Test
    fun `같은 날 23시간 전은 시간으로 적는다`() {
        // now: 2026-08-28 21:00 KST. 23시간 전은 2026-08-27 22:00 KST — 어라, 다른 날이다.
        // 같은 날짜 안에서 23시간 전이 나오려면 now가 자정에 가까워야 한다.
        val lateNow = Instant.parse("2026-08-28T14:59:00Z") // 2026-08-28 23:59 KST
        val then = lateNow.minusSeconds(3600 * 23) // 2026-08-28 00:59 KST, 같은 날

        assertEquals("23시간 전", relativeTimeKo(then, lateNow, zone))
    }

    @Test
    fun `23시간 전이어도 날짜가 다르면 어제다`() {
        // now: 2026-08-28 21:00 KST. 23시간 전은 2026-08-27 22:00 KST — 어제다.
        // 경과 시간이 24시간 미만이어도 날짜가 바뀌었으면 "어제"여야 한다.
        val then = now.minusSeconds(3600 * 23)

        assertEquals("어제", relativeTimeKo(then, now, zone))
    }

    @Test
    fun `달력상 어제면 어제다`() {
        // now: 2026-08-28 00:30 KST 직후. 1시간 전이 이미 어제 날짜다.
        val earlyNow = Instant.parse("2026-08-27T15:30:00Z") // 2026-08-28 00:30 KST
        val then = earlyNow.minusSeconds(3600) // 2026-08-27 23:30 KST, 어제

        assertEquals("어제", relativeTimeKo(then, earlyNow, zone))
    }

    @Test
    fun `이틀 넘으면 날짜를 적는다`() {
        val then = Instant.parse("2026-08-20T12:00:00Z")

        assertEquals("8월 20일", relativeTimeKo(then, now, zone))
    }
}
