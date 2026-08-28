package com.sypark.flightdeal.feed

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sypark.flightdeal.booking.BookingLauncher
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.ui.theme.FlightDealTheme

@Composable
fun DealFeedScreen(
    onSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DealFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    // onClick 람다는 클릭 시점에 실행되는 일반 람다라 그 안에서 Composable인
    // FlightDealTheme.colors를 읽을 수 없다. 미리 변수로 꺼내둔다.
    val indigo = FlightDealTheme.colors.indigo
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* 거절해도 추적 자체는 동작한다. 알림만 못 받는다. */ }

    // NavHost의 바깥 Scaffold가 하단 탭 바를 이미 갖고 있다. 여기서 또 Scaffold를
    // 두면 이 화면의 snackbarHost가 바깥 탭 바 높이를 모른 채 화면 맨 아래에
    // 배치되어 탭과 겹친다. Box + SnackbarHost로 이 화면 안에서만 띄운다.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(FlightDealTheme.colors.background),
        ) {
            Text(
                text = "오늘의 특가",
                color = FlightDealTheme.colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp),
            )

            // 입력창처럼 생겼으면 눌렀을 때 무언가 해야 한다. 목업에서 넘어온 채로
            // 오래 장식으로만 남아 있었다 — 화면에서 가장 눈에 띄는 자리가
            // 아무 일도 하지 않는 상태였다. 목적지를 고르는 화면은 달력이므로
            // 그리로 보낸다.
            Text(
                text = "어디로 떠나세요?",
                color = FlightDealTheme.colors.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onSearch)
                    .border(1.dp, FlightDealTheme.colors.outline, RoundedCornerShape(16.dp))
                    .background(FlightDealTheme.colors.surface, RoundedCornerShape(16.dp))
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 14.dp, vertical = 15.dp),
            )

            Text(
                text = "표시 가격은 참고가예요. 정확한 금액은 예약처에서 확인해주세요.",
                color = FlightDealTheme.colors.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            val tripType by viewModel.tripType.collectAsStateWithLifecycle()

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
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

            when (val current = state) {
                DealFeedUiState.Loading -> DealSkeleton()

                is DealFeedUiState.Success -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = current.deals,
                        key = {
                            "${it.quote.route.destination.iata}/${it.quote.departDate}" +
                                "/${it.quote.airline}/${it.quote.price.amount}"
                        },
                    ) { deal ->
                        DealCard(
                            item = deal,
                            // deepLink가 없는 견적도 있다. 그때는 DealCard가 자체적으로
                            // 클릭을 막고 "예약처 연결 없음"을 보여주므로 여기선 있는 경우만 연다.
                            onClick = {
                                deal.quote.deepLink?.let { url ->
                                    BookingLauncher.open(context, url, indigo)
                                }
                            },
                            onTrack = {
                                viewModel.track(deal)
                                // 런타임 알림 권한은 API 33부터다. 그 아래에서 요청하면
                                // 시스템이 모르는 권한이라 다이얼로그 없이 거부로 돌아온다.
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                        )
                    }
                }

                DealFeedUiState.Empty -> FeedMessage(
                    title = "아직 특가가 없어요",
                    body = "가격 데이터가 모이면 여기에 보여드릴게요.",
                    // 빈 데이터는 오류가 아니다. 재시도해도 결과가 같으므로 버튼을 두지 않는다.
                    onRetry = null,
                )

                is DealFeedUiState.Error -> FeedMessage(
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

@Composable
private fun TripTypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        // 흰색을 고정하지 않는다 — 다크에서 indigo는 밝은 라벤더라 흰 글씨가
        // 잘 안 읽힌다. onPrimary는 Theme.kt가 라이트/다크마다 대비를 맞춰 계산한다.
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
