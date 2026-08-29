package com.sypark.flightdeal.domain.repository

import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TrackRegistration
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface TrackedRouteRepository {

    fun observeAll(): Flow<List<TrackedRoute>>

    /** 워커용. 한 번만 읽는다. */
    suspend fun getAll(): List<TrackedRoute>

    /**
     * @return 등록 결과. 이미 추적 중이면 새로 만들지 않고 기존 id를 `isNew = false`로
     *   돌려준다 — 호출한 쪽이 "추적을 시작했어요"와 "이미 추적 중이에요"를 구분해
     *   안내할 수 있어야 한다.
     *
     * `notifiedPrice`에 기본값을 두지 않는다. 잊고 호출하면 통보 기준선이 NULL인
     * 채로 저장되고, 그 행은 어떤 가격 변동도 영영 판정하지 못하는 죽은 행이 된다.
     */
    suspend fun add(
        route: Route,
        departDate: LocalDate,
        returnDate: LocalDate?,
        tripType: TripType,
        targetPrice: Won?,
        notifiedPrice: Won?,
    ): TrackRegistration

    suspend fun remove(id: Long)

    /** 알림이 실제로 전달된 뒤에만 부른다. */
    suspend fun markNotified(id: Long, price: Won)

    /**
     * 목표가를 정하거나(null이면) 해제한다.
     *
     * `notifiedPrice`(통보 기준선)는 건드리지 않는다. 기준선을 함께 초기화하면
     * 다음 폴링에서 그동안의 가격 변동이 전부 새 변동으로 잡힌다.
     */
    suspend fun setTargetPrice(id: Long, target: Won?)
}
