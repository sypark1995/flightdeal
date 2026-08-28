package com.sypark.flightdeal.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 도메인 타입을 쓰지 않는다. 스키마가 도메인 모델의 변경에 끌려다니면
 * 모델을 손볼 때마다 마이그레이션을 써야 한다.
 *
 * @param departDate ISO-8601 `"2026-10-12"`
 * @param tripType [com.sypark.flightdeal.domain.model.TripType]의 `name`
 * @param createdAt epoch second
 */
@Entity(tableName = "tracked_route")
data class TrackedRouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originIata: String,
    val destinationIata: String,
    val departDate: String,
    val returnDate: String?,
    val tripType: String,
    val targetPrice: Int?,
    val createdAt: Long,
)
