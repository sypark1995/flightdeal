package com.sypark.flightdeal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.sypark.flightdeal.ui.theme.Background
import com.sypark.flightdeal.ui.theme.TextSecondary

@Composable
fun PlaceholderScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(Background),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "준비 중입니다", color = TextSecondary, fontSize = 14.sp)
    }
}
