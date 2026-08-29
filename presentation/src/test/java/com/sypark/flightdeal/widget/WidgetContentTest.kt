package com.sypark.flightdeal.widget

import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.tracking.TrackedItem
import com.sypark.flightdeal.tracking.previousDifferingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class WidgetContentTest {

    private val route = Route(Airport.INCHEON, Airport("TYO", "도쿄", "일본"))

    private fun tracked(id: Long, createdAt: Instant, hasDeparted: Boolean) = TrackedRoute(
        id = id,
        route = route,
        departDate = if (hasDeparted) LocalDate.of(2020, 1, 1) else LocalDate.of(2099, 1, 1),
        returnDate = null,
        tripType = TripType.ONE_WAY,
        targetPrice = null,
        notifiedPrice = null,
        createdAt = createdAt,
    )

    private fun snapshot(price: Int, at: Long) =
        PriceSnapshot(1L, Won(price), TripType.ONE_WAY, Instant.ofEpochSecond(at))

    private fun item(
        id: Long = 1L,
        createdAt: Instant = Instant.EPOCH,
        latest: PriceSnapshot? = null,
        previous: PriceSnapshot? = null,
        history: List<PriceSnapshot> = emptyList(),
        hasDeparted: Boolean = false,
    ) = TrackedItem(
        tracked = tracked(id, createdAt, hasDeparted),
        latest = latest,
        previous = previous,
        history = history,
        hasDeparted = hasDeparted,
    )

    @Test fun `지난 여정은 뒤로 민다`() {
        val departed = item(id = 1L, createdAt = Instant.ofEpochSecond(200), hasDeparted = true)
        val upcoming = item(id = 2L, createdAt = Instant.ofEpochSecond(100), hasDeparted = false)

        // 등록은 지난 여정 쪽이 더 최근이지만(200 > 100), 출발일이 지났으니 뒤로 밀려야 한다.
        val rows = widgetRows(listOf(departed, upcoming), limit = 10)

        assertEquals(listOf(false, true), rows.map { it.hasDeparted })
    }

    @Test fun `limit개까지만 남긴다`() {
        val items = (1..5L).map { item(id = it, createdAt = Instant.ofEpochSecond(it)) }

        val rows = widgetRows(items, limit = 3)

        assertEquals(3, rows.size)
    }

    @Test fun `관측이 없으면 가격이 null이다`() {
        val rows = widgetRows(listOf(item(latest = null)), limit = 3)

        assertNull(rows.single().price)
    }

    @Test fun `값이 그대로인 관측이 쌓여도 마지막 변동을 previous로 준다`() {
        // 30만 -> 28만(하락) -> 28만 -> 28만 : 값이 그대로인 폴링이 두 번 더 쌓인다.
        val history = listOf(
            snapshot(300_000, 1),
            snapshot(280_000, 2),
            snapshot(280_000, 3),
            snapshot(280_000, 4),
        )
        val latest = history.last()
        // TrackingViewModel과 정확히 같은 함수로 previous를 만든다 — 두 화면이 다른
        // 규칙으로 계산하면 같은 노선에 대해 다른 화살표가 뜬다.
        val previous = previousDifferingSnapshot(history, latest)

        val rows = widgetRows(
            listOf(item(latest = latest, previous = previous, history = history)),
            limit = 3,
        )

        assertEquals(Won(300_000), rows.single().previous)
    }
}
