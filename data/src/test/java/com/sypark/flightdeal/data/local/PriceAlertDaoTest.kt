package com.sypark.flightdeal.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sypark.flightdeal.data.local.entity.PriceAlertEntity
import com.sypark.flightdeal.data.local.entity.TrackedRouteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PriceAlertDaoTest {

    private lateinit var db: FlightDealDatabase
    private lateinit var routes: TrackedRouteDao
    private lateinit var alerts: PriceAlertDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlightDealDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        routes = db.trackedRouteDao()
        alerts = db.priceAlertDao()
    }

    @After
    fun tearDown() = db.close()

    private fun route(destination: String = "TYO") = TrackedRouteEntity(
        originIata = "ICN",
        destinationIata = destination,
        departDate = "2026-10-12",
        returnDate = "2026-10-16",
        tripType = "ROUND_TRIP",
        targetPrice = 280_000,
        createdAt = 1_800_000_000L,
    )

    private fun alert(routeId: Long, previous: Int, current: Int, notifiedAt: Long, reachedTarget: Boolean = false) =
        PriceAlertEntity(
            trackedRouteId = routeId,
            previous = previous,
            current = current,
            reachedTarget = reachedTarget,
            notifiedAt = notifiedAt,
        )

    @Test
    fun `알림을 기록하고 최신순으로 돌려준다`() = runTest {
        val id = routes.insert(route())
        alerts.insert(alert(id, 300_000, 280_000, notifiedAt = 100))
        alerts.insert(alert(id, 280_000, 260_000, notifiedAt = 200))

        val recent = alerts.observeRecent(sinceEpochSecond = 0).first()

        assertEquals(listOf(260_000, 280_000), recent.map { it.current })
    }

    @Test
    fun `추적을 해제하면 그 노선의 알림 기록도 사라진다`() = runTest {
        val id = routes.insert(route())
        alerts.insert(alert(id, 300_000, 280_000, notifiedAt = 100))

        routes.deleteById(id)

        // 외래키 CASCADE에 맡긴다. 이력(price_snapshot)과 같은 방식이다.
        assertEquals(0, alerts.observeRecent(sinceEpochSecond = 0).first().size)
    }

    @Test
    fun `보관 기간이 지난 기록은 정리된다`() = runTest {
        val id = routes.insert(route())
        alerts.insert(alert(id, 300_000, 280_000, notifiedAt = 100))
        alerts.insert(alert(id, 280_000, 260_000, notifiedAt = 500))

        alerts.deleteOlderThan(epochSecond = 200)

        assertEquals(1, alerts.observeRecent(sinceEpochSecond = 0).first().size)
    }
}
