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
import com.sypark.flightdeal.ui.Tab
import com.sypark.flightdeal.ui.theme.FlightDealTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val openRoute = MutableStateFlow<String?>(null)

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
        openRoute.value = intent?.getStringExtra(EXTRA_OPEN_ROUTE)
        // 인텐트가 그대로 남아 있으면 회전할 때마다 다시 읽힌다 — 특가로 옮겨간 뒤
        // 회전만 해도 목적지 탭으로 되돌아가 버린다.
        intent?.removeExtra(EXTRA_OPEN_ROUTE)
        setContent {
            FlightDealTheme {
                FlightDealNavHost(openRoute = openRoute)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_OPEN_ROUTE)?.let { openRoute.value = it }
        // 인텐트가 그대로 남아 있으면 회전할 때마다 다시 읽힌다 — 특가로 옮겨간 뒤
        // 회전만 해도 목적지 탭으로 되돌아가 버린다.
        intent.removeExtra(EXTRA_OPEN_ROUTE)
    }

    companion object {
        /**
         * 목적지 탭을 가리키는 문자열 하나. 값은 [Tab.route]를 그대로 쓴다 — 위젯, 알림,
         * 홈 화면 바로가기가 모두 이 extra로 같은 경로에 도착한다.
         */
        const val EXTRA_OPEN_ROUTE = "open_route"

        /** 정적 바로가기([res/xml/shortcuts.xml])가 문자열 리터럴로도 이 값을 쓴다. */
        val ROUTE_TRACKING = Tab.Tracking.route
        val ROUTE_SEARCH = Tab.Search.route
    }
}
