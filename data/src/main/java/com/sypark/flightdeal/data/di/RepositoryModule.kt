package com.sypark.flightdeal.data.di

import com.sypark.flightdeal.data.BuildConfig
import com.sypark.flightdeal.data.remote.TravelpayoutsApi
import com.sypark.flightdeal.data.remote.TravelpayoutsFlightPriceRepository
import com.sypark.flightdeal.domain.repository.FlightPriceRepository
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
}
