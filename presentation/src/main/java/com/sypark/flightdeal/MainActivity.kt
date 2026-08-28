package com.sypark.flightdeal

import android.content.Intent
import android.content.res.Configuration
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
        // 이제 앱이 다크 모드를 지원하므로 시스템 바 아이콘도 배경에 맞춰 골라야 한다.
        // 다크에서 light()를 그대로 쓰면 어두운 배경 위에 흰 아이콘이 아니라
        // 다시 어두운 아이콘이 그려져 상태 바가 안 보이게 된다.
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val systemBarStyle = if (isDarkMode) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(
            statusBarStyle = systemBarStyle,
            navigationBarStyle = systemBarStyle,
        )
        openTracking.value = intent?.getBooleanExtra(EXTRA_OPEN_TRACKING, false) == true
        // 인텐트가 그대로 남아 있으면 회전할 때마다 다시 읽힌다 — 특가로 옮겨간 뒤
        // 회전만 해도 추적 탭으로 되돌아가 버린다.
        intent?.removeExtra(EXTRA_OPEN_TRACKING)
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
        // 인텐트가 그대로 남아 있으면 회전할 때마다 다시 읽힌다 — 특가로 옮겨간 뒤
        // 회전만 해도 추적 탭으로 되돌아가 버린다.
        intent.removeExtra(EXTRA_OPEN_TRACKING)
    }

    companion object {
        const val EXTRA_OPEN_TRACKING = "open_tracking"
    }
}
