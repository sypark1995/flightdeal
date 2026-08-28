package com.sypark.flightdeal.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {

    /**
     * WorkManager의 최소 주기는 15분이지만 항공권 가격은 분 단위로 바뀌지 않는다.
     * 짧게 잡으면 배터리와 API 쿼터만 쓰고 Doze에서 어차피 밀린다.
     *
     * internal로 열어 내정보 화면이 이 값을 그대로 읽는다. 화면에 "6"을 따로 적으면
     * 나중에 주기를 바꿀 때 화면만 거짓말하게 된다.
     */
    internal const val INTERVAL_HOURS = 6L
    private const val WORK_NAME = "price-check"

    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<PriceCheckWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            // 이미 예약돼 있으면 그대로 둔다. 앱을 열 때마다 주기가 초기화되면
            // 자주 여는 사용자에게는 워커가 영영 돌지 않는다.
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
