package com.sypark.flightdeal.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sypark.flightdeal.data.local.entity.PriceSnapshotEntity
import com.sypark.flightdeal.data.local.entity.TrackedRouteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DaoTest {

    private lateinit var db: FlightDealDatabase
    private lateinit var routes: TrackedRouteDao
    private lateinit var snapshots: PriceSnapshotDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlightDealDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        routes = db.trackedRouteDao()
        snapshots = db.priceSnapshotDao()
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

    private fun snapshot(routeId: Long, price: Int, at: Long) = PriceSnapshotEntity(
        trackedRouteId = routeId,
        price = price,
        tripType = "ROUND_TRIP",
        capturedAt = at,
    )

    @Test
    fun `추적 노선을 넣고 관찰한다`() = runTest {
        routes.insert(route())

        assertEquals(1, routes.observeAll().first().size)
    }

    @Test
    fun `가장 최근 스냅샷을 돌려준다`() = runTest {
        val id = routes.insert(route())
        snapshots.insert(snapshot(id, 300_000, at = 100))
        snapshots.insert(snapshot(id, 280_000, at = 200))

        assertEquals(280_000, snapshots.latestFor(id)!!.price)
    }

    @Test
    fun `이력이 없으면 최근 스냅샷은 null이다`() = runTest {
        val id = routes.insert(route())

        assertNull(snapshots.latestFor(id))
    }

    @Test
    fun `기간 이후의 이력만 오래된 순으로 돌려준다`() = runTest {
        val id = routes.insert(route())
        snapshots.insert(snapshot(id, 300_000, at = 100))
        snapshots.insert(snapshot(id, 290_000, at = 300))
        snapshots.insert(snapshot(id, 280_000, at = 200))

        val history = snapshots.observeFor(id, sinceEpochSecond = 150).first()

        // 그래프는 시간순이어야 한다.
        assertEquals(listOf(280_000, 290_000), history.map { it.price })
    }

    @Test
    fun `오래된 이력을 지운다`() = runTest {
        val id = routes.insert(route())
        snapshots.insert(snapshot(id, 300_000, at = 100))
        snapshots.insert(snapshot(id, 280_000, at = 500))

        snapshots.deleteOlderThan(epochSecond = 200)

        assertEquals(1, snapshots.observeFor(id, sinceEpochSecond = 0).first().size)
    }

    @Test
    fun `추적을 해제하면 이력도 함께 사라진다`() = runTest {
        val id = routes.insert(route())
        snapshots.insert(snapshot(id, 300_000, at = 100))

        routes.deleteById(id)

        // 외래키 CASCADE에 맡긴다. 앱 코드가 지우는 것을 잊어도 남지 않아야 한다.
        assertEquals(0, snapshots.observeFor(id, sinceEpochSecond = 0).first().size)
    }

    @Test
    fun `다른 노선의 이력은 섞이지 않는다`() = runTest {
        val tokyo = routes.insert(route("TYO"))
        val bangkok = routes.insert(route("BKK"))
        snapshots.insert(snapshot(tokyo, 300_000, at = 100))
        snapshots.insert(snapshot(bangkok, 200_000, at = 100))

        assertEquals(1, snapshots.observeFor(tokyo, sinceEpochSecond = 0).first().size)
    }
}
