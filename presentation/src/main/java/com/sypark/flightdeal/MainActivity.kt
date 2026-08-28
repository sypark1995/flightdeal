package com.sypark.flightdeal

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sypark.flightdeal.ui.FlightDealNavHost
import com.sypark.flightdeal.ui.theme.FlightDealTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val openTracking = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            // 앱이 라이트 전용이므로 시스템 uiMode가 아니라 앱 배경에 맞춰 고정한다.
            // 기본 오버로드는 기기 다크 모드에서 흰 아이콘을 흰 배경 위에 그린다.
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        openTracking.value = intent?.getBooleanExtra(EXTRA_OPEN_TRACKING, false) == true
        setContent {
            FlightDealTheme {
                FlightDealNavHost(openTracking = openTracking)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_TRACKING, false)) openTracking.value = true
    }

    companion object {
        const val EXTRA_OPEN_TRACKING = "open_tracking"
    }
}
