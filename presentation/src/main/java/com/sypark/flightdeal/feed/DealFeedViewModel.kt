package com.sypark.flightdeal.feed

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.usecase.GetDealFeedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DealFeedViewModel @Inject constructor(
    private val getDealFeed: GetDealFeedUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DealFeedUiState>(DealFeedUiState.Loading)
    val uiState: StateFlow<DealFeedUiState> = _uiState.asStateFlow()

    private val _tripType = MutableStateFlow(TripType.ROUND_TRIP)
    val tripType: StateFlow<TripType> = _tripType.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    /** 같은 값이면 조회하지 않는다. 토글을 두 번 눌렀다고 왕복을 다시 받을 이유가 없다. */
    fun setTripType(tripType: TripType) {
        if (_tripType.value == tripType) return
        _tripType.value = tripType
        refresh()
    }

    fun refresh() {
        // 재시도 버튼을 연타하면 느린 이전 요청이 나중에 끝나 최신 결과를 덮어쓴다.
        // 결과만 버리는 게 아니라 요청 자체를 취소한다. 버릴 응답을 받자고
        // 네트워크와 배터리를 쓸 이유가 없다.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = DealFeedUiState.Loading

            // 기본 출발지는 인천 고정. 설정 화면이 생기면 DataStore에서 읽는다.
            _uiState.value = try {
                when (val result = getDealFeed(Airport.INCHEON, _tripType.value)) {
                    is AppResult.Success -> DealFeedUiState.Success(result.data)
                    AppResult.Empty -> DealFeedUiState.Empty
                    is AppResult.NetworkError -> {
                        Log.w(TAG, "네트워크 오류로 특가 조회 실패, 재시도 가능", result.cause)
                        DealFeedUiState.Error(retryable = true)
                    }
                    is AppResult.Unknown -> {
                        Log.e(TAG, "알 수 없는 오류로 특가 조회 실패", result.cause)
                        DealFeedUiState.Error(retryable = false)
                    }
                }
            } catch (e: CancellationException) {
                // 취소는 오류가 아니다. 삼키면 취소된 요청이 화면을 오류로 만든다.
                throw e
            } catch (e: Exception) {
                // Repository 구현체가 AppResult 대신 예외를 던져도 앱이 죽어서는 안 된다.
                Log.e(TAG, "특가 조회 중 예외 발생", e)
                DealFeedUiState.Error(retryable = false)
            }
        }
    }

    private companion object {
        const val TAG = "DealFeed"
    }
}
