package com.sypark.flightdeal.data.di

import com.sypark.flightdeal.data.BuildConfig
import com.sypark.flightdeal.data.local.PriceSnapshotDao
import com.sypark.flightdeal.data.local.RoomPriceHistoryRepository
import com.sypark.flightdeal.data.local.RoomTrackedRouteRepository
import com.sypark.flightdeal.data.local.TrackedRouteDao
import com.sypark.flightdeal.data.remote.TravelpayoutsApi
import com.sypark.flightdeal.data.remote.TravelpayoutsFlightPriceRepository
import com.sypark.flightdeal.domain.repository.FlightPriceRepository
import com.sypark.flightdeal.domain.repository.PriceHistoryRepository
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Fake는 지우지 않는다. 테스트와 오프라인 개발에 계속 쓴다.
 * 여기 반환값만 바꾸면 앱 전체가 Fake와 실데이터 사이를 오간다.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideFlightPriceRepository(api: TravelpayoutsApi): FlightPriceRepository =
        TravelpayoutsFlightPriceRepository(
            api = api,
            marker = BuildConfig.TRAVELPAYOUTS_MARKER,
            clock = Clock.systemDefaultZone(),
        )

    /**
     * `Clock`을 주입 가능하게 둔다. Task 7의 `CheckTrackedPricesUseCase`도 이걸 받는다 —
     * 바인딩이 없으면 Hilt가 그 UseCase를 만들지 못해 컴파일이 깨진다.
     * 테스트는 `Clock.fixed`를 직접 넘긴다.
     */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideTrackedRouteRepository(dao: TrackedRouteDao, clock: Clock): TrackedRouteRepository =
        RoomTrackedRouteRepository(dao, clock)

    @Provides
    @Singleton
    fun providePriceHistoryRepository(dao: PriceSnapshotDao, clock: Clock): PriceHistoryRepository =
        RoomPriceHistoryRepository(dao, clock)
}
