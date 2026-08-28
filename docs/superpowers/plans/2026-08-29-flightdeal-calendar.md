# 날짜별 최저가 캘린더 구현 계획

**Goal:** 목적지와 달을 고르면 그 달의 날짜별 최저가가 달력 격자로 보이고,
날짜를 누르면 그 항공권의 예약 페이지가 열린다. 지금 비어 있는 **검색** 탭을 채운다.

**Architecture:** `:domain`에 달력 한 장을 만드는 UseCase를 두고, 화면은 그 결과를 그린다.
격자 좌표 계산(1일이 무슨 요일에서 시작하는지, 빈 칸이 몇 개인지)은 Compose를 모르는
순수 함수로 분리해 JVM 테스트로 고정한다.

---

## 먼저 읽을 것 — 이 기능의 가장 큰 함정

`FlightPriceRepository.calendarPrices`는 **일부러** 예약처로 거르지 않는다.
구현에 그렇게 적혀 있다:

> `// 캘린더는 그날의 최저가를 보여주는 화면이다. 예약처로 거르지 않는다.`

그 선택은 **통계로서는 옳다.** `priceStats`가 그 위에 얹혀 있고, 할인 배지의 기준선은
시장 전체의 분포여야 한다. 한국에서 결제 가능한 것만 남긴 분포를 기준으로 삼으면
"시장 대비 얼마나 싼가"라는 질문의 답이 달라진다.

**하지만 화면으로서는 틀리다.** 사용자가 눌러서 예약하는 화면이 결제할 수 없는
러시아 마켓플레이스 가격을 보여주면, 같은 날짜인데 딜 피드와 캘린더의 숫자가 다르고,
눌러 들어가면 또 다른 값이 뜬다.

이 프로젝트는 이미 이 계열의 결함을 네 번 겪었다 — 등록 기준가와 폴링이 다른 규칙으로
가격을 골라 첫 실행마다 가짜 하락을 알렸고, 왕복 가격을 편도 분포와 견줘 배지가 영영
안 떴다. **"이 항공권 얼마인가"에 답하는 화면은 전부 같은 규칙을 써야 한다.**

그래서 이 계획은 **`calendarPrices`를 바꾸지 않는다.** 통계는 그대로 두고,
화면이 쓸 `calendarDeals`를 따로 만든다. 둘의 용도를 KDoc에 분명히 적는다.

## Global Constraints

- Java 소스 파일 금지 — `.java` 파일이 0개여야 한다
- `:domain`은 아무것도 의존하지 않는다. 안드로이드 타입 금지, "Travelpayouts" 문자열 금지
- `:data`는 `:domain`만 의존한다
- 비동기는 Coroutines/Flow. RxJava·LiveData 금지. KSP만, kapt 금지
- 가격은 `Won` value class
- `AppResult.Empty`는 오류가 아니다 — 한산한 노선은 정상적으로 빈 응답을 준다
- **새 의존성 금지**
- 주석은 한국어로, **왜**를 적을 것
- 커밋에 Co-Authored-By나 Claude/AI 트레일러 금지

---

## Task 1: 도메인 — 목적지 목록과 달력 한 장

**Files:**
- Modify: `domain/.../model/Airport.kt`
- Modify: `domain/.../repository/FlightPriceRepository.kt`
- Create: `domain/.../model/MonthCalendar.kt`
- Create: `domain/.../usecase/GetMonthCalendarUseCase.kt`
- Modify: `data/.../remote/TravelpayoutsFlightPriceRepository.kt`, `data/.../fake/FakeFlightPriceRepository.kt`
- Test: `domain/src/test/.../usecase/GetMonthCalendarUseCaseTest.kt`

- [ ] **Step 1: 목적지 목록을 도메인으로 옮긴다**

지금 `DEFAULT_DESTINATIONS`(`"TYO", "BKK", "DAD", "TPE", "HKG", "SIN"`)는 `:data`의
`TravelpayoutsFlightPriceRepository` 안에 있다. 화면이 목적지를 고르게 하려면
`:presentation`이 그 목록을 알아야 하는데, `:presentation`은 `:data`의 내부를 봐서는 안 된다.

**이 앱이 어느 노선을 다루는가는 데이터 소스의 사정이 아니라 제품의 결정이다.**
`Airport.kt`에 둔다:

```kotlin
    companion object {
        val INCHEON = Airport("ICN", "서울", "대한민국")

        /**
         * 이 앱이 다루는 인천 출발 목적지.
         *
         * 데이터 소스가 아니라 제품이 정하는 목록이라 도메인에 둔다.
         * `:data`의 조회도, 캘린더 화면의 선택지도 여기 하나를 본다 —
         * 두 군데에 두면 화면에는 있는데 조회는 안 되는 목적지가 생긴다.
         */
        val DESTINATIONS = listOf(
            Airport("TYO", "도쿄", "일본"),
            Airport("BKK", "방콕", "태국"),
            Airport("DAD", "다낭", "베트남"),
            Airport("TPE", "타이베이", "대만"),
            Airport("HKG", "홍콩", "홍콩"),
            Airport("SIN", "싱가포르", "싱가포르"),
        )
    }
```

