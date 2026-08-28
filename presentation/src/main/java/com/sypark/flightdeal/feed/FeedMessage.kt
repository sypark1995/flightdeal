package com.sypark.flightdeal.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sypark.flightdeal.ui.theme.FlightDealTheme

@Composable
fun FeedMessage(
    title: String,
    body: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, color = FlightDealTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            text = body,
            color = FlightDealTheme.colors.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (onRetry != null) {
            Button(
                onClick = onRetry,
                // 흰색을 고정하지 않는다 — 다크에서 indigo는 밝은 라벤더라 흰 글씨가
                // 잘 안 읽힌다. onPrimary는 Theme.kt가 라이트/다크마다 대비를 맞춰 계산한다.
                colors = ButtonDefaults.buttonColors(
                    containerColor = FlightDealTheme.colors.indigo,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(text = "다시 시도")
            }
        }
    }
}
