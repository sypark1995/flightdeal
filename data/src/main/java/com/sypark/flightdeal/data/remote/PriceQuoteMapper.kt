package com.sypark.flightdeal.data.remote

import com.sypark.flightdeal.data.remote.dto.PriceDto
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.Won
import java.time.Instant
import java.time.LocalDate

object PriceQuoteMapper {

    /**
     * @param foundAt 응답에 관측 시각이 없으므로 조회 시각을 넣는다.
     * @return 필수 필드가 빠졌거나 형식이 깨진 항목은 null. 한 건이 이상하다고
     *   피드 전체를 오류로 만들지 않는다.
     */
    fun toDomain(dto: PriceDto, foundAt: Instant, marker: String): PriceQuote? {
        val price = dto.price ?: return null
        val departDate = parseDate(dto.departureAt) ?: return null
        val originIata = dto.originAirport ?: dto.origin ?: return null
        val destinationIata = dto.destination ?: dto.destinationAirport ?: return null

        return PriceQuote(
            route = Route(
                origin = Airport(originIata, AirportNames.cityOf(originIata), ""),
                destination = Airport(destinationIata, AirportNames.cityOf(destinationIata), ""),
            ),
            departDate = departDate,
            returnDate = parseDate(dto.returnAt),
            price = Won(price),
            airline = AirlineNames.of(dto.airline),
            foundAt = foundAt,
            deepLink = DeepLinkBuilder.build(dto.link, marker),
            transfers = transfersOf(dto),
            outboundMinutes = dto.durationTo,
        )
    }

    /** ISO 8601 앞 10자가 날짜다. 형식이 깨지면 null. */
    private fun parseDate(raw: String?): LocalDate? {
        if (raw == null || raw.length < 10) return null
        return runCatching { LocalDate.parse(raw.substring(0, 10)) }.getOrNull()
    }

    /**
     * 가는 편과 오는 편 중 경유가 많은 쪽을 고른다. 편도(return_at 없음)면
     * return_transfers는 의미가 없으므로 보지 않는다.
     */
    private fun transfersOf(dto: PriceDto): Int? {
        val outbound = dto.transfers
        if (dto.returnAt == null) return outbound

        val inbound = dto.returnTransfers
        return when {
            outbound == null && inbound == null -> null
            outbound == null -> inbound
            inbound == null -> outbound
            else -> maxOf(outbound, inbound)
        }
    }
}
