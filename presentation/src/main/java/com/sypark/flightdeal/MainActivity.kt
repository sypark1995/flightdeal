package com.sypark.flightdeal

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sypark.flightdeal.ui.FlightDealNavHost
import com.sypark.flightdeal.ui.theme.FlightDealTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            // 앱이 라이트 전용이므로 시스템 uiMode가 아니라 앱 배경에 맞춰 고정한다.
            // 기본 오버로드는 기기 다크 모드에서 흰 아이콘을 흰 배경 위에 그린다.
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            FlightDealTheme {
                FlightDealNavHost()
            }
        }
    }
}
