package com.sypark.flightdeal.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthGridTest {

    @Test
    fun `1일이 무슨 요일이든 그 자리에서 시작한다`() {
        // 2026-10-01은 목요일이다. 월요일 시작이면 앞에 빈 칸 3개.
        val cells = MonthGrid.cellsOf(YearMonth.of(2026, 10))

        assertEquals(3, cells.take(3).count { it.date == null })
        assertEquals(LocalDate.of(2026, 10, 1), cells[3].date)
    }

    @Test
    fun `1일이 월요일이면 빈 칸이 없다`() {
        // 2026-06-01은 월요일이다.
        val cells = MonthGrid.cellsOf(YearMonth.of(2026, 6))

        assertEquals(LocalDate.of(2026, 6, 1), cells.first().date)
    }

    @Test
    fun `칸 수는 항상 7의 배수다`() {
        // 줄이 안 맞으면 마지막 줄이 깨진다. 2026년 2월(28일, 일요일 시작)과
        // 2024년 2월(윤년, 29일)을 함께 확인해 앞뒤 패딩이 둘 다 맞는지 본다.
        listOf(YearMonth.of(2026, 2), YearMonth.of(2026, 10), YearMonth.of(2024, 2))
            .forEach { assertEquals(0, MonthGrid.cellsOf(it).size % 7) }
    }

    @Test
    fun `그 달의 모든 날이 한 번씩 들어간다`() {
        val cells = MonthGrid.cellsOf(YearMonth.of(2026, 10))

        assertEquals(31, cells.count { it.date != null })
        assertEquals(31, cells.mapNotNull { it.date }.distinct().size)
    }
}
