package com.sypark.flightdeal.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.feed.formatWon
import com.sypark.flightdeal.ui.theme.Outline
import com.sypark.flightdeal.ui.theme.PriceDown
import com.sypark.flightdeal.ui.theme.PriceUp
import com.sypark.flightdeal.ui.theme.TextPrimary
import com.sypark.flightdeal.ui.theme.TextSecondary

@Composable
fun TrackedRouteCard(
    item: TrackedItem,
    onUntrack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Outline, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${item.tracked.route.origin.cityKo} → ${item.tracked.route.destination.cityKo}",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "해제",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onUntrack).padding(4.dp),
            )
        }

        Text(
            text = buildString {
                append(item.tracked.departDate)
                item.tracked.returnDate?.let { append(" – $it") }
                append(if (item.tracked.tripType == TripType.ROUND_TRIP) " · 왕복" else " · 편도")
            },
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 3.dp),
        )

        Row(
            modifier = Modifier.padding(top = 9.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = item.latest?.let { formatWon(it.price) } ?: "가격을 모으는 중이에요",
                color = TextPrimary,
                fontSize = if (item.latest != null) 21.sp else 13.sp,
                fontWeight = FontWeight.Bold,
            )

            val latest = item.latest
            val previous = item.previous
            if (latest != null && previous != null && latest.price != previous.price) {
                val dropped = latest.price < previous.price
                Text(
                    // 색이 정보를 나른다. 하락은 항상 초록, 상승은 항상 빨강.
                    text = if (dropped) "▼ ${formatWon(previous.price)}" else "▲ ${formatWon(previous.price)}",
                    color = if (dropped) PriceDown else PriceUp,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        item.tracked.targetPrice?.let { target ->
            Text(
                text = "목표가 ${formatWon(target)}",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
