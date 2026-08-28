package com.sypark.flightdeal.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 추적 항목이 사라지면 그 이력도 함께 사라진다. 앱 코드가 지우는 것을 잊어도
 * DB가 보장하도록 외래키에 맡긴다.
 */
@Entity(
    tableName = "price_snapshot",
    foreignKeys = [
        ForeignKey(
            entity = TrackedRouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackedRouteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trackedRouteId")],
)
data class PriceSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackedRouteId: Long,
    val price: Int,
    val tripType: String,
    val capturedAt: Long,
)
