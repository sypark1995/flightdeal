package com.sypark.flightdeal.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sypark.flightdeal.data.local.entity.TrackedRouteEntity
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class RoomTrackedRouteRepositoryTest {

    private lateinit var db: FlightDealDatabase
    private lateinit var tracked: RoomTrackedRouteRepository
    private lateinit var history: RoomPriceHistoryRepository

    private val clock = Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC)
    private val route = Route(Airport.INCHEON, Airport("TYO", "도쿄", "일본"))

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlightDealDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tracked = RoomTrackedRouteRepository(db.trackedRouteDao(), clock)
        history = RoomPriceHistoryRepository(db.priceSnapshotDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun addTokyo(tripType: TripType = TripType.ROUND_TRIP) = tracked.add(
        route = route,
        departDate = LocalDate.of(2026, 10, 12),
        returnDate = LocalDate.of(2026, 10, 16),
        tripType = tripType,
        targetPrice = Won(280_000),
        notifiedPrice = null,
    ).id

    @Test
    fun `등록한 노선을 도메인 모델로 되돌려준다`() = runTest {
        val id = addTokyo()

        val saved = tracked.observeAll().first().single()

        assertEquals(id, saved.id)
        assertEquals("ICN", saved.route.origin.iata)
        assertEquals("TYO", saved.route.destination.iata)
        assertEquals(LocalDate.of(2026, 10, 12), saved.departDate)
        assertEquals(LocalDate.of(2026, 10, 16), saved.returnDate)
        assertEquals(TripType.ROUND_TRIP, saved.tripType)
        assertEquals(Won(280_000), saved.targetPrice)
    }

    @Test
    fun `도시 이름을 표시할 수 있게 채워 돌려준다`() = runTest {
        addTokyo()

        val saved = tracked.observeAll().first().single()

        // DB에는 IATA만 있다. 화면은 "TYO"가 아니라 "도쿄"를 보여줘야 한다.
        assertEquals("도쿄", saved.route.destination.cityKo)
        assertEquals("서울", saved.route.origin.cityKo)
    }

    @Test
    fun `편도로 등록하면 편도로 저장된다`() = runTest {
        addTokyo(TripType.ONE_WAY)

        assertEquals(TripType.ONE_WAY, tracked.observeAll().first().single().tripType)
    }

    @Test
    fun `목표가를 안 정해도 등록된다`() = runTest {
        tracked.add(route, LocalDate.of(2026, 10, 12), null, TripType.ONE_WAY, targetPrice = null, notifiedPrice = null)

        val saved = tracked.observeAll().first().single()
        assertNull(saved.targetPrice)
        assertNull(saved.returnDate)
    }

    @Test
    fun `해제하면 목록에서 사라진다`() = runTest {
        val id = addTokyo()

        tracked.remove(id)

        assertEquals(0, tracked.observeAll().first().size)
    }

    @Test
    fun `스냅샷을 넣고 최근 값을 읽는다`() = runTest {
        val id = addTokyo()
        history.append(PriceSnapshot(id, Won(300_000), TripType.ROUND_TRIP, clock.instant()))
        history.append(
            PriceSnapshot(id, Won(280_000), TripType.ROUND_TRIP, clock.instant().plusSeconds(60))
        )

        val latest = history.latest(id)!!

        assertEquals(Won(280_000), latest.price)
        assertEquals(TripType.ROUND_TRIP, latest.tripType)
    }

    @Test
    fun `스냅샷의 여정 종류가 보존된다`() = runTest {
        val id = addTokyo(TripType.ONE_WAY)
        history.append(PriceSnapshot(id, Won(100_000), TripType.ONE_WAY, clock.instant()))

        // 종류가 섞이면 왕복과 편도를 비교해 가짜 하락 알림이 나간다.
        assertEquals(TripType.ONE_WAY, history.latest(id)!!.tripType)
    }

    @Test
    fun `지정한 일수 밖의 이력은 관찰 대상이 아니다`() = runTest {
        val id = addTokyo()
        val now = clock.instant()
        history.append(PriceSnapshot(id, Won(300_000), TripType.ROUND_TRIP, now.minusSeconds(40 * 86_400)))
        history.append(PriceSnapshot(id, Won(280_000), TripType.ROUND_TRIP, now))

        assertEquals(1, history.observeHistory(id, days = 30).first().size)
    }

    @Test
    fun `오래된 이력을 정리한다`() = runTest {
        val id = addTokyo()
        val now = clock.instant()
        history.append(PriceSnapshot(id, Won(300_000), TripType.ROUND_TRIP, now.minusSeconds(100 * 86_400)))
        history.append(PriceSnapshot(id, Won(280_000), TripType.ROUND_TRIP, now))

        history.pruneOlderThan(days = 90)

        assertEquals(1, history.observeHistory(id, days = 365).first().size)
    }

    @Test
    fun `음수 보관 기간은 거부한다`() = runTest {
        val id = addTokyo()
        history.append(PriceSnapshot(id, Won(300_000), TripType.ROUND_TRIP, clock.instant()))

        // 음수를 그대로 받으면 기준 시각이 미래가 되어 이력 전체가 지워진다.
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { history.pruneOlderThan(days = -1) }
        }
        assertEquals(1, history.observeHistory(id, days = 365).first().size)
    }

    @Test
    fun `읽을 수 없는 행이 있어도 나머지는 돌려준다`() = runTest {
        val good = addTokyo()
        db.trackedRouteDao().insert(
            TrackedRouteEntity(
                originIata = "ICN",
                destinationIata = "BKK",
                departDate = "not-a-date",
                returnDate = "",
                tripType = "ROUND_TRIP",
                targetPrice = null,
                createdAt = 1_800_000_000L,
            )
        )

        // 행 하나가 망가졌다고 추적 화면 전체가 열리지 않으면 안 된다.
        val saved = tracked.observeAll().first()
        assertEquals(1, saved.size)
        assertEquals(good, saved.single().id)
    }

    @Test
    fun `같은 노선을 두 번 등록해도 하나만 남는다`() = runTest {
        val first = addTokyo()
        val second = addTokyo()

        // 두 번 누르면 카드가 두 장 뜨고 알림도 두 번 간다.
        assertEquals(first, second)
        assertEquals(1, tracked.observeAll().first().size)
    }

    @Test
    fun `여정 종류가 다르면 따로 등록된다`() = runTest {
        val roundTrip = addTokyo(TripType.ROUND_TRIP)
        val oneWay = addTokyo(TripType.ONE_WAY)

        // 왕복과 편도는 가격대가 다르다. 별개의 추적이다.
        assertNotEquals(roundTrip, oneWay)
        assertEquals(2, tracked.observeAll().first().size)
    }

    @Test
    fun `귀국일이 없는 편도끼리도 중복으로 잡힌다`() = runTest {
        val first = tracked.add(route, LocalDate.of(2026, 10, 12), null, TripType.ONE_WAY, null, null)
        val second = tracked.add(route, LocalDate.of(2026, 10, 12), null, TripType.ONE_WAY, null, null)

        // SQLite의 유니크 인덱스는 NULL을 서로 다른 값으로 본다.
        // 편도의 귀국일을 NULL로 저장하면 중복이 그대로 쌓인다.
        assertEquals(first.id, second.id)
        assertEquals(1, tracked.observeAll().first().size)
    }

    @Test
    fun `등록 결과가 새로 만들어졌는지 알려준다`() = runTest {
        val first = tracked.add(route, LocalDate.of(2026, 10, 12), null, TripType.ONE_WAY, null, null)
        val second = tracked.add(route, LocalDate.of(2026, 10, 12), null, TripType.ONE_WAY, null, null)

        // 화면이 "추적을 시작했어요"와 "이미 추적 중이에요"를 구분할 근거다.
        assertEquals(true, first.isNew)
        assertEquals(false, second.isNew)
    }

    @Test
    fun `편도로 등록하면 귀국일이 null로 돌아온다`() = runTest {
        tracked.add(route, LocalDate.of(2026, 10, 12), null, TripType.ONE_WAY, null, null)

        // 저장은 빈 문자열이지만 도메인은 null이어야 한다. 빈 문자열이 새어 나가면
        // 화면이 "편도"인데 귀국일 자리에 빈칸이 생긴다.
        assertNull(tracked.observeAll().first().single().returnDate)
    }
}
