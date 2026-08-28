# 딜 피드가 사실대로 말하게 하기

**Goal:** 두 가지를 고친다 — 경유편을 직항처럼 보여주는 것, 그리고 새로고침이 실패하면
이미 보여주던 목록을 지워버리는 것.

**둘 다 화면이 실제보다 단순하게 말하는 문제다.**

## Global Constraints

- Java 소스 파일 금지 — `.java` 파일이 0개여야 한다
- `:domain`은 아무것도 의존하지 않는다. 안드로이드 타입 금지, "Travelpayouts" 문자열 금지
- `:data`는 `:domain`만 의존한다
- 비동기는 Coroutines/Flow. RxJava·LiveData 금지. KSP만, kapt 금지
- 가격은 `Won` value class
- `AppResult.Empty`는 오류가 아니다
- **새 의존성 금지**
- 주석은 한국어로, **왜**를 적을 것
- 커밋에 Co-Authored-By나 Claude/AI 트레일러 금지

---

## Task 1: 경유와 소요시간을 보여준다

**왜.** API는 `transfers`, `return_transfers`, `duration_to`를 **이미 주고 있고
`PriceDto`도 이미 받고 있다.** 매퍼가 버려서 도메인까지 오지 않을 뿐이다.
그 결과 2회 경유 20시간짜리와 직항 2시간 30분짜리가 카드에서 구별되지 않는다.
가격만 보고 누른 사용자는 예약 페이지에서야 그걸 안다.

실제 캡처를 확인했다 — 인천발 단거리 노선은 대부분 직항이라(왕복 44건 중 42건이
양쪽 직항) 경유가 흔하지는 않다. **드물기 때문에 더 위험하다.** 전부 직항인 줄 알고
보다가 한 건이 경유일 때 알아챌 방법이 없다.

**Files:**
- Modify: `domain/.../model/PriceQuote.kt`
- Modify: `data/.../remote/PriceQuoteMapper.kt`
- Modify: `presentation/.../feed/DealCard.kt`
- Test: `data/src/test/.../remote/PriceQuoteMapperTest.kt`, `presentation/src/test/.../feed/`

- [ ] **Step 1: 도메인 모델**

`PriceQuote`에 더한다:

```kotlin
    /**
     * 가는 편과 오는 편 중 **경유가 많은 쪽**의 횟수. 편도면 가는 편만 본다.
     *
     * 편마다 따로 보여주면 카드가 길어지고, 사용자가 카드에서 내리는 결정은
     * "이 딜을 열어볼까"뿐이다. 한쪽이라도 경유가 있으면 알아야 한다.
     * 정확한 편별 내역은 예약 페이지에 있다.
     */
    val transfers: Int?,

    /** 가는 편 비행 시간(분). 왕복의 `duration`은 두 편의 합이라 쓰지 않는다. */
    val outboundMinutes: Int?,
```

`null`은 "모른다"는 뜻이다. API가 필드를 빠뜨릴 수 있다.

- [ ] **Step 2: 매퍼 테스트를 먼저 쓴다**

```kotlin
    @Test
    fun `경유 횟수는 가는 편과 오는 편 중 많은 쪽이다`() {
        // 실제 응답에 있던 조합이다 — 가는 편 직항, 오는 편 1회 경유.
        val dto = priceDto(transfers = 0, returnTransfers = 1)

        assertEquals(1, PriceQuoteMapper.toDomain(dto, now, marker)!!.transfers)
    }

    @Test
    fun `편도면 오는 편 경유는 보지 않는다`() {
        // 편도 조회에는 return_at이 없다. return_transfers가 0으로 와도 의미가 없다.
        val dto = priceDto(transfers = 1, returnTransfers = 0, returnAt = null)

        assertEquals(1, PriceQuoteMapper.toDomain(dto, now, marker)!!.transfers)
    }

    @Test
    fun `왕복 duration이 아니라 duration_to를 쓴다`() {
        // 왕복 응답의 duration은 왕복 합계(300 = 150 + 150)다.
        // 그대로 쓰면 2시간 30분짜리가 5시간으로 보인다.
        val dto = priceDto(duration = 300, durationTo = 150, durationBack = 150)

        assertEquals(150, PriceQuoteMapper.toDomain(dto, now, marker)!!.outboundMinutes)
    }

    @Test
    fun `값이 없으면 null이다`() {
        val dto = priceDto(transfers = null, durationTo = null)
        val quote = PriceQuoteMapper.toDomain(dto, now, marker)!!

        assertNull(quote.transfers)
        assertNull(quote.outboundMinutes)
    }
```

**세 번째 테스트가 이 작업의 핵심이다.** 왕복 `duration`을 그대로 쓰면 모든 왕복
항공권의 비행 시간이 두 배로 표시된다. 실제 캡처에서 확인한 값이다
(`duration: 300, duration_to: 150, duration_back: 150`).

- [ ] **Step 3: 실패 확인 → 매퍼 구현 → 통과 확인**

- [ ] **Step 4: 카드에 표시한다**

`DealCard`의 항공사 줄 옆에 붙인다. 문구를 만드는 함수는 순수하게 두고 테스트한다:

```kotlin
/** 예: "직항 · 2시간 30분", "경유 1회 · 8시간 10분", 값을 모르면 null. */
fun itineraryLabel(transfers: Int?, outboundMinutes: Int?): String?
```

