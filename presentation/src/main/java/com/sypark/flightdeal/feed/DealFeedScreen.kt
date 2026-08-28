package com.sypark.flightdeal.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

        // 로딩·빈 데이터·오류 상태는 Task 9에서 붙인다.
        if (state is DealFeedUiState.Success) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = (state as DealFeedUiState.Success).deals,
                    key = { it.quote.route.destination.iata + it.quote.departDate },
                ) { deal ->
                    DealCard(item = deal, onClick = { /* 딥링크는 이후 계획서에서 */ })
                }
            }
        }
    }
}
