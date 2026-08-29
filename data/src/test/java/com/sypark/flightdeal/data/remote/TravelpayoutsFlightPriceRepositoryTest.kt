package com.sypark.flightdeal.data.remote

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class TravelpayoutsFlightPriceRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var api: TravelpayoutsApi
    private lateinit var repository: TravelpayoutsFlightPriceRepository

    private var now: Instant = Instant.parse("2026-08-28T00:00:00Z")

    /** 캐시 만료를 검증하려면 시간이 흘러야 한다. Clock.fixed로는 그 분기를 밟을 수 없다. */
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }
    private val incheon = Airport("ICN", "서울", "대한민국")
    private val route = Route(incheon, Airport("TYO", "도쿄", "일본"))

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        api = Retrofit.Builder()
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

    private fun twoDestinationRepository() = TravelpayoutsFlightPriceRepository(
        api = api, marker = "123456", clock = clock, destinations = listOf("TYO", "BKK"),
    )

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
    fun `레이트 리밋은 재시도 가능한 오류다`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))

        val result = repository.cheapestDeals(incheon, limit = 10, tripType = TripType.ONE_WAY)

        // 잠시 뒤 다시 하면 되는 상황이므로 재시도 버튼이 떠야 한다.
        assertTrue(result is AppResult.NetworkError)
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

    @Test
    fun `캘린더 딜은 예약처로 거르지 않은 calendarPrices보다 항목이 적을 수 있다`() = runTest {
        // 같은 날짜에 CIS 전용 예약처 항목이 하나 더 있다. calendarPrices는 그대로 두 건을
        // 다 주지만, calendarDeals는 딜 피드·추적과 같은 규칙으로 하루에 한 건만 남겨야 한다.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"success":true,"data":[
                  {"origin_airport":"ICN","destination":"TYO","departure_at":"2026-10-06T15:15:00+09:00",
                   "price":100000,"airline":"KE","gate":"Kupi.com","link":"/search/a"},
                  {"origin_airport":"ICN","destination":"TYO","departure_at":"2026-10-06T09:20:00+09:00",
                   "price":120000,"airline":"KE","gate":"Trip.com","link":"/search/b"}
                ]}"""
            )
        )

        val deals = (repository.calendarDeals(route, YearMonth.of(2026, 10), TripType.ONE_WAY)
            as AppResult.Success).data.deals

        // 더 싸더라도 한국에서 결제가 안 되는 곳이면 소용없다. 딜 피드가 고른 것과 같아야
        // 캘린더를 누르고 들어갔을 때 값이 갈리지 않는다.
        assertEquals(1, deals.size)
        assertEquals(120_000, deals.first().price.amount)
    }

    @Test
    fun `그 날의 유일한 예약처가 CIS 예약처면 그 날은 비운다`() = runTest {
        // 10/06은 Kupi.com(CIS)뿐이고, 10/07은 Trip.com이 있다. 예약처 규칙을
        // 날짜별로 적용하면 10/06은 그 날 유일한 항목이라는 이유로 폴백이 걸려
        // 그대로 노출된다. 규칙을 달 전체에 먼저 적용하면, 이 달에 Trip.com이
        // 있으므로 폴백이 걸리지 않고 10/06은 후보에서 아예 빠져야 한다.
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

        val deals = (repository.calendarDeals(route, YearMonth.of(2026, 10), TripType.ONE_WAY)
            as AppResult.Success).data.deals

        // 10/06 자리는 비어야 한다 — 한국에서 예약할 수 없는 곳뿐인 날에 값을
        // 채우면 사용자가 예약을 완료할 수 없는 페이지로 들어간다.
        assertEquals(1, deals.size)
        assertEquals(LocalDate.of(2026, 10, 7), deals.first().departDate)
    }

    @Test
    fun `예약 가능한 곳이 없던 날은 unbookableDates에 담긴다`() = runTest {
        // 위 테스트와 같은 응답이다 — 10/06은 Kupi.com(CIS)뿐이고 10/07은 Trip.com이다.
        // 화면이 값이 없던 날과 구별해 보여주려면 10/06이 왜 비었는지를 조회가
        // 함께 돌려줘야 한다. 새 요청 없이, 이미 가진 원본 응답과 걸러낸 결과의
        // 차집합으로 구할 수 있어야 한다.
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

        val result = (repository.calendarDeals(route, YearMonth.of(2026, 10), TripType.ONE_WAY)
            as AppResult.Success).data

        assertEquals(setOf(LocalDate.of(2026, 10, 6)), result.unbookableDates)
    }

    @Test
    fun `예약 가능한 날은 unbookableDates에 들어가지 않는다`() = runTest {
        // 10/07은 Trip.com이 있어 deals에도 담긴다. unbookableDates는 예약 가능한
        // 곳이 하나도 없던 날만 말해야 한다 — 여기 같이 담기면 화면이 값이 있는
        // 날마저 "—"로 덮어써 버린다.
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

        val result = (repository.calendarDeals(route, YearMonth.of(2026, 10), TripType.ONE_WAY)
            as AppResult.Success).data

        assertTrue(LocalDate.of(2026, 10, 7) !in result.unbookableDates)
    }

    @Test
    fun `캘린더 딜은 날짜당 한 건만 남긴다`() = runTest {
        enqueueFixture("v3-ICN-TYO.json")

        val deals = (repository.calendarDeals(route, YearMonth.of(2026, 10), TripType.ONE_WAY)
            as AppResult.Success).data.deals

        assertEquals(deals.size, deals.map { it.departDate }.distinct().size)
    }

    @Test
    fun `같은 요청을 연달아 보내면 한 번만 조회한다`() = runTest {
        enqueueFixture("v3-ICN-TYO.json")

        repository.cheapestDeals(incheon, limit = 10, tripType = TripType.ONE_WAY)
        repository.priceStats(route, YearMonth.now(clock).plusMonths(2), TripType.ONE_WAY)

        // 응답을 하나만 큐에 넣었다. 캐시가 없으면 두 번째 호출이 응답을 기다리다 실패한다.
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `목적지 하나가 실패해도 나머지는 보여준다`() = runTest {
        enqueueFixture("v3-ICN-TYO.json")
        server.enqueue(MockResponse().setResponseCode(500))

        val result = twoDestinationRepository()
            .cheapestDeals(incheon, limit = 10, tripType = TripType.ONE_WAY)

        // 다섯 개가 멀쩡한데 하나 때문에 전체 화면이 오류가 되면 안 된다.
        assertEquals(1, (result as AppResult.Success).data.size)
    }

    @Test
    fun `목적지가 전부 실패하면 오류다`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))

        val result = twoDestinationRepository()
            .cheapestDeals(incheon, limit = 10, tripType = TripType.ONE_WAY)

        assertTrue(result !is AppResult.Success)
    }

    @Test
    fun `캐시가 만료되면 다시 조회한다`() = runTest {
        enqueueFixture("v3-ICN-TYO.json")
        enqueueFixture("v3-ICN-TYO.json")

        repository.cheapestDeals(incheon, limit = 10, tripType = TripType.ONE_WAY)
        now = now.plus(Duration.ofMinutes(6))
        repository.cheapestDeals(incheon, limit = 10, tripType = TripType.ONE_WAY)

        // TTL은 5분이다. 6분 뒤에도 캐시를 쓰면 사용자가 낡은 가격을 보게 된다.
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `여정 종류가 다르면 따로 조회한다`() = runTest {
        enqueueFixture("v3-ICN-TYO.json")
        enqueueFixture("v3-ICN-TYO-roundtrip.json")

        repository.cheapestDeals(incheon, limit = 10, tripType = TripType.ONE_WAY)
        repository.cheapestDeals(incheon, limit = 10, tripType = TripType.ROUND_TRIP)

        // 편도와 왕복은 다른 운임이다. 같은 캐시를 쓰면 안 된다.
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `추적 가격도 한국에서 예약 가능한 예약처를 우선한다`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"success":true,"data":[
                  {"origin_airport":"ICN","destination":"TYO","departure_at":"2026-10-06T09:20:00+09:00",
                   "return_at":"2026-10-12T10:40:00+09:00","price":301430,"airline":"KE",
                   "gate":"Farera","link":"/search/a"},
                  {"origin_airport":"ICN","destination":"TYO","departure_at":"2026-10-06T15:15:00+09:00",
                   "return_at":"2026-10-12T10:40:00+09:00","price":330000,"airline":"KE",
                   "gate":"Trip.com","link":"/search/b"}
                ]}"""
            )
        )

        val result = repository.trackedPrice(
            route = route,
            departDate = LocalDate.of(2026, 10, 6),
            returnDate = LocalDate.of(2026, 10, 12),
            tripType = TripType.ROUND_TRIP,
        )

        // 더 싼 301,430은 한국에서 결제가 안 되는 예약처다. 딜 피드가 고른 것과 같아야 한다 —
        // 다르면 등록 직후 첫 실행에서 있지도 않은 하락이 잡힌다.
        assertEquals(Won(330_000), (result as AppResult.Success).data)
    }

    @Test
    fun `귀국일이 다르면 같은 여정으로 치지 않는다`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"success":true,"data":[
                  {"origin_airport":"ICN","destination":"TYO","departure_at":"2026-10-13T09:20:00+09:00",
                   "return_at":"2026-10-20T10:40:00+09:00","price":320806,"airline":"KE",
                   "gate":"Trip.com","link":"/search/a"},
                  {"origin_airport":"ICN","destination":"TYO","departure_at":"2026-10-13T15:15:00+09:00",
                   "return_at":"2026-10-16T10:40:00+09:00","price":326181,"airline":"KE",
                   "gate":"Trip.com","link":"/search/b"}
                ]}"""
            )
        )

        val result = repository.trackedPrice(
            route = route,
            departDate = LocalDate.of(2026, 10, 13),
            returnDate = LocalDate.of(2026, 10, 16),
            tripType = TripType.ROUND_TRIP,
        )

        // 출발일만 맞추면 10/20 귀국편이 잡혀 매번 가짜 변동이 뜬다.
        assertEquals(Won(326_181), (result as AppResult.Success).data)
    }
}
