package com.sypark.flightdeal.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sypark.flightdeal.domain.repository.PriceHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 알림 on/off는 이 ViewModel이 다루지 않는다. 안드로이드 알림 설정이 유일한 진실이고
 * [com.sypark.flightdeal.worker.NotificationStatus]가 그것을 읽는다 — 화면이 직접
 * 안드로이드 API를 부르므로 단위 테스트 대상이 아니다(기기에서 확인한다).
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val history: PriceHistoryRepository,
) : ViewModel() {

    val historyCount: StateFlow<Int> = history.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun clearHistory() {
        viewModelScope.launch { history.clearAll() }
    }
}
