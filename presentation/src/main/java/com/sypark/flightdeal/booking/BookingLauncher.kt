package com.sypark.flightdeal.booking

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.toArgb
import com.sypark.flightdeal.ui.theme.Indigo

private const val TAG = "BookingLauncher"

/**
 * 예약 페이지를 앱 안에서 연다.
 *
 * 외부 브라우저를 띄우면 사용자가 앱을 떠나고, 돌아오려면 작업 전환을 해야 한다.
 * Custom Tabs는 뒤로 가기 한 번이면 딜 피드로 돌아온다.
 */
object BookingLauncher {

    fun open(context: Context, url: String) {
        val colorSchemeParams = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(Indigo.toArgb())
            .build()

        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .setDefaultColorSchemeParams(colorSchemeParams)
            .build()

        try {
            intent.launchUrl(context, Uri.parse(url))
        } catch (e: ActivityNotFoundException) {
            // Custom Tabs를 지원하는 브라우저가 없는 기기가 있다. 그때는 아무 브라우저나 쓴다.
            // 여기서 막히면 사용자는 찾은 항공권을 예약할 방법이 없다.
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                .onFailure { Log.w(TAG, "예약 페이지를 열 수 있는 앱이 없다", it) }
        }
    }
}
