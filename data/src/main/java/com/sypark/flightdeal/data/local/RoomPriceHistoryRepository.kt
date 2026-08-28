package com.sypark.flightdeal.data.local

import android.util.Log
import com.sypark.flightdeal.data.local.entity.PriceSnapshotEntity
import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.PriceHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant

class RoomPriceHistoryRepository(
    private val dao: PriceSnapshotDao,
    private val clock: Clock,
) : PriceHistoryRepository {

    override suspend fun append(snapshot: PriceSnapshot) = dao.insert(
        PriceSnapshotEntity(
            trackedRouteId = snapshot.trackedRouteId,
            price = snapshot.price.amount,
            tripType = snapshot.tripType.name,
            capturedAt = snapshot.capturedAt.epochSecond,
        )
    )

    override suspend fun latest(trackedRouteId: Long): PriceSnapshot? =
        dao.latestFor(trackedRouteId)?.toDomain()

    override fun observeHistory(trackedRouteId: Long, days: Int): Flow<List<PriceSnapshot>> =
        dao.observeFor(trackedRouteId, cutoff(days)).map { list -> list.mapNotNull { it.toDomain() } }

    override suspend fun pruneOlderThan(days: Int) = dao.deleteOlderThan(cutoff(days))

    override fun observeCount(): Flow<Int> = dao.observeCount()

    override suspend fun clearAll() = dao.deleteAll()

    /**
     * 음수를 받으면 기준 시각이 미래가 되고, `capturedAt < 미래`는 모든 행에 참이라
     * 이력 전체가 지워진다. 조용히 0으로 보정하면 부르는 쪽의 계산 실수가 숨는다.
     */
    private fun cutoff(days: Int): Long {
        require(days >= 0) { "days는 음수일 수 없다: $days" }
        return clock.instant().epochSecond - days.toLong() * SECONDS_PER_DAY
    }

    /**
     * 읽을 수 없는 행은 버린다. `TripType.valueOf`는 모르는 이름에 예외를 던지는데,
     * 그게 `map` 안에서 터지면 행 하나 때문에 목록 전체가 죽는다 —
     * 열거형 값을 바꾸거나 앱 버전이 섞이면 실제로 일어난다.
     */
    private fun PriceSnapshotEntity.toDomain(): PriceSnapshot? = runCatching {
        PriceSnapshot(
            trackedRouteId = trackedRouteId,
            price = Won(price),
            tripType = TripType.valueOf(tripType),
            capturedAt = Instant.ofEpochSecond(capturedAt),
        )
    }.onFailure { Log.w(TAG, "읽을 수 없는 스냅샷을 건너뛴다: id=$id", it) }.getOrNull()

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
        const val TAG = "PriceHistory"
    }
}
