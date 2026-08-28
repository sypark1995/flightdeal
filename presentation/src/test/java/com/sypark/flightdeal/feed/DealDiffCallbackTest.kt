package com.sypark.flightdeal.feed

import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.DealItem
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.Won
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DealDiffCallbackTest {

    private fun item(destIata: String, price: Int) = DealItem(
        quote = PriceQuote(
            route = Route(Airport("ICN", "서울", "대한민국"), Airport(destIata, "도쿄", "일본")),
            departDate = LocalDate.of(2026, 10, 12),
            returnDate = null,
            price = Won(price),
            airline = "대한항공",
            foundAt = Instant.EPOCH,
            deepLink = null,
        ),
        discountPercent = null,
        originalPrice = null,
    )

    @Test
    fun `노선과 출발일이 같으면 같은 항목이다`() {
        assertTrue(DealDiffCallback.areItemsTheSame(item("TYO", 189_000), item("TYO", 215_000)))
    }

    @Test
    fun `목적지가 다르면 다른 항목이다`() {
        assertFalse(DealDiffCallback.areItemsTheSame(item("TYO", 189_000), item("BKK", 189_000)))
    }

    @Test
    fun `가격이 바뀌면 내용이 다르다`() {
        assertFalse(DealDiffCallback.areContentsTheSame(item("TYO", 189_000), item("TYO", 215_000)))
    }
}
