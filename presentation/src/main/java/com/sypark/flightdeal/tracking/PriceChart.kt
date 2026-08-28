package com.sypark.flightdeal.tracking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.feed.formatWon
import com.sypark.flightdeal.ui.theme.Indigo
import com.sypark.flightdeal.ui.theme.TextSecondary

/**
 * 가격 이력을 선 그래프로 그린다. 좌표 계산은 [PriceChartGeometry]가 순수하게 맡고,
 * 여기서는 그 0..1 좌표를 실제 픽셀로 옮겨 그리기만 한다.
 */
@Composable
fun PriceChart(
    snapshots: List<PriceSnapshot>,
    targetPrice: Won?,
    modifier: Modifier = Modifier,
) {
    // 점이 둘 미만이면 선을 그을 수 없다 — 하나로 선을 그으면 아무것도 안 그려지거나
    // 빈 상자만 남는다. 대신 언제 그래프가 채워질지 안내한다.
    if (snapshots.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "가격을 두 번 이상 확인하면 추이를 보여드릴게요",
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }
        return
    }

    val geometry = PriceChartGeometry.of(snapshots, targetPrice)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // 선 굵기가 위아래로 잘리지 않도록 여백을 준다. 기하 계산 자체는
                // 0..1 순수 좌표로 남겨두고, 픽셀 여백은 그리는 쪽에서만 처리한다.
                .padding(vertical = 4.dp),
        ) {
            val w = size.width
            val h = size.height

            fun toOffset(point: ChartPoint) = Offset(point.x * w, point.y * h)

            val path = Path().apply {
                geometry.points.forEachIndexed { index, point ->
                    val offset = toOffset(point)
                    if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
                }
            }
            drawPath(
                path = path,
                color = Indigo,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )

            geometry.targetY?.let { targetY ->
                drawLine(
                    color = TextSecondary,
                    start = Offset(0f, targetY * h),
                    end = Offset(w, targetY * h),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                )
            }

            val last = toOffset(geometry.points.last())
            drawCircle(color = Indigo, radius = 3.dp.toPx(), center = last)
        }

        Text(
            text = formatWon(geometry.scaleHigh),
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Text(
            text = formatWon(geometry.scaleLow),
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}
