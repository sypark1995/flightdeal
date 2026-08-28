package com.sypark.flightdeal.tracking

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    var showConfirm by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("추적을 해제할까요?") },
            // 이력까지 사라진다는 걸 분명히 말한다. 되돌릴 수 없다.
            text = { Text("${item.tracked.route.destination.cityKo} 노선의 추적을 해제하면 지금까지 모은 가격 이력도 함께 지워져요.") },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; onUntrack() }) { Text("해제") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("취소") }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Outline, RoundedCornerShape(16.dp))
            // 펼침/접힘 사이에 카드 높이가 애니메이션으로 변한다.
            .animateContentSize()
            .clickable { expanded = !expanded }
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
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showConfirm = true }
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .wrapContentSize(Alignment.Center)
                    .padding(horizontal = 8.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = buildString {
                    append(item.tracked.departDate)
                    item.tracked.returnDate?.let { append(" – $it") }
                    append(if (item.tracked.tripType == TripType.ROUND_TRIP) " · 왕복" else " · 편도")
                },
                color = TextSecondary,
                fontSize = 11.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            // 펼침 여부를 알려주는 화살표. 카드 전체가 클릭 대상이라 별도 클릭 처리는 없다.
            Text(
                text = if (expanded) "▴" else "▾",
                color = TextSecondary,
                fontSize = 11.sp,
            )
        }

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

        if (expanded) {
            PriceChart(
                snapshots = item.history,
                targetPrice = item.tracked.targetPrice,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
