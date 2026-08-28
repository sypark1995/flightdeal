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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.sypark.flightdeal.ui.theme.Background
import com.sypark.flightdeal.ui.theme.Indigo
import com.sypark.flightdeal.ui.theme.Outline
import com.sypark.flightdeal.ui.theme.Surface
import com.sypark.flightdeal.ui.theme.TextPrimary
import com.sypark.flightdeal.ui.theme.TextSecondary

@Composable
fun DealFeedScreen(
    modifier: Modifier = Modifier,
    viewModel: DealFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* 거절해도 추적 자체는 동작한다. 알림만 못 받는다. */ }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background),
    ) {
        Text(
            text = "오늘의 특가",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
        )

        Text(
            text = "어디로 떠나세요?",
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .border(1.dp, Outline, RoundedCornerShape(16.dp))
                .background(Surface, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 13.dp),
        )

        Text(
            text = "표시 가격은 참고가예요. 정확한 금액은 예약처에서 확인해주세요.",
            color = TextSecondary,
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
                            deal.quote.deepLink?.let { url -> BookingLauncher.open(context, url) }
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
