package com.sypark.flightdeal.data.remote

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TripType
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

class TravelpayoutsFlightPriceRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: TravelpayoutsFlightPriceRepository

    private val clock = Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC)
    private val incheon = Airport("ICN", "서울", "대한민국")
    private val route = Route(incheon, Airport("TYO", "도쿄", "일본"))

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TravelpayoutsApi::class.java)
        // 목적지를 하나로 고정한다. 기본값 6개를 쓰면 요청이 6번 나가 큐가 모자란다.
        repository = TravelpayoutsFlightPriceRepository(
            api = api, marker = "123456", clock = clock, destinations = listOf("TYO"),
        )
    }

    @After
    fun tearDown() = server.shutdown()

    private fun enqueueFixture(name: String) {
        val body = javaClass.getResourceAsStream("/fixtures/$name")!!.readBytes().decodeToString()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
    }

    @Test
    fun `실제 응답을 특가 목록으로 변환한다`() = runTest {
        enqueueFixture("v3-ICN-TYO.json")

        val result = repository.cheapestDeals(incheon, limit = 10, tripType = TripType.ONE_WAY)

        assertTrue(result is AppResult.Success)
        val deals = (result as AppResult.Success).data
        // 목적지 하나당 그 달의 최저가 하나. destinations를 TYO 하나로 줬으므로 1건이다.
        assertEquals(1, deals.size)
        assertTrue(deals.all { it.deepLink!!.contains("marker=123456") })
    }

    @Test
    fun `왕복 요청은 one_way false와 return_at을 보낸다`() = runTest {
        enqueueFixture("v3-ICN-TYO-roundtrip.json")

        repository.cheapestDeals(incheon, limit = 5, tripType = TripType.ROUND_TRIP)

        val url = server.takeRequest().requestUrl!!
        assertEquals("false", url.queryParameter("one_way"))
        assertTrue(url.queryParameter("return_at") != null)
    }

    @Test
    fun `편도 요청은 one_way true를 보내고 return_at을 보내지 않는다`() = runTest {
        enqueueFixture("v3-ICN-TYO.json")

        repository.cheapestDeals(incheon, limit = 5, tripType = TripType.ONE_WAY)

        val url = server.takeRequest().requestUrl!!
        assertEquals("true", url.queryParameter("one_way"))
        assertEquals(null, url.queryParameter("return_at"))
    }

    @Test
    fun `빈 결과는 Empty다`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true,"data":[]}"""))

        val result = repository.cheapestDeals(incheon, limit = 10, tripType = TripType.ONE_WAY)

        assertEquals(AppResult.Empty, result)
    }

    @Test
    fun `200이어도 success가 false면 Unknown이다`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"error":"something","data":null,"success":false}""")
        )

        val result = repository.cheapestDeals(incheon, limit = 10, tripType = TripType.ONE_WAY)

        assertTrue(result is AppResult.Unknown)
    }

    @Test
    fun `한국에서 예약 가능한 예약처를 우선해 고른다`() = runTest {
        // 첫 항목이 더 싸지만 CIS 예약처다. 두 번째가 Trip.com이다.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"success":true,"data":[
                  {"origin_airport":"ICN","destination":"TYO","departure_at":"2026-10-06T15:15:00+09:00",
                   "price":100000,"airline":"KE","gate":"Kupi.com","link":"/search/a"},
                  {"origin_airport":"ICN","destination":"TYO","departure_at":"2026-10-07T15:15:00+09:00",
                   "price":120000,"airline":"KE","gate":"Trip.com","link":"/search/b"}
                ]}"""
            )
        )

        val deals = (repository.cheapestDeals(incheon, limit = 10, tripType = TripType.ONE_WAY)
            as AppResult.Success).data

        // 더 싸더라도 한국에서 결제가 안 되는 곳이면 소용없다.
        assertEquals(1, deals.size)
        assertEquals(120_000, deals.first().price.amount)
    }

    @Test
    fun `401은 Unknown이다 재시도해도 소용없다`() = runTest {
        // 실제 API는 401에 JSON이 아니라 평문 Unauthorized를 돌려준다.
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        val result = repository.cheapestDeals(incheon, limit = 10, tripType = TripType.ONE_WAY)

        assertTrue(result is AppResult.Unknown)
    }

    @Test
    fun `연결이 끊기면 NetworkError다 재시도할 만하다`() = runTest {
        server.shutdown()

        val result = repository.cheapestDeals(incheon, limit = 10, tripType = TripType.ONE_WAY)

        assertTrue(result is AppResult.NetworkError)
    }

    @Test
    fun `분포는 그 달의 가격들로 계산한다`() = runTest {
        enqueueFixture("v3-ICN-TYO.json")

        val result = repository.priceStats(route, YearMonth.of(2026, 10), TripType.ONE_WAY)

        assertTrue(result is AppResult.Success)
        assertEquals(31, (result as AppResult.Success).data.sampleCount)
    }

    @Test
    fun `캘린더 조회는 그 달의 날짜별 가격을 돌려준다`() = runTest {
        enqueueFixture("v3-ICN-TYO.json")

        val result = repository.calendarPrices(route, YearMonth.of(2026, 10), TripType.ONE_WAY)

        assertEquals(31, (result as AppResult.Success).data.size)
    }
}
