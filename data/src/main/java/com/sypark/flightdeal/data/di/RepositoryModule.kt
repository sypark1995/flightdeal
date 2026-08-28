package com.sypark.flightdeal.data.di

import com.sypark.flightdeal.data.fake.FakeFlightPriceRepository
import com.sypark.flightdeal.domain.repository.FlightPriceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 지금은 Fake만 제공한다. Travelpayouts 구현체가 생기면
 * 이 함수의 반환값만 교체한다. 다른 어떤 파일도 손대지 않는다.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideFlightPriceRepository(): FlightPriceRepository =
        FakeFlightPriceRepository()
}
