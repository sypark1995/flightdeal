package com.sypark.flightdeal.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * `fallbackToDestructiveMigration()`을 쓰지 않는다. 이 앱이 지키는 데이터는
 * 며칠에 걸쳐 모은 가격 이력이고, 그건 다시 만들어낼 수 없다.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tracked_route ADD COLUMN notifiedPrice INTEGER")
        // 기준선을 비워두면 그 행은 영영 알림을 못 준다 — 비교 대상이 없어 변동이
        // 판정되지 않고, 판정이 없으니 기준선을 채울 기회도 오지 않는다.
        // 이미 모아둔 마지막 관측값으로 채운다.
        db.execSQL(
            "UPDATE tracked_route SET notifiedPrice = (" +
                "SELECT price FROM price_snapshot WHERE trackedRouteId = tracked_route.id " +
                "ORDER BY capturedAt DESC, id DESC LIMIT 1)"
        )
    }
}
