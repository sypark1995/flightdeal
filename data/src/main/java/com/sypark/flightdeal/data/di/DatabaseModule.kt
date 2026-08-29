package com.sypark.flightdeal.data.di

import android.content.Context
import androidx.room.Room
import com.sypark.flightdeal.data.local.FlightDealDatabase
import com.sypark.flightdeal.data.local.MIGRATION_1_2
import com.sypark.flightdeal.data.local.MIGRATION_2_3
import com.sypark.flightdeal.data.local.PriceAlertDao
import com.sypark.flightdeal.data.local.PriceSnapshotDao
import com.sypark.flightdeal.data.local.TrackedRouteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FlightDealDatabase =
        Room.databaseBuilder(context, FlightDealDatabase::class.java, "flightdeal.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    // Room은 외래키 강제를 기본으로 켠다. 따로 설정할 필요가 없다.

    @Provides
    fun provideTrackedRouteDao(db: FlightDealDatabase): TrackedRouteDao = db.trackedRouteDao()

    @Provides
    fun providePriceSnapshotDao(db: FlightDealDatabase): PriceSnapshotDao = db.priceSnapshotDao()

    @Provides
    fun providePriceAlertDao(db: FlightDealDatabase): PriceAlertDao = db.priceAlertDao()
}
