package com.sypark.flightdeal.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sypark.flightdeal.data.local.entity.PriceSnapshotEntity
import com.sypark.flightdeal.data.local.entity.TrackedRouteEntity

@Database(
    entities = [TrackedRouteEntity::class, PriceSnapshotEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FlightDealDatabase : RoomDatabase() {
    abstract fun trackedRouteDao(): TrackedRouteDao
    abstract fun priceSnapshotDao(): PriceSnapshotDao
}
