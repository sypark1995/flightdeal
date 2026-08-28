package com.sypark.flightdeal.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sypark.flightdeal.MainActivity
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

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TRACKING, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            // FLAG_IMMUTABLE은 API 31부터 필수다.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(NotificationCompat.InboxStyle().also { style -> lines.forEach(style::addLine) })
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
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

        // 고정 ID라 새 알림이 이전 알림을 덮어쓴다. 의도한 동작이다: 알림은 정보를
        // 담는 게 아니라 추적 화면으로 가는 포인터일 뿐이고, 그 화면이 항상 최신
        // 전체 상태를 보여주므로 이전 알림이 안 보였어도 잃는 정보가 없다.
        const val NOTIFICATION_ID = 1001
    }
}
