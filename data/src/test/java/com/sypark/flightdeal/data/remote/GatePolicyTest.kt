package com.sypark.flightdeal.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class GatePolicyTest {

    private data class Row(val gate: String?, val id: Int)

    private fun prioritize(rows: List<Row>, minCount: Int) =
        GatePolicy.prioritize(rows, { it.gate }, minCount).map { it.id }

    @Test
    fun `한국에서 쓸 수 있는 예약처를 앞으로 보낸다`() {
        val rows = listOf(
            Row("Kupi.com", 1), Row("Trip.com", 2), Row("Aviakassa", 3), Row("Kiwi.com", 4),
        )
        assertEquals(listOf(2, 4, 1, 3), prioritize(rows, minCount = 4))
    }

    @Test
    fun `허용 예약처가 충분하면 나머지는 버린다`() {
        val rows = listOf(
            Row("Trip.com", 1), Row("Kiwi.com", 2), Row("Aviakassa", 3), Row("Kupi.com", 4),
        )
        assertEquals(listOf(1, 2), prioritize(rows, minCount = 2))
    }

    @Test
    fun `허용 예약처가 모자라면 나머지로 채운다`() {
        val rows = listOf(
            Row("Trip.com", 1), Row("Aviakassa", 2), Row("Kupi.com", 3),
        )
        // 화면이 텅 비는 것보다 낫다. 한산한 노선에서 실제로 일어난다.
        assertEquals(listOf(1, 2, 3), prioritize(rows, minCount = 3))
    }

    @Test
    fun `허용 예약처가 하나도 없어도 빈 목록을 돌려주지 않는다`() {
        val rows = listOf(Row("Aviakassa", 1), Row("Kupi.com", 2))
        assertEquals(listOf(1, 2), prioritize(rows, minCount = 2))
    }

    @Test
    fun `예약처가 null이면 허용 목록으로 치지 않는다`() {
        val rows = listOf(Row(null, 1), Row("Trip.com", 2))
        assertEquals(listOf(2, 1), prioritize(rows, minCount = 2))
    }

    @Test
    fun `빈 입력은 빈 출력이다`() {
        assertEquals(emptyList<Int>(), prioritize(emptyList(), minCount = 5))
    }

    @Test
    fun `minCount가 0이어도 비어 있지 않은 입력을 비우지 않는다`() {
        val rows = listOf(Row("Aviakassa", 1), Row("Kupi.com", 2))
        // 허용 예약처가 하나도 없는데 minCount가 0이면 "충분하다"가 참이 되어
        // 빈 목록이 나가버린다. 화면을 비우지 않는 것이 이 함수의 목적이다.
        assertEquals(listOf(1, 2), prioritize(rows, minCount = 0))
    }
}
