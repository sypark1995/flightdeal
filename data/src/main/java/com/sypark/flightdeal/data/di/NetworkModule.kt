package com.sypark.flightdeal.data.di

import android.util.Log
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
    private const val TAG = "Travelpayouts"

    /**
     * 피드 한 번에 목적지 수만큼의 요청이 병렬로 나간다. 레이트 리밋이 걸린 API에
     * 그대로 쏟으면 429를 맞는다. 동시 실행 상한은 HTTP 사정이므로 도메인이 아니라 여기서 조인다.
     * (여기서 말하는 상한은 동시 연결 수이지, API의 `limit` 쿼리 파라미터와는 무관하다.)
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
        .addInterceptor { chain ->
            val request = chain.request()
            val startedAt = System.nanoTime()
            val response = chain.proceed(request)
            if (BuildConfig.DEBUG) {
                // HttpLoggingInterceptor는 BASIC 레벨에서도 쿼리를 통째로 찍는다.
                // 토큰이 쿼리에 실려 있으므로 직접 지운 URL만 남긴다.
                val safeUrl = request.url.newBuilder().removeAllQueryParameters("token").build()
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                Log.d(TAG, "${request.method} $safeUrl → ${response.code} (${elapsedMs}ms)")
            }
            response
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
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
