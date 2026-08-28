package com.sypark.flightdeal.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PricesForDatesResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: List<PriceDto>? = null,
    @SerializedName("currency") val currency: String? = null,
)

/**
 * 실제 응답 필드를 그대로 옮긴다. 31건 전부 모든 필드가 채워져 오지만,
 * API가 언제든 필드를 빠뜨릴 수 있으므로 nullable로 받고 매퍼에서 판단한다.
 *
 * [returnAt]은 편도 조회 시 아예 오지 않는다.
 * [link]는 `/search/...` 형태의 상대 경로다.
 * [airline]은 `"ZE"` 같은 IATA 코드이지 사람이 읽을 이름이 아니다.
 */
data class PriceDto(
    @SerializedName("origin") val origin: String? = null,
    @SerializedName("destination") val destination: String? = null,
    @SerializedName("origin_airport") val originAirport: String? = null,
    @SerializedName("destination_airport") val destinationAirport: String? = null,
    @SerializedName("departure_at") val departureAt: String? = null,
    @SerializedName("return_at") val returnAt: String? = null,
    @SerializedName("price") val price: Int? = null,
    @SerializedName("airline") val airline: String? = null,
    @SerializedName("flight_number") val flightNumber: String? = null,
    @SerializedName("gate") val gate: String? = null,
    @SerializedName("transfers") val transfers: Int? = null,
    @SerializedName("return_transfers") val returnTransfers: Int? = null,
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("link") val link: String? = null,
)
