package com.sypark.flightdeal.domain.repository

import com.sypark.flightdeal.domain.model.Airport
import kotlinx.coroutines.flow.Flow

/**
 * 사용자가 고른 앱 설정. 지금은 출발 공항 하나뿐이다.
 *
 * `:domain`은 저장 방식을 모른다 — DataStore인지 Room인지는 `:data` 구현체가 정한다.
 */
interface SettingsRepository {

    /** 고른 출발 공항. 고른 적이 없으면 [Airport.INCHEON]. */
    fun observeOrigin(): Flow<Airport>

    suspend fun setOrigin(origin: Airport)
}
