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
import kotlinx.coroutines.CancellationException

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
    } catch (e: CancellationException) {
        // WorkManager가 실행을 멈춘 것(네트워크 제약 소실, 10분 상한)이지 실패가 아니다.
        // 삼키면 취소된 실행이 스스로 실패를 로그로 남기고 취소된 스코프의 결과를 반환한다.
        throw e
    } catch (e: Exception) {
        // 다음 정기 주기를 기다리는 것보다 조금 뒤 다시 해보는 편이 낫다.
        Log.w(TAG, "가격 확인 실패", e)
        if (runAttemptCount < MAX_ATTEMPTS) {
            Result.retry()
        } else {
            // 주기 작업에서 failure()는 스케줄 자체를 끝낸다. 이번 회차를 포기하는 것과
            // 예약을 취소하는 것은 다르다 — success()로 돌려줘야 다음 6시간 주기가 산다.
            Result.success()
        }
    }

    private companion object {
        const val TAG = "PriceCheckWorker"
        const val MAX_ATTEMPTS = 3
    }
}
