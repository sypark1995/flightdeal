package com.sypark.flightdeal.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sypark.flightdeal.domain.model.Direction
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.feed.formatWon
import com.sypark.flightdeal.ui.theme.FlightDealTheme

@Composable
fun AlertHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlertHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().background(FlightDealTheme.colors.background)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "←",
                color = FlightDealTheme.colors.textPrimary,
                fontSize = 18.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .wrapContentSize(Alignment.Center),
            )
            Text(
                text = "알림 기록",
                color = FlightDealTheme.colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }

        when (val current = state) {
            AlertHistoryUiState.Loading -> Box(Modifier.fillMaxSize())

            AlertHistoryUiState.Empty -> Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "아직 알림 기록이 없어요",
                        color = FlightDealTheme.colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "가격이 바뀌면 여기에 남겨드릴게요.",
                        color = FlightDealTheme.colors.textSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            is AlertHistoryUiState.Success -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items = current.items, key = { it.alert.id }) { item ->
                    AlertHistoryRow(item)
                }
            }
        }
    }
}

@Composable
private fun AlertHistoryRow(item: AlertHistoryItem, modifier: Modifier = Modifier) {
    val alert = item.alert
    val dropped = alert.direction == Direction.DOWN

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, FlightDealTheme.colors.outline, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Text(
            text = "${item.route.origin.cityKo} → ${item.route.destination.cityKo}",
            color = FlightDealTheme.colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = buildString {
                append(item.departDate)
                item.returnDate?.let { append(" – $it") }
                append(if (item.tripType == TripType.ROUND_TRIP) " · 왕복" else " · 편도")
            },
            color = FlightDealTheme.colors.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 3.dp),
        )

        Row(
            modifier = Modifier.padding(top = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                // 색이 정보를 나른다. 하락은 항상 초록, 상승은 항상 빨강 —
                // TrackedRouteCard와 같은 규칙이다.
                text = buildString {
                    append(if (dropped) "▼ " else "▲ ")
                    append(formatWon(alert.previous))
                    append(" → ")
                    append(formatWon(alert.current))
                    if (alert.reachedTarget) append(" · 목표가 도달")
                },
                color = if (dropped) FlightDealTheme.colors.priceDown else FlightDealTheme.colors.priceUp,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = item.relativeTime,
                color = FlightDealTheme.colors.textSecondary,
                fontSize = 11.sp,
            )
        }
    }
}
