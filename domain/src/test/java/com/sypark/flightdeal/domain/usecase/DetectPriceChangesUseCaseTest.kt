package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.Direction
import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DetectPriceChangesUseCaseTest {

    private val useCase = DetectPriceChangesUseCase()

    private val route = Route(
        origin = Airport("ICN", "서울", "대한민국"),
        destination = Airport("TYO", "도쿄", "일본"),
    )

    private fun tracked(targetPrice: Won? = null) = TrackedRoute(
        id = 1L,
        route = route,
        departDate = LocalDate.of(2026, 10, 12),
        returnDate = LocalDate.of(2026, 10, 16),
        tripType = TripType.ROUND_TRIP,
        targetPrice = targetPrice,
        createdAt = Instant.EPOCH,
    )

    private fun snapshot(price: Int) = PriceSnapshot(1L, Won(price), TripType.ROUND_TRIP, Instant.EPOCH)

    @Test
    fun `가격이 내리면 DOWN 변동을 돌려준다`() {
        val change = useCase(tracked(), snapshot(215_000), Won(189_000))!!
        assertEquals(Direction.DOWN, change.direction)
        assertEquals(Won(215_000), change.previous)
        assertEquals(Won(189_000), change.current)
    }

    @Test
    fun `가격이 오르면 UP 변동을 돌려준다`() {
        val change = useCase(tracked(), snapshot(189_000), Won(215_000))!!
        assertEquals(Direction.UP, change.direction)
    }

    @Test
    fun `가격이 그대로면 null이다`() {
        assertNull(useCase(tracked(), snapshot(189_000), Won(189_000)))
    }

    @Test
    fun `직전 스냅샷이 없으면 알릴 변동이 없으므로 null이다`() {
        assertNull(useCase(tracked(), previous = null, current = Won(189_000)))
    }

    @Test
    fun `목표가 이하로 내려오면 reachedTarget이 true다`() {
        val change = useCase(tracked(targetPrice = Won(200_000)), snapshot(215_000), Won(189_000))!!
        assertTrue(change.reachedTarget)
    }

    @Test
    fun `목표가에 못 미치면 reachedTarget이 false다`() {
        val change = useCase(tracked(targetPrice = Won(150_000)), snapshot(215_000), Won(189_000))!!
        assertFalse(change.reachedTarget)
    }

    @Test
    fun `목표가와 정확히 같으면 도달로 본다`() {
        val change = useCase(tracked(targetPrice = Won(189_000)), snapshot(215_000), Won(189_000))!!
        assertTrue(change.reachedTarget)
    }

    @Test
    fun `목표가를 설정하지 않았으면 reachedTarget은 false다`() {
        val change = useCase(tracked(targetPrice = null), snapshot(215_000), Won(189_000))!!
        assertFalse(change.reachedTarget)
    }

    @Test
    fun `가격이 올랐어도 목표가 이하면 도달로 본다`() {
        val change = useCase(tracked(targetPrice = Won(200_000)), snapshot(150_000), Won(189_000))!!
        assertEquals(Direction.UP, change.direction)
        assertTrue(change.reachedTarget)
    }
}
