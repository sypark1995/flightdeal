package com.sypark.flightdeal.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sypark.flightdeal.ui.theme.Background
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

        when (val current = state) {
            DealFeedUiState.Loading -> DealSkeleton()

            is DealFeedUiState.Success -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = current.deals,
                    key = { "${it.quote.route.destination.iata}/${it.quote.departDate}" },
                ) { deal ->
                    DealCard(item = deal, onClick = { /* 딥링크는 이후 계획서에서 */ })
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
