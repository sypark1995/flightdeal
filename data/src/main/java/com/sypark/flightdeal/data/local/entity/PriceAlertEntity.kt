package com.sypark.flightdeal.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 추적을 해제하면 그 노선의 알림 기록도 함께 사라진다(CASCADE). 해제 확인 다이얼로그가
 * 이미 "지금까지 모은 가격 이력도 함께 지워져요"라고 약속하고 있어서 그 약속과
 * 어긋나지 않는다. 기록만 남기려면 노선 정보를 이 행에 복사해야 하는데, 그러면
 * 같은 사실이 두 곳(tracked_route, price_alert)에 저장된다.
 */
@Entity(
    tableName = "price_alert",
    foreignKeys = [
        ForeignKey(
            entity = TrackedRouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackedRouteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trackedRouteId"), Index("notifiedAt")],
)
data class PriceAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackedRouteId: Long,
    val previous: Int,
    val current: Int,
    val reachedTarget: Boolean,
    /** epoch second. 알림을 실제로 띄운 시각이다 — 관측 시각이 아니다. */
    val notifiedAt: Long,
)
