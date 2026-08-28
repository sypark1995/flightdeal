package com.sypark.flightdeal.data.remote

import com.google.gson.Gson
import com.sypark.flightdeal.data.remote.dto.PriceDto
import com.sypark.flightdeal.data.remote.dto.PricesForDatesResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PriceQuoteMapperTest {

    private val foundAt = Instant.parse("2026-08-28T00:00:00Z")

    private fun load(name: String): PricesForDatesResponse =
        Gson().fromJson(
            javaClass.getResourceAsStream("/fixtures/$name")!!.reader(),
            PricesForDatesResponse::class.java,
        )

    @Test
    fun `실제 편도 응답을 전부 변환한다`() {
        val response = load("v3-ICN-TYO.json")
        assertTrue(response.success)

        val quotes = response.data!!.mapNotNull {
            PriceQuoteMapper.toDomain(it, foundAt, marker = "123456")
        }

        // 31건 전부 필수 필드가 채워져 있음을 실측으로 확인했다.
        assertEquals(31, quotes.size)
    }

    @Test
    fun `첫 항목의 값이 응답과 일치한다`() {
        val dto = load("v3-ICN-TYO.json").data!!.first()
        val quote = PriceQuoteMapper.toDomain(dto, foundAt, marker = "123456")!!

        assertEquals("ICN", quote.route.origin.iata)
        assertEquals("TYO", quote.route.destination.iata)
        assertEquals(dto.price, quote.price.amount)
        assertEquals(LocalDate.parse(dto.departureAt!!.substring(0, 10)), quote.departDate)
        assertEquals(foundAt, quote.foundAt)
        assertNotNull(quote.deepLink)
        assertTrue(quote.deepLink!!.contains("marker=123456"))
    }

    @Test
    fun `편도 응답은 귀국일이 없다`() {
        val dto = load("v3-ICN-TYO.json").data!!.first()
        assertNull(PriceQuoteMapper.toDomain(dto, foundAt, "1")!!.returnDate)
    }

    @Test
    fun `왕복 응답은 귀국일이 채워진다`() {
        val dto = load("v3-ICN-TYO-roundtrip.json").data!!.first()
        val quote = PriceQuoteMapper.toDomain(dto, foundAt, "1")!!

        assertNotNull(quote.returnDate)
        assertTrue(quote.returnDate!!.isAfter(quote.departDate))
    }

    @Test
    fun `항공사 코드를 한국어 이름으로 바꾼다`() {
        val dto = PriceDto(
            originAirport = "ICN", destination = "TYO",
            departureAt = "2026-10-06T15:15:00+09:00", price = 100354,
            airline = "KE", link = "/search/x",
        )
        assertEquals("대한항공", PriceQuoteMapper.toDomain(dto, foundAt, "1")!!.airline)
    }

    @Test
    fun `모르는 항공사 코드는 코드를 그대로 쓴다`() {
        val dto = PriceDto(
            originAirport = "ICN", destination = "TYO",
            departureAt = "2026-10-06T15:15:00+09:00", price = 100354,
            airline = "XX", link = "/search/x",
        )
        assertEquals("XX", PriceQuoteMapper.toDomain(dto, foundAt, "1")!!.airline)
    }

    @Test
    fun `가격이 없으면 버린다`() {
        val dto = PriceDto(
            originAirport = "ICN", destination = "TYO",
            departureAt = "2026-10-06T15:15:00+09:00", price = null, link = "/search/x",
        )
        assertNull(PriceQuoteMapper.toDomain(dto, foundAt, "1"))
    }

    @Test
    fun `출발일이 없으면 버린다`() {
        val dto = PriceDto(
            originAirport = "ICN", destination = "TYO",
            departureAt = null, price = 100354, link = "/search/x",
        )
        assertNull(PriceQuoteMapper.toDomain(dto, foundAt, "1"))
    }

    @Test
    fun `출발일 형식이 깨졌으면 예외를 던지지 않고 버린다`() {
        val dto = PriceDto(
            originAirport = "ICN", destination = "TYO",
            departureAt = "not-a-date", price = 100354, link = "/search/x",
        )
        assertNull(PriceQuoteMapper.toDomain(dto, foundAt, "1"))
    }

    @Test
    fun `데이터가 얇은 노선도 변환된다`() {
        val response = load("v3-ICN-DAD.json")
        val quotes = response.data!!.mapNotNull { PriceQuoteMapper.toDomain(it, foundAt, "1") }
        assertEquals(20, quotes.size)
    }
}
