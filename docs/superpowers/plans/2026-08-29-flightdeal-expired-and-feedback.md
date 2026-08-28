# 지난 여정 처리와 추적 피드백 구현 계획

**Goal:** 출발일이 지난 추적 항목이 조용히 망가진 채 남아 있지 않게 하고,
추적 버튼을 눌렀을 때 무슨 일이 일어났는지 사용자가 알 수 있게 한다.

**두 문제 다 "앱이 사용자에게 아무 말도 하지 않는" 종류다.**

**문제 1 — 지난 여정.** `CheckTrackedPricesUseCase`는 출발일을 확인하지 않는다.
10월 10일 출발 항공권을 추적하다가 그 날이 지나면, 워커는 6시간마다 계속 그 달을
조회한다. API는 지난 날짜에 아무것도 주지 않으므로 `Empty`가 돌아오고, 카드는 마지막
가격에 얼어붙은 채 영원히 남는다. **사용자는 그게 최신 가격인 줄 안다.**
API 쿼터도 계속 쓴다.

**문제 2 — 추적 피드백.** `DealFeedViewModel.track()`은 실행하고 끝난다.
실패해도 로그에만 남는다. 사용자는 버튼을 누른 뒤 추적 탭으로 건너가 보기 전까지
성공했는지 알 수 없고, 실패했다면 영영 모른다.

## Global Constraints

- Java 소스 파일 금지 — `.java` 파일이 0개여야 한다
- `:domain`은 아무것도 의존하지 않는다. 안드로이드 타입 금지. `:data`는 `:domain`만 의존
- 비동기는 Coroutines/Flow. RxJava·LiveData 금지. KSP만, kapt 금지
- 가격은 `Won` value class
- **새 의존성 금지**
- 주석은 한국어로, **왜**를 적을 것
- 커밋에 Co-Authored-By나 Claude/AI 트레일러 금지

---

## Task 1: 출발일이 지난 여정은 조회하지 않는다

**Files:**
- Modify: `domain/.../model/TrackedRoute.kt`
- Modify: `domain/.../usecase/CheckTrackedPricesUseCase.kt`
- Modify: `presentation/.../tracking/TrackingUiState.kt`, `TrackingViewModel.kt`, `TrackedRouteCard.kt`
- Test: `domain/src/test/.../usecase/CheckTrackedPricesUseCaseTest.kt`

- [ ] **Step 1: 도메인에 판정을 둔다**

```kotlin
    /**
     * 출발일이 지났는가. 지난 여정은 더 조회하지 않는다 —
     * 소스가 지난 날짜에 아무것도 주지 않아 매번 헛돌고, 화면에는 마지막 가격이
     * 최신인 것처럼 남는다.
     */
    fun hasDeparted(today: LocalDate): Boolean = departDate.isBefore(today)
```

**지우지는 않는다.** 사용자가 등록한 항목이고 모아둔 이력이 붙어 있다.
조회를 멈추고 화면에서 지난 여정임을 밝히는 것까지가 이 작업이다.

- [ ] **Step 2: 실패하는 테스트를 먼저 쓴다**

`CheckTrackedPricesUseCaseTest`에:

```kotlin
    @Test
    fun `출발일이 지난 여정은 조회하지 않는다`() = runTest {
        // 지난 날짜를 계속 조회하면 API 쿼터만 쓰고 매번 빈 응답이 온다.
        val clock = Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), ZoneOffset.UTC)
        // departDate = 2026-10-10 인 추적 항목 하나만 등록해 둔다
        ...
        val changes = useCase()

        assertTrue(changes.isEmpty())
        assertEquals(0, stubPrices.trackedPriceCalls, "지난 여정은 조회조차 하지 않아야 한다")
    }

    @Test
    fun `출발일이 오늘이면 아직 조회한다`() = runTest {
        // 당일 출발도 아직 탈 수 있다. 경계에서 하루 일찍 끊으면 안 된다.
        ...
        assertEquals(1, stubPrices.trackedPriceCalls)
    }
```

`StubPrices`에 호출 횟수 카운터가 없으면 더한다. **기존 단언은 바꾸지 마라.**

- [ ] **Step 3: 실패를 확인하고 구현한다**

`CheckTrackedPricesUseCase.invoke`에서 `getAll()` 결과를 거른다:

```kotlin
        val today = LocalDate.ofInstant(clock.instant(), clock.zone)
        return trackedRoutes.getAll()
            .filterNot { it.hasDeparted(today) }
            .mapNotNull { tracked -> check(tracked) }
```

