package com.sypark.flightdeal.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.ui.theme.FlightDealTheme
import java.time.LocalDate

/**
 * 격자 한 칸. [cell.date]가 null이면 1일 앞 또는 말일 뒤의 빈 칸이라 아무것도 그리지 않는다 —
 * 그래도 칸 크기는 맞춰야 옆 줄과 격자가 어긋나지 않는다.
 *
 * 누를 수 없는 칸(값이 없거나, 지났거나, 예약처 연결이 없는 날)은 [DealCard]가
 * "예약처 연결 없음"으로 누르기 전에 알려주는 것과 같은 이유로, 누르기 전에
 * 흐리게 표시해 조용히 먹통이 되지 않게 한다.
 */
@Composable
fun DayCell(
    cell: GridCell,
    quote: PriceQuote?,
    isCheapest: Boolean,
    isBelowMedian: Boolean,
    today: LocalDate,
    onClick: (PriceQuote) -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = cell.date
    if (date == null) {
        Box(modifier = modifier.aspectRatio(1f))
        return
    }

    val isPast = date < today
    val hasBookingLink = quote?.deepLink != null
    val isClickable = quote != null && !isPast && hasBookingLink
    // 값이 있는데도 누를 수 없는 이유(과거, 예약처 없음)가 있으면 누르기 전에 흐리게 보여준다.
    val isDimmed = quote != null && !isClickable

    val cellBackground = when {
        isCheapest && quote != null -> FlightDealTheme.colors.indigo
        isBelowMedian && quote != null -> FlightDealTheme.colors.indigoSubtle
        else -> Color.Transparent
    }
    // 흰색을 고정하지 않는다 — 다크에서 indigo는 밝은 라벤더라 흰 글씨가 잘 안 읽힌다.
    // onPrimary는 Theme.kt가 라이트/다크마다 대비를 맞춰 계산한다.
    val textColor = if (isCheapest && quote != null) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        FlightDealTheme.colors.textPrimary
    }

    Column(
        modifier = modifier
            .aspectRatio(1f)
            .let { if (isDimmed) it.alpha(0.4f) else it }
            .padding(2.dp)
            .background(cellBackground, RoundedCornerShape(8.dp))
            .let { if (isClickable) it.clickable { onClick(quote!!) } else it }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            color = if (quote == null) FlightDealTheme.colors.textSecondary else textColor,
            fontSize = 13.sp,
            fontWeight = if (isCheapest && quote != null) FontWeight.Bold else FontWeight.Normal,
        )
        if (quote != null) {
            Text(
                text = formatWonCompact(quote.price),
                color = textColor,
                fontSize = 10.sp,
            )
        }
    }
}

/**
 * 304,619원 → 30.5만. 칸이 좁아 원 단위를 다 쓰면 격자가 줄바꿈으로 깨진다.
 * 만원 단위 소수 첫째 자리까지 반올림해 항상 같은 자리수로 보여준다.
 */
private fun formatWonCompact(won: Won): String {
    val tenths = Math.round(won.amount / 1_000.0)
    return "${tenths / 10}.${tenths % 10}만"
}
