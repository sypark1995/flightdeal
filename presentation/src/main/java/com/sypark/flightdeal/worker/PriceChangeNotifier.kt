package com.sypark.flightdeal.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sypark.flightdeal.domain.model.Direction
import com.sypark.flightdeal.domain.model.PriceChange
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.feed.formatWon
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class PriceChangeNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * 한 번의 워커 실행에서 바뀐 것들을 알림 하나로 묶는다.
     * 노선마다 따로 쏘면 추적을 여러 개 걸어둔 사용자에게 알림 폭탄이 된다.
     */
    fun notify(changes: List<PriceChange>, routes: List<TrackedRoute>) {
        if (changes.isEmpty()) return

        ensureChannel()
        if (!notificationsAllowed()) return

        val byId = routes.associateBy { it.id }
        val lines = changes.mapNotNull { change ->
            val route = byId[change.trackedRouteId] ?: return@mapNotNull null
            val arrow = if (change.direction == Direction.DOWN) "▼" else "▲"
            val target = if (change.reachedTarget) " · 목표가 도달" else ""
            "$arrow ${route.route.destination.cityKo} ${formatWon(change.current)}$target"
        }
        if (lines.isEmpty()) return

        val title = if (changes.size == 1) "항공권 가격이 바뀌었어요" else "항공권 ${changes.size}건의 가격이 바뀌었어요"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(NotificationCompat.InboxStyle().also { style -> lines.forEach(style::addLine) })
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /**
     * 권한이 없으면 조용히 넘어간다. 알림을 못 받을 뿐 앱의 나머지는 정상 동작해야 한다.
     *
     * `POST_NOTIFICATIONS`를 직접 확인하면 안 된다. 그 권한은 API 33부터 존재하고,
     * minSdk 26~32에서는 시스템이 모르는 권한이라 항상 거부로 나온다.
     * 정작 그 버전들은 런타임 권한 자체가 필요 없는데도 알림이 전부 버려진다.
     */
    private fun notificationsAllowed(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "가격 변동", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private companion object {
        const val CHANNEL_ID = "price_change"
        const val NOTIFICATION_ID = 1001
    }
}
