package com.sypark.flightdeal.tracking

import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PriceChartGeometryTest {

    private fun snapshot(price: Int, atEpochSecond: Long) = PriceSnapshot(
        trackedRouteId = 1,
        price = Won(price),
        tripType = TripType.ROUND_TRIP,
        capturedAt = Instant.ofEpochSecond(atEpochSecond),
    )

    @Test
    fun `가로 위치는 인덱스가 아니라 시각에 비례한다`() {
        // 첫 점에서 1시간 뒤, 그리고 25시간 뒤. 인덱스로 놓으면 0, 0.5, 1이 된다.
        val geometry = PriceChartGeometry.of(
            listOf(
                snapshot(300_000, 0),
                snapshot(280_000, 3_600),
                snapshot(260_000, 90_000),
            ),
            targetPrice = null,
        )

        assertEquals(0f, geometry.points[0].x, 0.001f)
        assertEquals(0.04f, geometry.points[1].x, 0.005f)
        assertEquals(1f, geometry.points[2].x, 0.001f)
    }

    @Test
    fun `가격이 모두 같아도 NaN이 되지 않는다`() {
        val geometry = PriceChartGeometry.of(
            listOf(snapshot(300_000, 0), snapshot(300_000, 3_600)),
            targetPrice = null,
        )

        // 나눗셈의 분모가 0이다. 가운데 수평선으로 그린다.
        geometry.points.forEach { assertEquals(0.5f, it.y, 0.001f) }
        assertTrue(geometry.points.none { it.y.isNaN() })
    }

    @Test
    fun `점이 하나면 한가운데에 놓는다`() {
        val geometry = PriceChartGeometry.of(listOf(snapshot(300_000, 0)), targetPrice = null)

        assertEquals(1, geometry.points.size)
        assertEquals(0.5f, geometry.points.single().x, 0.001f)
        assertEquals(0.5f, geometry.points.single().y, 0.001f)
    }

    @Test
    fun `비싼 값이 위로 간다`() {
        val geometry = PriceChartGeometry.of(
            listOf(snapshot(300_000, 0), snapshot(200_000, 3_600)),
            targetPrice = null,
        )

        assertEquals(0f, geometry.points[0].y, 0.001f)
        assertEquals(1f, geometry.points[1].y, 0.001f)
    }

    @Test
    fun `목표가가 범위 밖이면 선을 그리지 않는다`() {
        // 축을 목표가까지 늘리면 실제 변동이 납작해진다.
        val geometry = PriceChartGeometry.of(
            listOf(snapshot(300_000, 0), snapshot(280_000, 3_600)),
            targetPrice = Won(50_000),
        )

        assertNull(geometry.targetY)
        assertEquals(Won(280_000), geometry.scaleLow)
        assertEquals(Won(300_000), geometry.scaleHigh)
    }

    @Test
    fun `목표가가 범위 안이면 비율대로 놓는다`() {
        val geometry = PriceChartGeometry.of(
            listOf(snapshot(300_000, 0), snapshot(200_000, 3_600)),
            targetPrice = Won(250_000),
        )

        assertEquals(0.5f, geometry.targetY!!, 0.001f)
    }

    @Test
    fun `이력이 없으면 점도 없다`() {
        val geometry = PriceChartGeometry.of(emptyList(), targetPrice = null)

        assertTrue(geometry.points.isEmpty())
    }
}
