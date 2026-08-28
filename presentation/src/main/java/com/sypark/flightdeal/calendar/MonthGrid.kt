package com.sypark.flightdeal.calendar

import java.time.LocalDate
import java.time.YearMonth

/** 달력 한 칸. [date]가 null이면 1일 앞 또는 말일 뒤의 빈 칸이다. */
data class GridCell(val date: LocalDate?)

object MonthGrid {

    private const val DAYS_PER_WEEK = 7

    /** 월요일 시작. 한국 달력은 일요일 시작도 쓰지만 이 앱은 월요일로 고정한다. */
    fun cellsOf(month: YearMonth): List<GridCell> {
        // DayOfWeek.value는 월요일=1 ~ 일요일=7이라 그대로 빼면 월요일 시작 기준
        // 앞칸 개수가 된다.
        val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
        val dateCells = (1..month.lengthOfMonth()).map { GridCell(month.atDay(it)) }

        // 앞칸만 채우면 마지막 줄이 7칸을 못 채울 수 있다. 뒷칸도 7의 배수가 되도록
        // 채워야 격자를 7칸씩 끊어 그릴 때 줄이 깨지지 않는다.
        val used = leadingBlanks + dateCells.size
        val trailingBlanks = (DAYS_PER_WEEK - used % DAYS_PER_WEEK) % DAYS_PER_WEEK

        return List(leadingBlanks) { GridCell(null) } + dateCells + List(trailingBlanks) { GridCell(null) }
    }
}
