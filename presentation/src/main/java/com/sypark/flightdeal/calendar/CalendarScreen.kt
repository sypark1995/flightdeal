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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.sypark.flightdeal.ui.OriginSelector
import com.sypark.flightdeal.ui.theme.FlightDealTheme

private val WEEKDAY_HEADERS = listOf("월", "화", "수", "목", "금", "토", "일")

@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val origin by viewModel.origin.collectAsStateWithLifecycle()
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    val tripType by viewModel.tripType.collectAsStateWithLifecycle()
    val canGoBack by viewModel.canGoToPreviousMonth.collectAsStateWithLifecycle()

    val context = LocalContext.current
    // onClick 람다는 Composable 스코프가 아니라 클릭 시점에 실행된다 —
    // 그 안에서 FlightDealTheme.colors를 직접 못 읽으므로 미리 변수로 꺼내둔다.
    val indigo = FlightDealTheme.colors.indigo

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(FlightDealTheme.colors.background)
                // 6주짜리 달은 그리드 6행 아래에 캡션 두 줄을 더할 자리가 늘 남지는 않는다.
                // 스크롤이 없으면 그 경우 화면 아래로 넘친 부분이 소리 없이 잘려 안 보인다 —
                // 캡션을 그리드 아래 붙이면서 실제로 그 잘림이 나타났다.
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "날짜별 최저가",
                color = FlightDealTheme.colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp),
            )

            OriginSelector(origin = origin, onSelect = viewModel::selectOrigin)

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
                    color = FlightDealTheme.colors.textPrimary,
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
                    color = FlightDealTheme.colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "›",
                    color = FlightDealTheme.colors.textPrimary,
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
                        color = FlightDealTheme.colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            when (val current = state) {
                CalendarUiState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "불러오는 중...", color = FlightDealTheme.colors.textSecondary, fontSize = 13.sp)
                }

                is CalendarUiState.Success -> {
                    val calendar = current.calendar
                    // ViewModel이 주입받은 Clock에서 뽑는다 — 여기서 LocalDate.now()를
                    // 직접 부르면 CalendarViewModel과 다른 시계를 보게 되고, 테스트에서도
                    // 이 화면만 고정할 방법이 없다.
                    val today = viewModel.today

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
                                        isUnbookable = cell.date != null && cell.date in calendar.unbookableDates,
                                        today = today,
                                        onClick = { picked ->
                                            picked.deepLink?.let { url ->
                                                if (!BookingLauncher.open(context, url, indigo)) {
                                                    viewModel.bookingUnavailable()
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }

                    CalendarCaption(tripType = tripType)
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        )
    }
}

/**
 * 그리드 아래에 두는 안내 두 줄.
 *
 * 이 화면 제목은 "날짜별 최저가"라 날짜끼리 값을 직접 비교하는 그리드처럼 읽힌다.
 * 하지만 왕복은 API가 여정 길이를 고정해주지 않아 날짜마다 귀국일이 다르고,
 * 예약처 규칙 때문에 어떤 날은 셀이 비어 있다. 둘 다 쿼리를 바꿔서 없앨 수 없는
 * 차이라, 화면이 최소한 그 사실을 말해줘야 사용자가 그리드를 잘못 읽지 않는다.
 */
@Composable
private fun CalendarCaption(tripType: TripType) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        // 두 줄이 딱 붙으면 좁은 화면에서 서로 눌린 것처럼 읽힌다. 최소한의 숨 쉴
        // 틈을 둔다.
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (tripType == TripType.ROUND_TRIP) {
            Text(
                text = "왕복은 날짜마다 귀국일이 달라요. 눌러서 확인하세요.",
                color = FlightDealTheme.colors.textSecondary,
                fontSize = 11.sp,
            )
        }
        Text(
            text = "한국에서 예약할 수 없는 날은 —로 표시해요.",
            color = FlightDealTheme.colors.textSecondary,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun DestinationChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        // 흰색을 고정하지 않는다 — 다크에서 indigo는 밝은 라벤더라 흰 글씨가
        // 잘 안 읽힌다. onPrimary는 Theme.kt가 라이트/다크마다 대비를 맞춰 계산한다.
        color = if (selected) MaterialTheme.colorScheme.onPrimary else FlightDealTheme.colors.textSecondary,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .background(
                color = if (selected) FlightDealTheme.colors.indigo else FlightDealTheme.colors.surface,
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
        color = if (selected) MaterialTheme.colorScheme.onPrimary else FlightDealTheme.colors.textSecondary,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .background(
                color = if (selected) FlightDealTheme.colors.indigo else FlightDealTheme.colors.surface,
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun CalendarMessage(title: String, body: String, onRetry: (() -> Unit)?) {
    // fillMaxSize가 아니라 fillMaxWidth다. 화면 전체(그리드+캡션 포함)가 스크롤
    // 가능한 Column 안에 있어, 여기서 fillMaxSize를 쓰면 세로로 무한한 높이
    // 제약에서 "화면 전체를 채워라"를 시도하다 크래시한다.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, color = FlightDealTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(
            text = body,
            color = FlightDealTheme.colors.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        onRetry?.let { retry ->
            Text(
                text = "다시 시도",
                color = FlightDealTheme.colors.indigo,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .background(FlightDealTheme.colors.surface, RoundedCornerShape(20.dp))
                    .clickable(onClick = retry)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}
