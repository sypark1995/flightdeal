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
    }
}