- 둘 다 null이면 null을 돌려주고 아무것도 그리지 않는다.
  **모르는 것을 "직항"이라고 말하면 안 된다.**
- `transfers == 0` → `"직항"`, `>= 1` → `"경유 ${n}회"`
- 분은 `"2시간 30분"` 꼴로. 60분 미만이면 `"50분"`, 정각이면 `"3시간"`
- 경유가 있으면 `TextSecondary`가 아니라 조금 더 눈에 띄게 — 다만
  `PriceUp`(빨강)은 쓰지 마라. 경유는 오류가 아니라 정보다

테스트:
```kotlin
    @Test fun `직항과 시간을 함께 적는다`()
    @Test fun `경유가 있으면 횟수를 적는다`()
    @Test fun `둘 다 모르면 아무것도 적지 않는다`()
    @Test fun `한 시간 미만은 분만 적는다`()
    @Test fun `정각이면 분을 적지 않는다`()
```

- [ ] **Step 5: 커밋** — `feat: 딜 카드에 경유 횟수와 비행 시간을 표시한다`

---

## Task 2: 새로고침이 실패해도 보던 목록을 지우지 않는다

**왜.** `DealFeedViewModel.refresh()`는 시작하자마자 `_uiState.value = Loading`을 넣고,
실패하면 `Error`로 덮는다. 이미 화면에 실제 가격이 떠 있었더라도 **전부 사라지고
오류 화면만 남는다.** 지하철에서 잠깐 신호가 끊기면 보고 있던 특가가 통째로 없어진다.

설계 문서 §8은 캐시를 유지하고 스낵바로 알리기를 요구한다. 스낵바 통로는 이미 있다 —
추적 피드백 작업에서 만든 `_messages` `Channel`을 그대로 쓴다.

**규칙 하나로 정리된다: 화면은 데이터를 보여주던 상태에서 뒤로 가지 않는다.**

**Files:**
- Modify: `presentation/.../feed/DealFeedViewModel.kt`
- Test: `presentation/src/test/.../feed/DealFeedViewModelTest.kt`

- [ ] **Step 1: 실패하는 테스트를 먼저 쓴다**

```kotlin
    @Test
    fun `새로고침이 실패해도 보던 목록을 지우지 않는다`() = runTest {
        // 첫 조회는 성공해서 목록이 떠 있다.
        ...
        assertTrue(viewModel.uiState.value is DealFeedUiState.Success)

        // 두 번째 조회가 네트워크 오류로 실패한다.
        repository.nextResult = AppResult.NetworkError(IOException())
        viewModel.refresh()
        advanceUntilIdle()

        // 목록은 그대로 남아야 한다. 오류는 스낵바로만 알린다.
        assertTrue(viewModel.uiState.value is DealFeedUiState.Success)
    }

    @Test
    fun `첫 조회가 실패하면 오류 화면을 보여준다`() = runTest {
        // 보여줄 것이 없으면 오류 화면이 맞다. 빈 화면보다 낫다.
        repository.nextResult = AppResult.NetworkError(IOException())
        ...
        assertTrue(viewModel.uiState.value is DealFeedUiState.Error)
    }

    @Test
    fun `목록이 떠 있으면 새로고침 중에 Loading으로 되돌리지 않는다`() = runTest {
        // Loading으로 바꾸면 스켈레톤이 떴다가 목록이 돌아온다 — 깜빡인다.
        ...
    }

    @Test
    fun `실패를 스낵바로 알린다`() = runTest {
        // 목록을 남겨두기만 하고 아무 말도 안 하면 사용자는 갱신된 줄 안다.
        ...
        assertEquals("가격을 새로 받아오지 못했어요", messages.awaitItem())
    }
```

**네 번째 테스트를 빠뜨리지 마라.** 목록을 유지하기만 하고 알리지 않으면
"오래된 값을 최신인 것처럼 보여주는" 더 나쁜 상태가 된다.

- [ ] **Step 2: 실패 확인 → 구현**

```kotlin
    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val hadData = _uiState.value is DealFeedUiState.Success

            // 보여줄 게 이미 있으면 스켈레톤으로 되돌리지 않는다. 깜빡이기만 한다.
            if (!hadData) _uiState.value = DealFeedUiState.Loading
            ...
```

오류 분기에서 `hadData`면 상태를 그대로 두고 `_messages.send(...)`만 한다.
`Empty`도 마찬가지로 다룰지 판단할 것 — **빈 응답은 오류가 아니므로
목록이 있었다면 유지하고, 없었다면 `Empty` 화면이 맞다.**

- [ ] **Step 3: 화면 확인**

`DealFeedScreen`은 이미 `messages`를 스낵바로 보여준다. 새로 붙일 것은 없지만
**실제로 뜨는지 확인할 것.**

- [ ] **Step 4: 기기 확인**

`emulator-5554`에서 딜 피드를 띄운 뒤 비행기 모드로 네트워크를 끊고
(`adb -s emulator-5554 shell svc wifi disable && adb -s emulator-5554 shell svc data disable`)
새로고침을 시도한다. **목록이 남아 있고 스낵바가 뜨는지** 확인하고 스크린샷을 남긴다.
끝나면 네트워크를 되돌린다.

- [ ] **Step 5: 커밋** — `fix: 새로고침 실패가 보던 목록을 지우던 문제 수정`
