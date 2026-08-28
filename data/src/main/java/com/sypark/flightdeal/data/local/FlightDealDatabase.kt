package com.sypark.flightdeal.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sypark.flightdeal.data.local.entity.PriceSnapshotEntity
import com.sypark.flightdeal.data.local.entity.TrackedRouteEntity

/**
 * `exportSchema = true`로 둔다. 스키마 JSON이 있어야 나중에 컬럼을 바꿀 때
 * 마이그레이션을 테스트할 수 있다. 없으면 두 갈래뿐이다 — 마이그레이션을 안 쓰고
 * 앱이 열리자마자 크래시하거나, `fallbackToDestructiveMigration()`으로
 * 사용자가 등록한 추적 노선과 이력을 통째로 조용히 지우거나.
 */
@Database(
    entities = [TrackedRouteEntity::class, PriceSnapshotEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class FlightDealDatabase : RoomDatabase() {
    abstract fun trackedRouteDao(): TrackedRouteDao
    abstract fun priceSnapshotDao(): PriceSnapshotDao
}