`TravelpayoutsFlightPriceRepository.DEFAULT_DESTINATIONS`를 지우고
`Airport.DESTINATIONS.map { it.iata }`를 쓰게 고친다. **목록이 두 곳에 남으면 안 된다.**

- [ ] **Step 2: 화면용 조회를 더한다**

`FlightPriceRepository`에:

```kotlin
    /**
     * 캘린더 화면이 쓰는, 날짜별 **예약 가능한** 최저가.
     *
     * [calendarPrices]와 다르다. 저쪽은 할인율 기준선을 만드는 통계용이라 예약처로
     * 거르지 않은 시장 전체의 분포를 준다. 이쪽은 사용자가 눌러서 결제할 화면이므로
     * 딜 피드·가격 추적과 **같은 예약처 규칙**을 쓴다. 규칙이 갈리면 같은 날짜인데
     * 화면마다 다른 숫자가 뜬다.
     *
     * 날짜당 하나씩, 출발일 오름차순.
     */
    suspend fun calendarDeals(
        route: Route,
        month: YearMonth,
        tripType: TripType,
    ): AppResult<List<PriceQuote>>
```

`TravelpayoutsFlightPriceRepository` 구현:

```kotlin
    override suspend fun calendarDeals(
        route: Route,
        month: YearMonth,
        tripType: TripType,
    ): AppResult<List<PriceQuote>> = call {
        fetch(route.origin.iata, route.destination.iata, month, tripType)
            .groupBy { it.quote.departDate }
            .mapNotNull { (_, quotes) ->
                // 딜 피드·추적과 같은 규칙이다. 여기서 갈리면 화면마다 값이 달라진다.
                GatePolicy.prioritize(quotes, { it.gate }, minCount = 1).firstOrNull()?.quote
            }
            .sortedBy { it.departDate }
    }

```

`FakeFlightPriceRepository`에도 구현한다.

- [ ] **Step 3: 달력 한 장 모델**

```kotlin
/**
 * @param byDate 날짜별 최저가. 값이 없는 날은 키가 없다 — 한산한 날은 정상적으로 비어 있다.
 * @param cheapestDate 그 달에서 가장 싼 날. 값이 하나도 없으면 null.
 * @param median 그 달 분포의 중앙값. 싼 날을 강조하는 기준이다.
 */
data class MonthCalendar(
    val month: YearMonth,
    val byDate: Map<LocalDate, PriceQuote>,
    val cheapestDate: LocalDate?,
    val median: Won?,
)
```

- [ ] **Step 4: 실패하는 테스트를 먼저 쓴다**

```kotlin
    @Test
    fun `날짜마다 가장 싼 값 하나만 남는다`() { ... }

    @Test
    fun `가장 싼 날을 찾는다`() { ... }

    @Test
    fun `값이 하나도 없으면 Empty다`() {
        // 빈 응답은 오류가 아니다. 한산한 노선은 정상적으로 아무것도 주지 않는다.
        ...
        assertEquals(AppResult.Empty, result)
    }

    @Test
    fun `네트워크 오류는 그대로 전달한다`() { ... }
```

`median`은 기존 `PriceStats`를 재사용한다 — **중앙값 계산을 새로 쓰지 마라.**

- [ ] **Step 5: 구현하고 통과를 확인한다**

- [ ] **Step 6: 커밋** — `feat: 날짜별 예약 가능한 최저가 조회 추가`

---

## Task 2: 격자 좌표 (순수 함수)

**Files:**
- Create: `presentation/.../calendar/MonthGrid.kt`
- Test: `presentation/src/test/.../calendar/MonthGridTest.kt`

**안드로이드 import가 하나도 없어야 한다.**

한 달을 7칸씩 줄로 끊어 놓으려면 1일 앞에 빈 칸이 몇 개 필요한지 알아야 한다.
이건 화면 없이 검증할 수 있고, 조용히 틀리면 달력 전체가 하루씩 밀린다.

```kotlin
/** 달력 한 칸. [date]가 null이면 1일 앞 또는 말일 뒤의 빈 칸이다. */
data class GridCell(val date: LocalDate?)

object MonthGrid {
    /** 월요일 시작. 한국 달력은 일요일 시작도 쓰지만 이 앱은 월요일로 고정한다. */
    fun cellsOf(month: YearMonth): List<GridCell>
}
```

- [ ] **Step 1: 테스트를 먼저 쓴다**

