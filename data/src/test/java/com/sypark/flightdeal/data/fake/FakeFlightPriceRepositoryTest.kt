package com.sypark.flightdeal.data.fake

import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.AppResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeFlightPriceRepositoryTest {

    @Test
    fun `기본 동작은 특가 목록을 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository()

        val result = repo.cheapestDeals(Airport.INCHEON, limit = 10)

        assertTrue(result is AppResult.Success)
        assertTrue((result as AppResult.Success).data.isNotEmpty())
    }

    @Test
    fun `limit보다 많이 돌려주지 않는다`() = runTest {
        val repo = FakeFlightPriceRepository()

        val result = repo.cheapestDeals(Airport.INCHEON, limit = 2)

        assertEquals(2, (result as AppResult.Success).data.size)
    }

    @Test
    fun `EmptyData 모드는 Empty를 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository(FakeFlightPriceRepository.Behavior.EmptyData)

        val result = repo.cheapestDeals(Airport.INCHEON, limit = 10)

        assertEquals(AppResult.Empty, result)
    }

    @Test
    fun `Failing 모드는 NetworkError를 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository(FakeFlightPriceRepository.Behavior.Failing)

        val result = repo.cheapestDeals(Airport.INCHEON, limit = 10)

        assertTrue(result is AppResult.NetworkError)
    }
}
