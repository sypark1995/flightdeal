package com.sypark.flightdeal.calendar

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.usecase.GetMonthCalendarUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val getMonthCalendar: GetMonthCalendarUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    // 딜 피드가 도쿄를 기본으로 보여준다. 캘린더도 같은 목적지로 시작해야
    // 탭을 오갈 때 같은 여정을 계속 보는 것처럼 느껴진다.
    private val _destination = MutableStateFlow(Airport.DESTINATIONS.first())
    val destination: StateFlow<Airport> = _destination.asStateFlow()

    private val _month = MutableStateFlow(YearMonth.now(clock))
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    /**
     * 딜 피드와 기본값을 반드시 맞춘다. 여기만 편도로 시작하면 같은 날짜인데
     * 화면마다 3배 가까이 다른 값이 뜨고, 사용자는 원인을 알 방법이 없다.
     */
    private val _tripType = MutableStateFlow(TripType.ROUND_TRIP)
    val tripType: StateFlow<TripType> = _tripType.asStateFlow()

    /** 이전 달 이동 버튼을 흐리게 보여줄지 화면이 판단하는 데 쓴다. */
    val canGoToPreviousMonth: StateFlow<Boolean> = _month
        .map { it > YearMonth.now(clock) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var loadJob: Job? = null

    init {
        refresh()
    }

    /** 같은 목적지를 다시 고르면 조회하지 않는다. */
    fun selectDestination(airport: Airport) {
        if (_destination.value == airport) return
        _destination.value = airport
        refresh()
    }

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
        refresh()
    }

    /**
     * 과거 달로는 못 가게 막는다. 지난 달은 소스가 아무 것도 주지 않아
     * 빈 달력만 나오고, 사용자는 왜 비었는지 알 길이 없다.
     */
    fun previousMonth() {
        val target = _month.value.minusMonths(1)
        if (target < YearMonth.now(clock)) return
        _month.value = target
        refresh()
    }

    /** 같은 값이면 조회하지 않는다. */
    fun setTripType(tripType: TripType) {
        if (_tripType.value == tripType) return
        _tripType.value = tripType
        refresh()
    }

    fun refresh() {
        // 목적지·달·종류를 바꿀 때마다 새로 조회한다. 이전 요청을 취소하지 않으면
        // 느린 이전 응답이 나중에 도착해 방금 고른 목적지의 결과를 덮어쓴다.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = CalendarUiState.Loading

            val route = Route(Airport.INCHEON, _destination.value)
            _uiState.value = try {
                when (val result = getMonthCalendar(route, _month.value, _tripType.value)) {
                    is AppResult.Success -> CalendarUiState.Success(result.data)
                    AppResult.Empty -> CalendarUiState.Empty
                    is AppResult.NetworkError -> {
                        Log.w(TAG, "네트워크 오류로 캘린더 조회 실패, 재시도 가능", result.cause)
                        CalendarUiState.Error(retryable = true)
                    }
                    is AppResult.Unknown -> {
                        Log.e(TAG, "알 수 없는 오류로 캘린더 조회 실패", result.cause)
                        CalendarUiState.Error(retryable = false)
                    }
                }
            } catch (e: CancellationException) {
                // 취소는 오류가 아니다. 삼키면 취소된 요청이 화면을 오류로 만든다.
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "캘린더 조회 중 예외 발생", e)
                CalendarUiState.Error(retryable = false)
            }
        }
    }

    private companion object {
        const val TAG = "Calendar"
    }
}
