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

    @Test
    fun `편도 응답의 모든 항목이 온전한 값을 갖는다`() {
        val quotes = load("v3-ICN-TYO.json").data!!.mapNotNull {
            PriceQuoteMapper.toDomain(it, foundAt, marker = "123456")
        }

        // 개수만 세는 검사는 2번째 이후 레코드가 망가져도 통과한다. 전부 훑는다.
        quotes.forEach { quote ->
            assertEquals("ICN", quote.route.origin.iata)
            assertEquals("인천", quote.route.origin.cityKo)
            assertEquals("TYO", quote.route.destination.iata)
            assertEquals("도쿄", quote.route.destination.cityKo)
            assertTrue("가격이 0 이하: ${quote.price.amount}", quote.price.amount > 0)
            assertEquals(2026, quote.departDate.year)
            assertEquals(10, quote.departDate.monthValue)
            assertNotNull(quote.deepLink)
            assertTrue(quote.deepLink!!.startsWith("https://www.aviasales.com/"))
            // 항공사 코드가 표에 없으면 코드가 그대로 남는다. 그것도 값이지만
            // 두 글자짜리 대문자만 남았다면 표를 넓혀야 한다는 신호다.
            assertNotNull(quote.airline)
        }
    }

    /** 경유·소요시간 조합만 바꿔가며 검증할 때 나머지 필수 필드를 채워주는 헬퍼. */
    private fun priceDto(
        transfers: Int? = 0,
        returnTransfers: Int? = null,
        duration: Int? = null,
        durationTo: Int? = null,
        durationBack: Int? = null,
        returnAt: String? = "2026-10-16T10:00:00+09:00",
    ) = PriceDto(
        originAirport = "ICN", destination = "TYO",
        departureAt = "2026-10-06T15:15:00+09:00", returnAt = returnAt, price = 100354,
        airline = "KE", link = "/search/x",
        transfers = transfers, returnTransfers = returnTransfers,
        duration = duration, durationTo = durationTo, durationBack = durationBack,
    )

    @Test
    fun `경유 횟수는 가는 편과 오는 편 중 많은 쪽이다`() {
        // 실제 응답에 있던 조합이다 — 가는 편 직항, 오는 편 1회 경유.
        val dto = priceDto(transfers = 0, returnTransfers = 1)

        assertEquals(1, PriceQuoteMapper.toDomain(dto, foundAt, "1")!!.transfers)
    }

    @Test
    fun `편도면 오는 편 경유는 보지 않는다`() {
        // 편도 조회에는 return_at이 없다. return_transfers가 0으로 와도 의미가 없다.
        val dto = priceDto(transfers = 1, returnTransfers = 0, returnAt = null)

        assertEquals(1, PriceQuoteMapper.toDomain(dto, foundAt, "1")!!.transfers)
    }

    @Test
    fun `왕복 duration이 아니라 duration_to를 쓴다`() {
        // 왕복 응답의 duration은 왕복 합계(300 = 150 + 150)다.
        // 그대로 쓰면 2시간 30분짜리가 5시간으로 보인다.
        val dto = priceDto(duration = 300, durationTo = 150, durationBack = 150)

        assertEquals(150, PriceQuoteMapper.toDomain(dto, foundAt, "1")!!.outboundMinutes)
    }

    @Test
    fun `값이 없으면 null이다`() {
        val dto = priceDto(transfers = null, durationTo = null)
        val quote = PriceQuoteMapper.toDomain(dto, foundAt, "1")!!

        assertNull(quote.transfers)
        assertNull(quote.outboundMinutes)
    }

    @Test
    fun `왕복 응답의 모든 항목에서 귀국일이 출발일보다 늦다`() {
        val quotes = load("v3-ICN-TYO-roundtrip.json").data!!.mapNotNull {
            PriceQuoteMapper.toDomain(it, foundAt, marker = "123456")
        }

        assertEquals(44, quotes.size)
        quotes.forEach { quote ->
            assertNotNull("귀국일이 없다: $quote", quote.returnDate)
            assertTrue(
                "귀국일이 출발일보다 빠르다: ${quote.departDate} → ${quote.returnDate}",
                quote.returnDate!!.isAfter(quote.departDate),
            )
        }
    }
}
