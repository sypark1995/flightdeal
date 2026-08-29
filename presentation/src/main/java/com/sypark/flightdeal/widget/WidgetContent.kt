package com.sypark.flightdeal.widget

import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.tracking.TrackedItem

/**
 * 위젯 한 줄.
 *
 * 안드로이드도 Glance도 모른다. 그 순간 JVM 테스트로 못 부르게 된다 — 위젯은
 * 기기에 올려야만 눈으로 확인할 수 있는데, 무엇을 보여줄지 고르는 규칙까지
 * 기기에서만 검증하면 회귀를 놓친다.
 */
data class WidgetRow(
    val label: String,          // "인천 → 도쿄"
    val price: Won?,            // 아직 관측이 없으면 null
    val previous: Won?,         // 마지막으로 값이 달랐던 관측. 없으면 화살표를 안 그린다
    val hasDeparted: Boolean,
)

/**
 * 위젯은 좁다. 다 보여주려 하면 아무것도 안 읽힌다.
 *
 * - 출발일이 지난 여정은 뒤로 민다. 값이 갱신되지 않으므로 먼저 볼 이유가 없다
 * - 그 안에서는 등록이 최근인 것부터 — 방금 추적을 걸기 시작한 노선이 궁금한 노선이다
 * - [limit]개까지만
 *
 * [WidgetRow.previous]는 [item]이 이미 들고 있는 [TrackedItem.previous]를 그대로 옮긴다.
 * 여기서 다시 계산하지 않는다 — 추적 화면과 위젯이 같은 노선에 대해 다른 화살표를
 * 보여주는 사고를 막으려면 "값이 다른 마지막 관측을 고르는 규칙"이 코드베이스에
 * 한 곳([com.sypark.flightdeal.tracking.previousDifferingSnapshot])에만 있어야 한다.
 */
fun widgetRows(items: List<TrackedItem>, limit: Int): List<WidgetRow> =
    items
        .sortedWith(compareBy<TrackedItem> { it.hasDeparted }.thenByDescending { it.tracked.createdAt })
        .take(limit)
        .map { item ->
            WidgetRow(
                label = "${item.tracked.route.origin.cityKo} → ${item.tracked.route.destination.cityKo}",
                price = item.latest?.price,
                previous = item.previous?.price,
                hasDeparted = item.hasDeparted,
            )
        }
