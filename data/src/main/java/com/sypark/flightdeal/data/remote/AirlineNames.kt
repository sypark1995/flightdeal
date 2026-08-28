package com.sypark.flightdeal.data.remote

/**
 * IATA 항공사 코드를 한국어 이름으로. 인천 출발 노선에 실제로 나타나는 것 위주다.
 * 표에 없으면 코드를 그대로 돌려준다 — 빈칸보다는 낫다.
 */
object AirlineNames {

    // 코드는 실제 응답 픽스처에 나타난 것과 IATA 공식 코드로 확인했다.
    // 에어프레미아는 YP다. RF는 청주 기반의 다른 항공사인 에어로케이이므로
    // 둘을 헷갈리면 사용자에게 엉뚱한 항공사 이름이 뜬다 —
    // 코드가 그대로 보이는 것보다 나쁘다.
    private val NAMES = mapOf(
        // 국적사
        "KE" to "대한항공", "OZ" to "아시아나항공", "TW" to "티웨이항공",
        "LJ" to "진에어", "7C" to "제주항공", "ZE" to "이스타항공",
        "BX" to "에어부산", "RS" to "에어서울", "YP" to "에어프레미아",
        "RF" to "에어로케이",
        // 일본
        "JL" to "일본항공", "NH" to "전일본공수", "MM" to "피치항공",
        "ZG" to "집에어",
        // 동남아
        "TG" to "타이항공", "VJ" to "비엣젯항공", "VZ" to "타이비엣젯항공",
        "VN" to "베트남항공", "FD" to "타이에어아시아", "WE" to "타이스마일", // 2024년 1월 타이항공에 인수됨, 레거시 데이터에만 존재
        "SQ" to "싱가포르항공", "TR" to "스쿠트", "PR" to "필리핀항공",
        "MH" to "말레이시아항공", "GA" to "가루다인도네시아", "AK" to "에어아시아",
        // 중화권
        "CI" to "중화항공", "BR" to "에바항공", "IT" to "타이거에어 타이완", "CX" to "캐세이퍼시픽",
        "HX" to "홍콩항공", "UO" to "홍콩익스프레스",
        "CZ" to "중국남방항공", "MU" to "중국동방항공", "CA" to "중국국제항공", "SC" to "산둥항공",
    )

    fun of(iata: String?): String? = iata?.let { NAMES[it] ?: it }
}