```kotlin
    @Test
    fun `1일이 무슨 요일이든 그 자리에서 시작한다`() {
        // 2026-10-01은 목요일이다. 월요일 시작이면 앞에 빈 칸 3개.
        val cells = MonthGrid.cellsOf(YearMonth.of(2026, 10))

        assertEquals(3, cells.take(3).count { it.date == null })
        assertEquals(LocalDate.of(2026, 10, 1), cells[3].date)
    }

    @Test
    fun `1일이 월요일이면 빈 칸이 없다`() {
        // 2026-06-01은 월요일이다.
        val cells = MonthGrid.cellsOf(YearMonth.of(2026, 6))

        assertEquals(LocalDate.of(2026, 6, 1), cells.first().date)
    }

    @Test
    fun `칸 수는 항상 7의 배수다`() {
        // 줄이 안 맞으면 마지막 줄이 깨진다.
        listOf(YearMonth.of(2026, 2), YearMonth.of(2026, 10), YearMonth.of(2024, 2))
            .forEach { assertEquals(0, MonthGrid.cellsOf(it).size % 7) }
    }

    @Test
    fun `그 달의 모든 날이 한 번씩 들어간다`() {
        val cells = MonthGrid.cellsOf(YearMonth.of(2026, 10))

        assertEquals(31, cells.count { it.date != null })
        assertEquals(31, cells.mapNotNull { it.date }.distinct().size)
    }
```

- [ ] **Step 2: 실패 확인 → 구현 → 통과 확인**

- [ ] **Step 3: 커밋** — `feat: 달력 격자 좌표 계산 추가`

---

## Task 3: 캘린더 화면

**Files:**
- Create: `presentation/.../calendar/CalendarScreen.kt`, `CalendarViewModel.kt`, `CalendarUiState.kt`, `DayCell.kt`
- Modify: `presentation/.../ui/FlightDealNavHost.kt`
- Test: `presentation/src/test/.../calendar/CalendarViewModelTest.kt`

- [ ] **Step 1: UI 상태**

딜 피드와 같은 네 상태를 쓴다 — `Loading` / `Empty` / `Success` / `Error`.
**`Empty`에는 재시도 버튼을 두지 마라.** 눌러도 결과가 같다. 딜 피드가 이미 그렇게 한다.

- [ ] **Step 2: ViewModel**

- 목적지 기본값은 `Airport.DESTINATIONS.first()` (도쿄)
- 달 기본값은 이번 달
- 왕복/편도는 딜 피드와 같은 토글을 둔다. **기본값을 딜 피드와 같게 할 것** —
  다르면 같은 날짜에 화면마다 다른 값이 뜬다
- 목적지·달·종류가 바뀌면 다시 조회한다. 이전 요청은 취소한다
  (`DealFeedViewModel`의 재진입 처리를 읽고 같은 방식을 쓸 것)

테스트:
```kotlin
    @Test fun `목적지를 바꾸면 다시 조회한다`()
    @Test fun `값이 없으면 Empty다`()
    @Test fun `조회 중이던 요청은 취소한다`()
```

- [ ] **Step 3: 화면**

- 상단: 목적지 칩 가로 스크롤 (`Airport.DESTINATIONS`), 선택된 것은 `Indigo`
- 그 아래: `‹ 2026년 10월 ›` 달 이동. **과거 달로는 못 가게 한다** — 지난 날짜는
  소스가 아무것도 주지 않아 빈 달력만 나온다
- 요일 머리글: 월 화 수 목 금 토 일
- 격자: `MonthGrid.cellsOf(month)`를 7칸씩. 각 칸에 날짜와 가격
  - 가격은 만원 단위로 줄여 쓴다 (`304,619원` → `30.5만`). 원 단위를 다 쓰면 칸을 넘는다
  - 중앙값 이하인 날은 `IndigoSubtle` 배경
  - `cheapestDate`는 `Indigo` 배경에 흰 글자
  - 값이 없는 날은 날짜만 `TextSecondary`로, 누를 수 없게
  - 오늘 이전 날짜는 흐리게, 누를 수 없게
- 칸을 누르면 그 견적의 `deepLink`를 `BookingLauncher.open`으로 연다.
  **`deepLink`가 null이면 누를 수 없어야 한다** — 딜 카드가 이미 그 규칙을 쓴다

- [ ] **Step 4: 검색 탭에 연결한다**

`FlightDealNavHost`의 `composable(Tab.Search.route) { PlaceholderScreen() }`를
`CalendarScreen()`으로 바꾼다. 탭 이름 `검색`이 하는 일과 맞는지 보고,
안 맞으면 `달력`으로 바꾼다.

- [ ] **Step 5: 빌드·테스트·기기 확인**

`emulator-5554`에 설치하고 검색 탭에서 목적지를 바꿔 가며 값이 실제로 바뀌는지,
가장 싼 날이 강조되는지, 날짜를 눌러 예약 페이지가 열리는지 확인한다.

**딜 피드에서 같은 목적지·같은 날짜의 가격과 캘린더의 값이 일치하는지 반드시 대조할 것.**
다르면 선택 규칙이 갈린 것이고, 이 계획서 맨 위가 막으려던 바로 그 결함이다.

스크린샷을 남긴다.

- [ ] **Step 6: 커밋** — `feat: 날짜별 최저가 캘린더 화면 추가`
