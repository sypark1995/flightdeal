package com.sypark.flightdeal.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat

/**
 * 알림 가능 여부 판정을 한 곳에 둔다.
 *
 * [PriceChangeNotifier]와 내정보 화면이 각자 판정을 구현하면 둘이 어긋날 수 있다 —
 * 화면은 "켜짐"이라고 보여주는데 정작 알림은 안 오는 상태가 생긴다. 그래서 이 프로젝트가
 * 전에 겪은 결함과 같은 모양이 되지 않도록 판정 함수를 하나로 뽑아 양쪽이 그대로 부른다.
 */
object NotificationStatus {

    /** [PriceChangeNotifier]가 알림을 만들 때 쓰는 채널. 화면이 시스템 설정을 열 때도 같은 값이 필요하다. */
    const val CHANNEL_ID = "price_change"

    /**
     * 채널이 아직 없으면 만든다. [PriceChangeNotifier]가 알림을 보내기 전에,
     * 그리고 내정보 화면이 채널 설정 화면을 열기 전에 똑같이 부른다.
     *
     * 채널 생성 책임을 여기 하나로 모은 이유: 채널은 새 설치에서는 알림을 한 번도
     * 보내기 전까지 존재하지 않는다. 화면이 `ACTION_CHANNEL_NOTIFICATION_SETTINGS`로
     * 그 채널의 설정 화면을 열려고 하면, 채널이 없어도 예외는 나지 않는다 — 시스템
     * Settings 액티비티 자체는 항상 있으니 그냥 열렸다가 빈 화면인 채로 바로 끝난다.
     * 그래서 "설정 화면이 안 열리는 경우"를 잡으려면 예외를 기다릴 게 아니라, 열기
     * 전에 채널이 있는지부터 이 함수로 보장해야 한다.
     */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "가격 변동", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    /**
     * 앱 전체 스위치와 채널 importance를 모두 본다. 기기에서 음소거/해제 대조로
     * 검증된 판정이므로 로직을 바꾸지 않는다.
     *
     * `POST_NOTIFICATIONS`를 직접 확인하면 안 된다. 그 권한은 API 33부터 존재하고,
     * minSdk 26~32에서는 시스템이 모르는 권한이라 항상 거부로 나온다.
     * 정작 그 버전들은 런타임 권한 자체가 필요 없는데도 알림이 전부 버려진다.
     *
     * `areNotificationsEnabled()`만 보면 안 된다. 사용자가 "가격 변동" 채널 하나만 끈
     * 경우에도 참을 돌려주는데, 그 상태에서 알림은 조용히 버려진다.
     */
    fun isAllowed(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL_ID)
        // channel == null은 채널이 아직 안 만들어졌다는 뜻이다(알림을 한 번도 안 보낸 새 설치,
        // 또는 조회 실패). 사용자가 끈 것이 아니므로 허용으로 본다.
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }
}
