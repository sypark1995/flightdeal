package com.sypark.flightdeal.data.remote

import com.sypark.flightdeal.data.remote.dto.PricesForDatesResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface TravelpayoutsApi {

    /**
     * 한 노선·한 달의 날짜별 최저가.
     *
     * @param departureAt `"2026-10"` 형태. 날짜까지 주면 그날만 온다.
     * @param returnAt 왕복일 때만 준다. 편도면 null.
     * @param oneWay false면 왕복. [returnAt]과 함께 줘야 한다.
     */
    @GET("aviasales/v3/prices_for_dates")
    suspend fun pricesForDates(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("departure_at") departureAt: String,
        @Query("return_at") returnAt: String? = null,
        @Query("one_way") oneWay: Boolean = true,
        @Query("currency") currency: String = "krw",
        @Query("sorting") sorting: String = "price",
        @Query("limit") limit: Int = 1000,
    ): PricesForDatesResponse
}
