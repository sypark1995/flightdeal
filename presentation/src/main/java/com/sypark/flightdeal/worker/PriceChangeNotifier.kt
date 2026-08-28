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
     *
     * @return 알림에 실제로 담겨 보인 변동들. 권한이 없거나, 노선 정보가 없어 문구를
     *   만들지 못했거나, 애초에 입력이 비어 있으면 빈 리스트다 — 호출부는 이 리스트에
     *   담긴 변동만 통보된 것으로 보고 기준선을 옮겨야 한다. 화면에 뜨지 않은 변동을
     *   같이 옮기면 사용자는 보지도 못한 변동을 "통보받은 것"으로 잃는다.
     */
    fun notify(changes: List<PriceChange>, routes: List<TrackedRoute>): List<PriceChange> {
        if (changes.isEmpty()) return emptyList()

        ensureChannel()
        if (!notificationsAllowed()) return emptyList()

        val byId = routes.associateBy { it.id }
        val shown = changes.filter { byId.containsKey(it.trackedRouteId) }
        val lines = shown.map { change ->
            val route = byId.getValue(change.trackedRouteId)
            val arrow = if (change.direction == Direction.DOWN) "▼" else "▲"
            val target = if (change.reachedTarget) " · 목표가 도달" else ""
            "$arrow ${route.route.destination.cityKo} ${formatWon(change.current)}$target"
        }
        if (lines.isEmpty()) return emptyList()

        val title = if (shown.size == 1) "항공권 가격이 바뀌었어요" else "항공권 ${shown.size}건의 가격이 바뀌었어요"

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
        return shown
    }

    /**
     * 권한이 없으면 조용히 넘어간다. 알림을 못 받을 뿐 앱의 나머지는 정상 동작해야 한다.
     *
     * `POST_NOTIFICATIONS`를 직접 확인하면 안 된다. 그 권한은 API 33부터 존재하고,
     * minSdk 26~32에서는 시스템이 모르는 권한이라 항상 거부로 나온다.
     * 정작 그 버전들은 런타임 권한 자체가 필요 없는데도 알림이 전부 버려진다.
     *
     * 앱 전체 스위치와 채널 스위치를 모두 본다.
     *
     * `areNotificationsEnabled()`만 보면 안 된다. 사용자가 "가격 변동" 채널 하나만 끈
     * 경우에도 참을 돌려주는데, 그 상태에서 `notify()`는 조용히 버려진다. 그걸 성공으로
     * 보고하면 워커가 기준선을 옮기고, 알리지 못한 변동이 영영 사라진다.
     */
    private fun notificationsAllowed(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL_ID)
        // channel == null은 ensureChannel()이 방금 만들었는데도 조회가 실패했다는 뜻이다.
        // 사용자가 끈 것이 아니라 조회 실패이므로 허용으로 본다.
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

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
