package com.sypark.flightdeal.data.remote

/**
 * Aviasales는 러시아에서 출발한 서비스라 예약처 구성이 CIS 시장에 치우쳐 있다.
 * Aviakassa, Kupi.com, Biletix, Clickavia, Авиасейлс 같은 곳은 한국 사용자가
 * 결제까지 가기 어렵다. 실측으로 노선당 절반 조금 넘게가 여기 해당했다.
 *
 * 그렇다고 완전히 걸러내면 한산한 노선은 화면이 빈다. 그래서 우선순위를 주되,
 * 모자라면 나머지로 채운다.
 */
object GatePolicy {

    /** 한국에서 실제로 예약까지 이어지는 예약처. 확인되는 대로 늘린다. */
    private val PREFERRED = setOf("Trip.com", "Kiwi.com")

    /**
     * [PREFERRED]에 해당하는 항목을 앞으로 보내고, 그것만으로 [minCount]를 채우지 못하면
     * 나머지를 원래 순서대로 뒤에 붙인다.
     */
    fun <T> prioritize(items: List<T>, gateOf: (T) -> String?, minCount: Int): List<T> {
        val (preferred, rest) = items.partition { gateOf(it) in PREFERRED }

        // minCount가 0 이하면 "충분하다"가 무조건 참이 되어, 허용 예약처가 하나도 없는
        // 노선에서 빈 목록을 돌려준다. 이 함수의 존재 이유가 바로 그걸 막는 것이다.
        if (preferred.isNotEmpty() && preferred.size >= minCount.coerceAtLeast(1)) return preferred
        return preferred + rest
    }
}
