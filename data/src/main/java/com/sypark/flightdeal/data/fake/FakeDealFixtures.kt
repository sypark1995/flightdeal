package com.sypark.flightdeal.data.fake

import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.Won
import java.time.Instant
import java.time.LocalDate

/**
 * 개발·테스트용 고정 데이터. 실제 API 연동 전까지 화면을 채운다.
 */
object FakeDealFixtures {

    private val DESTINATIONS = listOf(
        Airport("TYO", "도쿄", "일본") to 189_000,
        Airport("BKK", "방콕", "태국") to 241_000,
        Airport("DAD", "다낭", "베트남") to 265_000,
        Airport("TPE", "타이베이", "대만") to 278_000,
        Airport("HKG", "홍콩", "중국") to 312_000,
        Airport("SIN", "싱가포르", "싱가포르") to 398_000,
    )

    private val AIRLINES = listOf("대한항공", "아시아나항공", "티웨이항공", "제주항공")

    fun deals(): List<PriceQuote> = DESTINATIONS.mapIndexed { index, (destination, price) ->
        PriceQuote(
            route = Route(Airport.INCHEON, destination),
            departDate = LocalDate.of(2026, 10, 12).plusDays(index.toLong()),
            returnDate = LocalDate.of(2026, 10, 16).plusDays(index.toLong()),
            price = Won(price),
            airline = AIRLINES[index % AIRLINES.size],
            foundAt = Instant.parse("2026-08-28T00:00:00Z"),
            deepLink = "https://example.com/booking/${destination.iata}",
        )
    }

    /** 한 노선의 한 달치 가격. 중앙값이 특가보다 확실히 높도록 구성한다. */
    fun monthlyPrices(route: Route): List<PriceQuote> {
        val base = DESTINATIONS.firstOrNull { it.first.iata == route.destination.iata }?.second
            ?: return emptyList()
        return (1..28).map { day ->
            PriceQuote(
                route = route,
                departDate = LocalDate.of(2026, 10, day),
                returnDate = LocalDate.of(2026, 10, day).plusDays(4),
                // 특가(base)의 1.2배 ~ 1.9배 사이에서 흔들리게 만든다.
                price = Won(base * (120 + (day * 27) % 70) / 100),
                airline = AIRLINES[day % AIRLINES.size],
                foundAt = Instant.parse("2026-08-28T00:00:00Z"),
                deepLink = "https://example.com/booking/${route.destination.iata}/$day",
            )
        }
    }
}
