package com.sypark.flightdeal.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import com.sypark.flightdeal.domain.usecase.CheckTrackedPricesUseCase
import com.sypark.flightdeal.domain.usecase.ConfirmNotifiedUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class PriceCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val checkPrices: CheckTrackedPricesUseCase,
    private val trackedRoutes: TrackedRouteRepository,
    private val notifier: PriceChangeNotifier,
    private val confirmNotified: ConfirmNotifiedUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val changes = checkPrices()
        // 전달된 뒤에만 기준선을 옮긴다. 순서가 뒤집히면 알림이 실패한 변동이 사라진다.
        if (notifier.notify(changes, trackedRoutes.getAll())) {
            confirmNotified(changes)
        }
        Log.d(TAG, "가격 확인 완료, 변동 ${changes.size}건")
        Result.success()
    } catch (e: Exception) {
        // 다음 정기 주기를 기다리는 것보다 조금 뒤 다시 해보는 편이 낫다.
        Log.w(TAG, "가격 확인 실패", e)
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    private companion object {
        const val TAG = "PriceCheckWorker"
        const val MAX_ATTEMPTS = 3
    }
}
