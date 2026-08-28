package com.sypark.flightdeal.feed

/**
 * 예: "직항 · 2시간 30분", "경유 1회 · 8시간 10분". 값을 모르면 null.
 *
 * 둘 다 모르면 아무것도 그리지 않는다 — 모르는 것을 "직항"이라고 말하면 안 된다.
 */
fun itineraryLabel(transfers: Int?, outboundMinutes: Int?): String? {
    val transferPart = transfers?.let { if (it == 0) "직항" else "경유 ${it}회" }
    val durationPart = outboundMinutes?.let(::formatMinutes)

    return when {
        transferPart != null && durationPart != null -> "$transferPart · $durationPart"
        transferPart != null -> transferPart
        durationPart != null -> durationPart
        else -> null
    }
}

/** "2시간 30분" 꼴로. 60분 미만이면 "50분", 정각이면 "3시간". */
private fun formatMinutes(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours == 0 -> "${mins}분"
        mins == 0 -> "${hours}시간"
        else -> "${hours}시간 ${mins}분"
    }
}
