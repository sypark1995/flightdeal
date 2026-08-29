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

/**
 * 알림 기록(`price_alert`) 테이블을 새로 만들 뿐, 기존 테이블은 건드리지 않는다.
 * `tracked_route`와 `price_snapshot`에 며칠에 걸쳐 쌓인 데이터가 있는 기기가 실제로
 * 있으므로 `fallbackToDestructiveMigration()`을 쓰지 않는다.
 *
 * SQL은 `data/schemas/.../3.json`이 내보낸 것과 한 글자도 다르지 않게 맞춘다 —
 * `MigrationTestHelper.runMigrationsAndValidate`가 마이그레이션 뒤 스키마를 그
 * JSON과 비교하기 때문에, 다르면 마이그레이션은 성공해도 검증에서 실패한다.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `price_alert` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`trackedRouteId` INTEGER NOT NULL, " +
                "`previous` INTEGER NOT NULL, " +
                "`current` INTEGER NOT NULL, " +
                "`reachedTarget` INTEGER NOT NULL, " +
                "`notifiedAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`trackedRouteId`) REFERENCES `tracked_route`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_price_alert_trackedRouteId` " +
                "ON `price_alert` (`trackedRouteId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_price_alert_notifiedAt` " +
                "ON `price_alert` (`notifiedAt`)"
        )
    }
}
