package com.sypark.flightdeal.data.local

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
        dao.observeFor(trackedRouteId, cutoff(days)).map { list -> list.map { it.toDomain() } }

    override suspend fun pruneOlderThan(days: Int) = dao.deleteOlderThan(cutoff(days))

    private fun cutoff(days: Int): Long =
        clock.instant().epochSecond - days.toLong() * SECONDS_PER_DAY

    private fun PriceSnapshotEntity.toDomain() = PriceSnapshot(
        trackedRouteId = trackedRouteId,
        price = Won(price),
        tripType = TripType.valueOf(tripType),
        capturedAt = Instant.ofEpochSecond(capturedAt),
    )

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
    }
}
