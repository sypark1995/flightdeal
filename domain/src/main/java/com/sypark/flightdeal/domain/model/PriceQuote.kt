package com.sypark.flightdeal.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * 특정 노선·날짜의 가격 하나.
 *
 * @param foundAt 이 가격이 관측된 시각. 소스가 캐시 기반이라 조회 시각과 다를 수 있다.
 * @param deepLink 예약처로 연결할 URL. 없을 수 있다.
 */
data class PriceQuote(
    val route: Route,
    val departDate: LocalDate,
    val returnDate: LocalDate?,
    val price: Won,
    val airline: String?,
    val foundAt: Instant,
    val deepLink: String?,
    /**
     * 가는 편과 오는 편 중 **경유가 많은 쪽**의 횟수. 편도면 가는 편만 본다.
     *
     * 편마다 따로 보여주면 카드가 길어지고, 사용자가 카드에서 내리는 결정은
     * "이 딜을 열어볼까"뿐이다. 한쪽이라도 경유가 있으면 알아야 한다.
     * 정확한 편별 내역은 예약 페이지에 있다.
     */
    val transfers: Int?,
    /** 가는 편 비행 시간(분). 왕복의 `duration`은 두 편의 합이라 쓰지 않는다. */
    val outboundMinutes: Int?,
)
