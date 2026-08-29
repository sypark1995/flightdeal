package com.sypark.flightdeal.feed

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.DealItem
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.repository.SettingsRepository
import com.sypark.flightdeal.domain.usecase.GetDealFeedUseCase
import com.sypark.flightdeal.domain.usecase.TrackRouteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DealFeedViewModel @Inject constructor(
    private val getDealFeed: GetDealFeedUseCase,
    private val trackRoute: TrackRouteUseCase,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DealFeedUiState>(DealFeedUiState.Loading)
    val uiState: StateFlow<DealFeedUiState> = _uiState.asStateFlow()

    private val _origin = MutableStateFlow(Airport.INCHEON)
    val origin: StateFlow<Airport> = _origin.asStateFlow()

    /**
     * 저장된 출발지를 아직 한 번도 못 읽었는지. [_origin]의 초깃값(인천)과 실제로
     * 저장된 값이 우연히 같으면 "값이 바뀌었는지"만으로는 최초 조회를 트리거할 수
     * 없다 — 그래서 값 비교와 별개로 최초 1회는 무조건 조회한다.
     */
    private var originLoaded = false

    private val _tripType = MutableStateFlow(TripType.ROUND_TRIP)
    val tripType: StateFlow<TripType> = _tripType.asStateFlow()

    /**
     * 지금 화면에 떠 있는 목록이 실제로 어떤 출발지·여정 종류의 결과인지.
     *
     * "이미 보여줄 데이터가 있으면 실패해도 목록을 유지한다"는 정책은 **같은 조건을
     * 다시 물어본 요청**(새로고침, 같은 값으로의 재조회)에만 적용해야 한다. 조건이
     * 바뀐 요청(다른 출발지·다른 여정 종류)이 실패하거나 빈 결과면, 화면에 남아있는
     * 목록은 이번 질문에 대한 답이 아니므로 유지할 근거가 없다 — 인천 목록을 띄워둔
     * 채로 제주로 바꿨는데 제주가 빈 결과라면, 화면은 "제주 출발"이라 말하면서 인천
     * 가격을 보여주게 된다. 그래서 이번 요청이 "같은 조건"인지 판단할 기준으로
     * `_origin`/`_tripType`(탭한 즉시 바뀌는 값)이 아니라, 화면에 실제로 반영된
     * 마지막 결과가 어떤 조건이었는지를 별도로 들고 있는다. 요청이 성공하거나
     * 빈 결과로 화면에 실제로 반영됐을 때만 이 값을 갱신한다.
     */
    private var loadedTripType = TripType.ROUND_TRIP
    private var loadedOrigin = Airport.INCHEON

    /**
     * 일회성 안내. `StateFlow`로 두면 화면 회전 때 같은 메시지가 다시 뜬다 —
     * 마지막 값을 replay하기 때문이다.
     *
     * `Channel`은 쓰지 않는다. 이 ViewModel은 탭을 전환해도 살아남는데(NavHost가
     * saveState/restoreState를 쓴다), `Channel`은 화면이 구독을 끊은 동안 보낸
     * 메시지를 버리지 않고 버퍼에 쌓아뒀다가 사용자가 돌아와 다시 구독하는 순간
     * 그대로 튀어나온다 — 몇 분 전에 실패한 새로고침 안내가 지금 막 일어난 일처럼
     * 뜨는 식이다. `MutableSharedFlow(replay = 0, extraBufferCapacity = 1,
     * onBufferOverflow = DROP_OLDEST)`는 구독자가 없으면(=화면이 안 보고 있으면)
     * `tryEmit`이 그 메시지를 조용히 버린다. 일부러 그렇게 둔다 — 아무도 안 보고
     * 있었다면 사용자가 돌아왔을 때 이미 낡은 메시지이므로, 뒤늦게 보여주는 것보다
     * 버리는 쪽이 맞다.
     */
    private val _messages = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var loadJob: Job? = null

    init {
        // 인천으로 먼저 그렸다가 저장된 값으로 다시 그리면 화면이 한 번 깜빡인다.
        // 그래서 refresh()를 바로 부르지 않고, DataStore가 실제 값을 주는 순간에만
        // 조회를 시작한다. 이후 값이 바뀔 때(달력 등 다른 화면에서 바꿔도)도
        // 같은 경로로 다시 조회된다 — 두 화면이 다른 출발지를 보여줄 일이 없다.
        viewModelScope.launch {
            settings.observeOrigin().collect { newOrigin ->
                val changed = originLoaded && newOrigin != _origin.value
                _origin.value = newOrigin
                val shouldLoad = !originLoaded || changed
                originLoaded = true
                if (shouldLoad) load()
            }
        }
    }

    /** 같은 값이면 조회하지 않는다. 토글을 두 번 눌렀다고 왕복을 다시 받을 이유가 없다. */
    fun setTripType(tripType: TripType) {
        if (_tripType.value == tripType) return
        _tripType.value = tripType
        load()
    }

    fun refresh() = load()

    /**
     * 출발지를 고르면 DataStore에 저장한다. 화면 상태([_origin])는 여기서 직접
     * 바꾸지 않는다 — [init]의 collect가 저장된 값을 다시 읽어와 반영해야
     * 캘린더 화면도 같은 경로로 같은 값을 보게 된다.
     */
    fun selectOrigin(airport: Airport) {
        viewModelScope.launch { settings.setOrigin(airport) }
    }

    private fun load() {
        // 재시도 버튼을 연타하면 느린 이전 요청이 나중에 끝나 최신 결과를 덮어쓴다.
        // 결과만 버리는 게 아니라 요청 자체를 취소한다. 버릴 응답을 받자고
        // 네트워크와 배터리를 쓸 이유가 없다.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // 이미 보여줄 목록이 있으면 Loading으로 되돌리지 않는다. 화면은 데이터를
            // 보여주던 상태에서 뒤로 가지 않는다 — 스켈레톤이 떴다가 목록이 돌아오는
            // 깜빡임도 막는다.
            val hadData = _uiState.value is DealFeedUiState.Success

            // 이번 요청이 어떤 종류를 조회하는지 시작 시점에 고정한다. _tripType은
            // 이 코루틴이 도는 동안에도 다시 바뀔 수 있어, 나중에 다시 읽으면
            // 이 요청이 실제로 물어본 종류가 아닐 수 있다.
            val requestedTripType = _tripType.value
            // 출발지도 트립타입과 같은 이유로 시작 시점에 고정한다 — 조회가 도는 동안
            // 사용자가 다른 공항을 고르면 [_origin]이 먼저 바뀌어, 나중에 다시 읽으면
            // 이 요청이 실제로 물어본 공항이 아닐 수 있다.
            val requestedOrigin = _origin.value

            // 핵심 규칙: 같은 조건일 때만 목록을 유지한다. 화면에 떠 있는 목록이
            // 지금 이 요청과 같은 출발지·여정 종류의 결과일 때만 "실패해도 지우지
            // 않는다" 정책을 쓴다. 조건이 다르면(출발지나 종류가 바뀌었으면) 화면의
            // 목록은 이번 질문에 대한 답이 아니므로 유지할 근거가 없다 — 곧바로
            // Loading으로 비우고, 결과가 무엇이든(Success/Empty/Error) 그대로 보여준다.
            val sameQuery = requestedOrigin == loadedOrigin && requestedTripType == loadedTripType
            val keepOnFailure = hadData && sameQuery
            if (!keepOnFailure) _uiState.value = DealFeedUiState.Loading

            val nextState = try {
                when (val result = getDealFeed(requestedOrigin, requestedTripType)) {
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

            // 같은 조건인데 목록이 떠 있는 상태에서 이번 조회가 오류로 실패했다면,
            // 오래된 값을 최신인 것처럼 보여주는 셈이니 목록은 유지하되 실패했다는
            // 사실은 스낵바로 알린다. Empty는 오류가 아니다 — 같은 조건에서 목록이
            // 있었다면 조용히 유지하고(빈 결과로 덮지 않는다), 없었다면 Empty
            // 화면이 맞다. 조건이 바뀐 요청이면(keepOnFailure == false) 이 두
            // 분기에 걸리지 않고 아래 else로 내려가 결과를 그대로 반영한다 — 유지할
            // 이전 목록 자체가 없기 때문이다.
            when {
                keepOnFailure && nextState is DealFeedUiState.Error ->
                    _messages.tryEmit("가격을 새로 받아오지 못했어요")
                keepOnFailure && nextState is DealFeedUiState.Empty -> Unit
                else -> {
                    _uiState.value = nextState
                    // 화면이 이번 요청의 결과를 실제로 받아들였을 때만(성공 또는
                    // 빈 결과) "화면이 지금 어떤 조건을 보여주고 있는지"를 갱신한다.
                    // Error는 여기서 제외한다 — Error는 이전 화면을 그대로 둔 채
                    // 끝나는 게 아니라(그건 위 keepOnFailure 분기의 일) 새로 Error
                    // 화면을 보여주는 것이므로, 이 요청이 물어본 조건을 "화면이 지금
                    // 보여주는 것"으로 기록하면 안 된다 — 화면에는 아무 데이터도 없다.
                    if (nextState is DealFeedUiState.Success || nextState is DealFeedUiState.Empty) {
                        loadedTripType = requestedTripType
                        loadedOrigin = requestedOrigin
                    }
                }
            }
        }
    }

    /**
     * 견적 자체가 말하는 여정 종류로 등록한다. 화면 토글([_tripType])을 읽지 않는다.
     *
     * `_tripType`은 토글을 누른 즉시 바뀌지만, "이미 보여줄 데이터가 있으면 Loading으로
     * 되돌리지 않는다" 정책 때문에 목록은 새 조회가 끝날 때까지 이전 종류 그대로 화면에
     * 남는다. 그 사이 사용자가 카드를 눌러 추적을 시작하면, 그 카드가 들고 있는
     * [PriceQuote]는 이전 조회 결과인데 [_tripType]은 이미 다음 값이라 서로 어긋난다.
     * 이 상태에서 화면 토글을 기준으로 등록하면, 왕복 견적이 편도로(또는 그 반대로)
     * 저장되어 `returnDate`와 `tripType`이 서로 모순된 행이 만들어지고, 이후 폴링이
     * 요청하는 종류와 등록된 종류가 달라 스냅샷이 영원히 쌓이지 않는다.
     * `returnDate`의 유무는 조회 순간과 무관하게 이 견적이 실제로 왕복인지 편도인지를
     * 그대로 말해주는 값이라 여기서는 이것을 유일한 근거로 삼는다.
     */
    fun track(item: DealItem) {
        viewModelScope.launch {
            try {
                val registration = trackRoute(item.quote, item.quote.impliedTripType())
                _messages.tryEmit(if (registration.isNew) "추적을 시작했어요" else "이미 추적 중이에요")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 실패해도 로그에만 남기고 끝나면 사용자는 버튼을 누른 뒤 성공 여부를
                // 영영 알 수 없다.
                Log.e(TAG, "추적 등록 실패", e)
                _messages.tryEmit("추적을 시작하지 못했어요")
            }
        }
    }

    /**
     * 예약 페이지를 열 브라우저가 기기에 없을 때 화면이 부른다.
     *
     * [BookingLauncher]는 Composable이 아니라 이 SharedFlow에 직접 emit할 수 없어
     * 화면이 대신 호출해준다. 같은 [_messages]를 쓰므로 위 [track]의 안내와 동일하게
     * 사용자가 화면을 안 보고 있으면 조용히 버려진다 — 예약 실패 안내는 지금 누른
     * 결과이지, 나중에 돌아왔을 때 뜬금없이 뜰 이유가 없다.
     */
    fun bookingUnavailable() {
        _messages.tryEmit("예약 페이지를 열 수 있는 앱이 없어요")
    }

    private companion object {
        const val TAG = "DealFeed"
    }
}

/**
 * [PriceQuote]가 실제로 어떤 여정 종류인지: 귀국일이 있으면 왕복, 없으면 편도.
 *
 * 화면 상태(토글, `_tripType`)는 사용자가 탭한 순간 바뀌지만 그 값이 가리키는
 * 데이터가 아직 화면에 도착하지 않았을 수 있다. 반면 `returnDate`는 그 견적을
 * 만들어낸 실제 조회의 결과이므로 언제 읽어도 어긋나지 않는다 — 추적 등록처럼
 * "이 견적이 진짜로 무엇인지"가 중요한 곳에서는 화면이 아니라 이 값을 근거로 삼는다.
 */
private fun PriceQuote.impliedTripType(): TripType =
    if (returnDate != null) TripType.ROUND_TRIP else TripType.ONE_WAY
