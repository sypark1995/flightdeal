package com.sypark.flightdeal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.ui.theme.FlightDealTheme

/**
 * 딜 피드와 캘린더가 함께 쓰는 출발지 선택기. "인천 출발 ▾" 꼴로 두고, 누르면
 * [Airport.ORIGINS] 중 하나를 고르는 다이얼로그를 연다.
 *
 * 두 화면이 이 컴포저블 하나를 같이 쓴다고 해서 출발지가 저절로 맞춰지지는
 * 않는다 — 실제로 값이 같아지는 것은 두 ViewModel이 같은 `SettingsRepository`를
 * 구독하기 때문이고, 여기서는 그 값을 어떻게 보여주고 어떻게 바꿀지만 정한다.
 */
@Composable
fun OriginSelector(
    origin: Airport,
    onSelect: (Airport) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    Text(
        text = "${origin.cityKo} 출발 ▾",
        color = FlightDealTheme.colors.textSecondary,
        fontSize = 13.sp,
        modifier = modifier
            .clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("출발 공항") },
            text = {
                Column {
                    Airport.ORIGINS.forEach { airport ->
                        val selected = airport == origin
                        Text(
                            text = airport.cityKo,
                            color = if (selected) FlightDealTheme.colors.indigo else FlightDealTheme.colors.textPrimary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // 목록에서 고르는 즉시 저장한다 — 두 화면이 같은 값을
                                    // 봐야 하므로 "확인" 버튼으로 한 번 더 미루지 않는다.
                                    onSelect(airport)
                                    showDialog = false
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("닫기") }
            },
        )
    }
}
