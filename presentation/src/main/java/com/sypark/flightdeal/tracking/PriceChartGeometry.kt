package com.sypark.flightdeal.tracking

import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.Won

/** 0..1로 정규화된 점. y는 0이 위(비쌈), 1이 아래(쌈) — Canvas 좌표계와 같은 방향이다. */
data class ChartPoint(val x: Float, val y: Float)

/**
 * @param scaleLow 세로축 아래 끝 가격. 축 눈금으로 그대로 표시한다.
 * @param scaleHigh 세로축 위 끝 가격.
 * @param targetY 목표가 선의 y. 목표가가 없거나 축 범위 밖이면 null이다.
 */
data class PriceChartGeometry(
    val points: List<ChartPoint>,
    val scaleLow: Won,
    val scaleHigh: Won,
    val targetY: Float?,
) {
    companion object {
        fun of(snapshots: List<PriceSnapshot>, targetPrice: Won?): PriceChartGeometry {
            if (snapshots.isEmpty()) {
                return PriceChartGeometry(
                    points = emptyList(),
                    scaleLow = Won(0),
                    scaleHigh = Won(0),
                    targetY = null,
                )
            }

            val times = snapshots.map { it.capturedAt.epochSecond }
            val tMin = times.min()
            val tMax = times.max()
            // 점이 하나뿐이거나 모든 시각이 같으면 분모가 0이다. 인덱스로 균등 배치하지 않고
            // 그냥 가운데 두 번째 규칙과 같은 이유로 — 시간 간격이 없다는 뜻이니 조작하지 않는다.
            val timeSpan = (tMax - tMin).toFloat()

            val prices = snapshots.map { it.price.amount }
            val scaleLow = Won(prices.min())
            val scaleHigh = Won(prices.max())
            // 가격이 전부 같으면 (max - min)이 0이다. 나누면 NaN이 나오고 Canvas가
            // 아무것도 그리지 않거나 튄다 — 그럴 때는 가운데 수평선으로 둔다.
            val priceSpan = (scaleHigh.amount - scaleLow.amount).toFloat()

            val points = snapshots.map { snapshot ->
                val x = if (timeSpan == 0f) {
                    0.5f
                } else {
                    (snapshot.capturedAt.epochSecond - tMin) / timeSpan
                }
                val y = if (priceSpan == 0f) {
                    0.5f
                } else {
                    (scaleHigh.amount - snapshot.price.amount) / priceSpan
                }
                ChartPoint(x, y)
            }

            // 목표가로 축을 늘리지 않는다. 데이터 범위 밖이면 선을 그리지 않는다 — 카드에
            // 이미 숫자로 목표가가 있으니 굳이 축을 왜곡해 보여줄 필요가 없다.
            val targetY = targetPrice?.let { target ->
                if (target < scaleLow || target > scaleHigh) {
                    null
                } else if (priceSpan == 0f) {
                    0.5f
                } else {
                    (scaleHigh.amount - target.amount) / priceSpan
                }
            }

            return PriceChartGeometry(points, scaleLow, scaleHigh, targetY)
        }
    }
}
