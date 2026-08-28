package com.sypark.flightdeal.feed

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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sypark.flightdeal.domain.model.DealItem
import com.sypark.flightdeal.ui.theme.Indigo
import com.sypark.flightdeal.ui.theme.IndigoSubtle
import com.sypark.flightdeal.ui.theme.Outline
import com.sypark.flightdeal.ui.theme.TextPrimary
import com.sypark.flightdeal.ui.theme.TextSecondary

@Composable
fun DealCard(
    item: DealItem,
    onClick: () -> Unit,
    onTrack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 예약 링크가 없는 견적이 있다(API가 link를 안 주는 경우). 그런 카드를 누를 수 있게
    // 두면 아무 반응이 없어서 사용자는 앱이 고장난 줄 안다 — 누를 수 없게 막고,
    // 막혀 있다는 걸 누르기 전에 보여준다.
    val hasBookingLink = item.quote.deepLink != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Outline, RoundedCornerShape(16.dp))
            .let { if (hasBookingLink) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Text(
            text = item.quote.route.destination.cityKo,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )

        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            item.discountPercent?.let { percent ->
                Text(
                    text = "평균가 −$percent%",
                    color = Indigo,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(IndigoSubtle, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
            item.quote.airline?.let { airline ->
                Text(text = airline, color = TextSecondary, fontSize = 11.sp)
            }
            itineraryLabel(item.quote.transfers, item.quote.outboundMinutes)?.let { label ->
                Text(
                    text = label,
                    // 경유는 오류가 아니라 정보지만, 직항보다는 눈에 띄어야 예약 페이지에서
                    // 놀라지 않는다. PriceUp(빨강)은 가격 상승 전용이라 쓰지 않는다.
                    color = if ((item.quote.transfers ?: 0) > 0) TextPrimary else TextSecondary,
                    fontSize = 11.sp,
                )
            }
            if (!hasBookingLink) {
                Text(text = "예약처 연결 없음", color = TextSecondary, fontSize = 11.sp)
            }
        }

        Row(
            modifier = Modifier
                .padding(top = 9.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = formatWon(item.quote.price),
                color = TextPrimary,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            )
            item.originalPrice?.let { original ->
                Text(
                    text = formatWon(original),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textDecoration = TextDecoration.LineThrough,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "추적",
                color = Indigo,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    // 같은 카드 안의 할인 배지와 같은 색이라 테두리로 눌리는 것임을 알린다.
                    .border(1.dp, Indigo, RoundedCornerShape(20.dp))
                    .clickable(onClick = onTrack)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
