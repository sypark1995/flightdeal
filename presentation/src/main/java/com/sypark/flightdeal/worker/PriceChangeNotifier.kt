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
        if (!NotificationStatus.isAllowed(context)) return emptyList()

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

        val notification = NotificationCompat.Builder(context, NotificationStatus.CHANNEL_ID)
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
     * 알림 가능 여부 판정은 [NotificationStatus]로 뽑았다 — 내정보 화면도 같은 함수를
     * 부른다. 화면과 워커가 각자 판정하면 둘이 어긋날 수 있다.
     */
    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(NotificationStatus.CHANNEL_ID, "가격 변동", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private companion object {
        // 고정 ID라 새 알림이 이전 알림을 덮어쓴다. 의도한 동작이다: 알림은 정보를
        // 담는 게 아니라 추적 화면으로 가는 포인터일 뿐이고, 그 화면이 항상 최신
        // 전체 상태를 보여주므로 이전 알림이 안 보였어도 잃는 정보가 없다.
        const val NOTIFICATION_ID = 1001
    }
}
