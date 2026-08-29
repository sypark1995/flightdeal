package com.sypark.flightdeal.tracking

import androidx.compose.foundation.background
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
import com.sypark.flightdeal.ui.theme.FlightDealTheme

@Composable
fun TrackingScreen(
    onOpenAlertHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TrackingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().background(FlightDealTheme.colors.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "추적 중인 항공권",
                color = FlightDealTheme.colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            // 알림 기록은 추적에 딸린 화면이다 — 하단 탭을 늘리지 않고 여기서 들어간다.
            Text(
                text = "알림 기록",
                color = FlightDealTheme.colors.indigo,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenAlertHistory)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .wrapContentSize(Alignment.CenterEnd)
                    .padding(horizontal = 4.dp),
            )
        }

        when (val current = state) {
            TrackingUiState.Loading -> Box(Modifier.fillMaxSize())

            TrackingUiState.Empty -> Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "특가 탭에서 마음에 드는 항공권을 추적해보세요.\n가격이 바뀌면 알려드릴게요.",
                    color = FlightDealTheme.colors.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }

            is TrackingUiState.Success -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items = current.items, key = { it.tracked.id }) { item ->
                    TrackedRouteCard(
                        item = item,
                        onUntrack = { viewModel.untrack(item.tracked.id) },
                        onSetTarget = { target -> viewModel.setTarget(item.tracked.id, target) },
                    )
                }
            }
        }
    }
}