`clock`은 이미 주입돼 있다.

- [ ] **Step 4: 화면에 지난 여정임을 밝힌다**

`TrackedItem`에 `val hasDeparted: Boolean`을 더하고, `TrackingViewModel`이
주입받은 `Clock`으로 계산해 채운다. **ViewModel에 `Clock`을 새로 주입해야 한다** —
`LocalDate.now()`를 직접 부르면 테스트에서 고정할 수 없다.

`TrackedRouteCard`는 `hasDeparted`일 때:
- 날짜 줄 뒤에 " · 지난 여정"을 `TextSecondary`로 붙인다
- 가격 글자를 `TextSecondary`로 낮춘다 (더는 최신값이 아니다)
- ▼/▲ 변동 표시를 감춘다 — 지난 여정의 변동은 의미가 없다

그래프는 그대로 둔다. 지나간 여정의 가격이 어떻게 움직였는지는 여전히 볼 만하다.

- [ ] **Step 5: 커밋** — `fix: 출발일이 지난 여정을 계속 조회하던 문제 수정`

---

## Task 2: 추적 버튼이 결과를 말한다

**Files:**
- Modify: `domain/.../repository/TrackedRouteRepository.kt`, `domain/.../usecase/TrackRouteUseCase.kt`
- Modify: `data/.../local/RoomTrackedRouteRepository.kt`, `data/.../fake/*` (구현체 전부)
- Modify: `presentation/.../feed/DealFeedViewModel.kt`, `DealFeedScreen.kt`
- Test: `domain/src/test/.../usecase/TrackRouteUseCaseTest.kt`

- [ ] **Step 1: 등록이 새것인지 알린다**

`TrackedRouteRepository.add`가 지금은 id만 돌려준다. 이미 추적 중이면 기존 id를
돌려주는데(`INSERT OR IGNORE`), 호출한 쪽은 새로 만들어진 것인지 알 수 없다.
안내 문구가 달라야 하므로 구분해서 돌려준다:

```kotlin
/** @param isNew 새로 만들어졌으면 true, 이미 추적 중이던 것이면 false. */
data class TrackRegistration(val id: Long, val isNew: Boolean)
```

`RoomTrackedRouteRepository.add`는 `dao.insert(entity)`가 `-1`이 아니면 `isNew = true`다.
그 정보가 이미 그 자리에 있다 — 버리지 말고 돌려주면 된다.

`TrackRouteUseCase`도 `TrackRegistration`을 돌려준다.

- [ ] **Step 2: 테스트**

```kotlin
    @Test
    fun `이미 추적 중이면 새것이 아니라고 알린다`() = runTest {
        val first = useCase(quote, TripType.ROUND_TRIP)
        val second = useCase(quote, TripType.ROUND_TRIP)

        assertTrue(first.isNew)
        assertFalse(second.isNew)
        assertEquals(first.id, second.id)
    }
```

- [ ] **Step 3: ViewModel이 일회성 이벤트를 낸다**

```kotlin
    /**
     * 일회성 안내. `StateFlow`로 두면 화면 회전 때 같은 메시지가 다시 뜬다.
     */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()
```

`track()`에서:
- 성공·새것 → `"추적을 시작했어요"`
- 성공·이미 있음 → `"이미 추적 중이에요"`
- 실패 → `"추적을 시작하지 못했어요"` (기존 `Log.e`는 남긴다)

`CancellationException`은 지금처럼 다시 던진다 — 메시지를 내지 않는다.

- [ ] **Step 4: 화면이 보여준다**

`DealFeedScreen`에 `SnackbarHostState`를 두고 `Scaffold`의 `snackbarHost`로 건다.
`LaunchedEffect(Unit) { viewModel.messages.collect { snackbarHostState.showSnackbar(it) } }`.

`FlightDealNavHost`에 이미 `Scaffold`가 있다. **중첩 `Scaffold`가 하단 탭과 겹치지
않는지 확인할 것** — 겹치면 `DealFeedScreen` 안에 `Box` + `SnackbarHost`를
`Alignment.BottomCenter`로 직접 두는 편이 낫다.

- [ ] **Step 5: 빌드·테스트·기기 확인**

`emulator-5554`에 설치하고 딜의 추적 버튼을 눌러 스낵바가 뜨는지, 같은 딜을 한 번 더
눌렀을 때 "이미 추적 중이에요"로 바뀌는지 확인한다. 스크린샷을 남긴다.

- [ ] **Step 6: 커밋** — `feat: 추적 버튼이 결과를 알려준다`
