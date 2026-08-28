package com.sypark.flightdeal.feed

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.DealItem
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.usecase.GetDealFeedUseCase
import com.sypark.flightdeal.domain.usecase.TrackRouteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DealFeedViewModel @Inject constructor(
    private val getDealFeed: GetDealFeedUseCase,
    private val trackRoute: TrackRouteUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DealFeedUiState>(DealFeedUiState.Loading)
    val uiState: StateFlow<DealFeedUiState> = _uiState.asStateFlow()

    private val _tripType = MutableStateFlow(TripType.ROUND_TRIP)
    val tripType: StateFlow<TripType> = _tripType.asStateFlow()

    /**
     * 일회성 안내. `StateFlow`로 두면 화면 회전 때 같은 메시지가 다시 뜬다 —
     * 마지막 값을 replay하기 때문이다. `Channel`은 한 번 받으면 사라진다.
     */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

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

    /** 지금 화면이 보여주는 여정 종류로 등록한다. 화면과 다른 종류로 저장하면 이후 비교가 어긋난다. */
    fun track(item: DealItem) {
        viewModelScope.launch {
            try {
                val registration = trackRoute(item.quote, _tripType.value)
                _messages.send(if (registration.isNew) "추적을 시작했어요" else "이미 추적 중이에요")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 실패해도 로그에만 남기고 끝나면 사용자는 버튼을 누른 뒤 성공 여부를
                // 영영 알 수 없다.
                Log.e(TAG, "추적 등록 실패", e)
                _messages.send("추적을 시작하지 못했어요")
            }
        }
    }

    private companion object {
        const val TAG = "DealFeed"
    }
}
