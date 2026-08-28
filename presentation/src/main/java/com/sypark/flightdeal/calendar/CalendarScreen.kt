package com.sypark.flightdeal.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sypark.flightdeal.booking.BookingLauncher
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.ui.theme.Background
import com.sypark.flightdeal.ui.theme.Indigo
import com.sypark.flightdeal.ui.theme.Outline
import com.sypark.flightdeal.ui.theme.Surface
import com.sypark.flightdeal.ui.theme.TextPrimary
import com.sypark.flightdeal.ui.theme.TextSecondary
import java.time.LocalDate

private val WEEKDAY_HEADERS = listOf("월", "화", "수", "목", "금", "토", "일")

@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    val tripType by viewModel.tripType.collectAsStateWithLifecycle()
    val canGoBack by viewModel.canGoToPreviousMonth.collectAsStateWithLifecycle()

    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background),
    ) {
        Text(
            text = "날짜별 최저가",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = Airport.DESTINATIONS, key = { it.iata }) { airport ->
                DestinationChip(
                    label = airport.cityKo,
                    selected = airport == destination,
                    onClick = { viewModel.selectDestination(airport) },
                )
            }
        }

        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TripTypeChip(
                label = "왕복",
                selected = tripType == TripType.ROUND_TRIP,
                onClick = { viewModel.setTripType(TripType.ROUND_TRIP) },
            )
            TripTypeChip(
                label = "편도",
                selected = tripType == TripType.ONE_WAY,
                onClick = { viewModel.setTripType(TripType.ONE_WAY) },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    // 과거 달로는 못 간다. 눌러도 결과가 같으므로 누르기 전에 흐리게 보여준다.
                    .alpha(if (canGoBack) 1f else 0.3f)
                    .let { if (canGoBack) it.clickable { viewModel.previousMonth() } else it }
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Text(
                text = "${month.year}년 ${month.monthValue}월",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "›",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { viewModel.nextMonth() }
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            WEEKDAY_HEADERS.forEach { day ->
                Text(
                    text = day,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        when (val current = state) {
            CalendarUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "불러오는 중...", color = TextSecondary, fontSize = 13.sp)
            }

            is CalendarUiState.Success -> {
                val calendar = current.calendar
                val today = LocalDate.now()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    MonthGrid.cellsOf(month).chunked(7).forEach { week ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            val median = calendar.median
                            week.forEach { cell ->
                                val quote = cell.date?.let { calendar.byDate[it] }
                                DayCell(
                                    cell = cell,
                                    quote = quote,
                                    isCheapest = cell.date != null && cell.date == calendar.cheapestDate,
                                    isBelowMedian = quote != null && median != null &&
                                        quote.price <= median,
                                    today = today,
                                    onClick = { picked ->
                                        picked.deepLink?.let { url -> BookingLauncher.open(context, url) }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            CalendarUiState.Empty -> CalendarMessage(
                title = "이 달은 가격 정보가 없어요",
                body = "다른 달이나 목적지를 골라보세요.",
                // 빈 데이터는 오류가 아니다. 다시 눌러도 결과가 같으므로 재시도 버튼을 두지 않는다.
                onRetry = null,
            )

            is CalendarUiState.Error -> CalendarMessage(
                title = "가격을 불러오지 못했어요",
                body = "네트워크를 확인하고 다시 시도해주세요.",
                onRetry = if (current.retryable) viewModel::refresh else null,
            )
        }
    }
}

@Composable
private fun DestinationChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.White else TextSecondary,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .background(
                color = if (selected) Indigo else Surface,
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun TripTypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.White else TextSecondary,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .background(
                color = if (selected) Indigo else Surface,
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun CalendarMessage(title: String, body: String, onRetry: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(
            text = body,
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        onRetry?.let { retry ->
            Text(
                text = "다시 시도",
                color = Indigo,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .background(Surface, RoundedCornerShape(20.dp))
                    .clickable(onClick = retry)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}
