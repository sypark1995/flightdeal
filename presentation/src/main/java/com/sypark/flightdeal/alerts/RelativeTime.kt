package com.sypark.flightdeal.alerts

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * 예: "방금", "3시간 전", "어제", "10월 2일".
 *
 * 안드로이드 import가 하나도 없다 — :presentation의 JVM 유닛 테스트가 Robolectric
 * 없이 이 함수를 직접 부를 수 있어야 하기 때문이다.
 *
 * **"어제"는 경과 시간이 아니라 날짜로 판정한다.** 23시간 전이어도 자정을 넘겼으면
 * 어제다. `Instant.atZone(zone).toLocalDate()`로 비교한다 — `LocalDate.ofInstant`는
 * API 34부터라서, 이 앱이 이미 워커에서 겪은 것과 같은 종류의 크래시(안드로이드 13
 * 이하 전 기기에서 NoSuchMethodError)를 화면에서도 반복하게 된다.
 */
fun relativeTimeKo(then: Instant, now: Instant, zone: ZoneId): String {
    val elapsedSeconds = Duration.between(then, now).seconds

    if (elapsedSeconds < 60) return "방금"
    if (elapsedSeconds < 3600) return "${elapsedSeconds / 60}분 전"

    val thenDate = then.atZone(zone).toLocalDate()
    val nowDate = now.atZone(zone).toLocalDate()

    // 같은 날짜 안이면 경과 시간(최대 24시간 미만)을 그대로 적는다.
    if (thenDate == nowDate) return "${elapsedSeconds / 3600}시간 전"
    if (thenDate == nowDate.minusDays(1)) return "어제"

    val zoned = then.atZone(zone)
    return "${zoned.monthValue}월 ${zoned.dayOfMonth}일"
}
