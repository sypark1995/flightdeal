package com.sypark.flightdeal.data.di

import com.sypark.flightdeal.data.BuildConfig
import com.sypark.flightdeal.data.remote.TravelpayoutsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://api.travelpayouts.com/"

    /**
     * 피드 한 번에 (노선 수 + 1)개의 요청이 병렬로 나간다. limit 기본값이 20이므로
     * 최대 21개다. 레이트 리밋이 걸린 API에 그대로 쏟으면 429를 맞는다.
     * 동시 실행 상한은 HTTP 사정이므로 도메인이 아니라 여기서 조인다.
     */
    private const val MAX_REQUESTS_PER_HOST = 4

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .dispatcher(
            Dispatcher().apply {
                maxRequestsPerHost = MAX_REQUESTS_PER_HOST
                maxRequests = MAX_REQUESTS_PER_HOST
            }
        )
        .addInterceptor { chain ->
            // 토큰을 매 호출마다 붙이는 것을 잊지 않도록 한 곳에 모은다.
            val url = chain.request().url.newBuilder()
                .addQueryParameter("token", BuildConfig.TRAVELPAYOUTS_TOKEN)
                .build()
            chain.proceed(chain.request().newBuilder().url(url).build())
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideTravelpayoutsApi(retrofit: Retrofit): TravelpayoutsApi =
        retrofit.create(TravelpayoutsApi::class.java)
}
