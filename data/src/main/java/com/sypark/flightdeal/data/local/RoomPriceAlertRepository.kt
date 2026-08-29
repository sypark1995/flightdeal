package com.sypark.flightdeal.data.local

import com.sypark.flightdeal.data.local.entity.PriceAlertEntity
import com.sypark.flightdeal.domain.model.PriceAlert
import com.sypark.flightdeal.domain.model.PriceChange
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.PriceAlertRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant

class RoomPriceAlertRepository(
    private val dao: PriceAlertDao,
    private val clock: Clock,
) : PriceAlertRepository {

    override suspend fun record(changes: List<PriceChange>, at: Instant) {
        changes.forEach { change ->
            dao.insert(
                PriceAlertEntity(
                    trackedRouteId = change.trackedRouteId,
                    previous = change.previous.amount,
                    current = change.current.amount,
                    reachedTarget = change.reachedTarget,
                    notifiedAt = at.epochSecond,
                )
            )
        }
    }

    override fun observeRecent(days: Int): Flow<List<PriceAlert>> =
        dao.observeRecent(cutoff(days)).map { list -> list.map { it.toDomain() } }

    override suspend fun pruneOlderThan(days: Int) = dao.deleteOlderThan(cutoff(days))

    /**
     * 음수를 받으면 기준 시각이 미래가 되고, `notifiedAt < 미래`는 모든 행에 참이라
     * 기록 전체가 지워진다. 조용히 0으로 보정하면 부르는 쪽의 계산 실수가 숨는다.
     */
    private fun cutoff(days: Int): Long {
        require(days >= 0) { "days는 음수일 수 없다: $days" }
        return clock.instant().epochSecond - days.toLong() * SECONDS_PER_DAY
    }

    private fun PriceAlertEntity.toDomain(): PriceAlert = PriceAlert(
        id = id,
        trackedRouteId = trackedRouteId,
        previous = Won(previous),
        current = Won(current),
        reachedTarget = reachedTarget,
        notifiedAt = Instant.ofEpochSecond(notifiedAt),
    )

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
    }
}
