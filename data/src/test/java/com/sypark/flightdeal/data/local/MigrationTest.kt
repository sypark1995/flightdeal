package com.sypark.flightdeal.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 이 저장소는 스키마 JSON을 내보내면서도(`schemas/`), 실제로 v1 스키마를 열어
 * `MIGRATION_1_2`를 태워보는 테스트가 없었다 — 다른 DB 테스트는 전부
 * `Room.inMemoryDatabaseBuilder`로 현재 버전에서 바로 시작해서 마이그레이션 코드를
 * 지나치지 않는다. 이 테스트는 v1 DB를 만들고 실데이터를 넣은 뒤 마이그레이션을
 * 태워, 행이 살아남는지와 `notifiedPrice` 백필이 실제로 동작하는지를 고정한다.
 *
 * 백필이 가장 중요하다 — 이게 없으면 v1에서 넘어온 행은 기준선이 비어 있어
 * 영영 변동을 판정하지 못한다. (`기준선이 비어 영영 알림이 오지 않던 상태 제거`
 * 커밋이 고친 것과 같은 종류의 문제를, 이번엔 마이그레이션 경로에서 고정한다.)
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FlightDealDatabase::class.java,
    )

    @Test
    fun `MIGRATION_1_2는 기존 행을 보존하고 notifiedPrice를 마지막 관측값으로 채운다`() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL(
                "INSERT INTO tracked_route " +
                    "(id, originIata, destinationIata, departDate, returnDate, tripType, targetPrice, createdAt) " +
                    "VALUES (1, 'ICN', 'TYO', '2026-10-12', '2026-10-16', 'ROUND_TRIP', 280000, 1800000000)"
            )
            // capturedAt이 더 큰(200) 쪽이 "마지막 관측값"이다. 순서를 뒤섞어 넣어서
            // 백필이 삽입 순서가 아니라 시각으로 고른다는 것을 검증한다.
            execSQL(
                "INSERT INTO price_snapshot (id, trackedRouteId, price, tripType, capturedAt) " +
                    "VALUES (1, 1, 300000, 'ROUND_TRIP', 100)"
            )
            execSQL(
                "INSERT INTO price_snapshot (id, trackedRouteId, price, tripType, capturedAt) " +
                    "VALUES (2, 1, 260000, 'ROUND_TRIP', 200)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2)

        migrated.query("SELECT originIata, destinationIata, targetPrice, notifiedPrice FROM tracked_route WHERE id = 1")
            .use { cursor ->
                assertTrue("마이그레이션 뒤에도 행이 남아 있어야 한다", cursor.moveToFirst())
                assertEquals("ICN", cursor.getString(0))
                assertEquals("TYO", cursor.getString(1))
                assertEquals(280_000, cursor.getInt(2))
                // 가장 최근(capturedAt=200) 스냅샷 가격인 260000으로 백필돼야 한다.
                assertEquals(260_000, cursor.getInt(3))
            }

        migrated.query("SELECT COUNT(*) FROM price_snapshot WHERE trackedRouteId = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }

        migrated.close()
    }

    /**
     * `price_alert` 테이블은 v2에 없던 새 테이블이다. 이 마이그레이션이
     * `fallbackToDestructiveMigration()`으로 잘못 바뀌면 `tracked_route`와
     * `price_snapshot`에 며칠에 걸쳐 쌓인 행이 통째로 사라진다 — 그건 다시
     * 만들어낼 수 없다. v2로 열어 두 테이블에 행을 넣고 마이그레이션한 뒤
     * 둘 다 남아 있고 `price_alert`는 비어 있는(새로 만들어졌을 뿐인) 것을 확인한다.
     */
    @Test
    fun `MIGRATION_2_3은 기존 데이터를 보존한다`() {
        helper.createDatabase(DB_NAME, 2).apply {
            execSQL(
                "INSERT INTO tracked_route " +
                    "(id, originIata, destinationIata, departDate, returnDate, tripType, " +
                    "targetPrice, notifiedPrice, createdAt) " +
                    "VALUES (1, 'ICN', 'TYO', '2026-10-12', '2026-10-16', 'ROUND_TRIP', " +
                    "280000, 300000, 1800000000)"
            )
            execSQL(
                "INSERT INTO price_snapshot (id, trackedRouteId, price, tripType, capturedAt) " +
                    "VALUES (1, 1, 300000, 'ROUND_TRIP', 100)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_2_3)

        migrated.query("SELECT COUNT(*) FROM tracked_route WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("기존 추적 항목이 남아 있어야 한다", 1, cursor.getInt(0))
        }

        migrated.query("SELECT COUNT(*) FROM price_snapshot WHERE trackedRouteId = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("기존 가격 이력이 남아 있어야 한다", 1, cursor.getInt(0))
        }

        migrated.query("SELECT COUNT(*) FROM price_alert").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("price_alert는 새로 만들어질 뿐 비어 있어야 한다", 0, cursor.getInt(0))
        }

        migrated.close()
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
