# 캘린더 두 가지 보완

**Goal:** 캘린더를 처음 열었을 때 쓸모 있는 달이 보이게 하고, 비어 있는 칸이
무엇 때문에 비었는지 구별되게 한다.

---

## 문제 1 — 처음 열면 거의 빈 격자가 나온다

`CalendarViewModel`은 `YearMonth.now(clock)`을 기본값으로 쓴다. 오늘이 8월 29일이면
남은 날이 이틀뿐이라 **격자가 사실상 비어 있다.** 값이 없는 게 아니라 지난 날짜라서인데,
처음 여는 사용자에게는 "이 앱은 가격 정보가 없구나"로 읽힌다.

딜 피드는 이미 다른 답을 갖고 있다 — `TravelpayoutsFlightPriceRepository`의
`LEAD_MONTHS = 2`, 즉 두 달 뒤를 본다. 그게 이 데이터 소스에서 값이 실제로 차 있는
구간이다. **캘린더도 같은 달에서 시작해야 한다.**

같은 달을 보게 하면 부수 효과가 하나 더 있다. 지금은 딜 피드와 캘린더가 서로 다른 달을
보여줘서, 같은 노선의 가격을 대조하려 하면 값이 안 맞는다 — 이 프로젝트를 검증하던
중에도 실제로 한 번 혼동을 일으켰다.

`LEAD_MONTHS`는 지금 `:data`의 `private const`다. 화면이 그 값을 알아야 하므로
**`:domain`으로 옮긴다.** "언제쯤 항공권을 보는가"는 데이터 소스의 사정이 아니라
제품의 결정이다. 두 곳에 두면 한쪽만 바뀌어 화면과 조회가 어긋난다.

## 문제 2 — 비어 있는 칸이 두 가지 뜻을 갖는다

`calendarDeals`는 예약처 규칙을 달 전체에 적용한 뒤 날짜별로 최저가를 고른다.
그래서 **한국에서 결제할 수 있는 예약처가 하나도 없던 날은 아예 빠진다.**
화면에서는 애초에 값이 없던 날과 똑같이 빈 칸이다.

격자 아래 캡션이 그걸 설명하지만, 칸 하나만 보면 어느 쪽인지 알 수 없다.
**앱은 둘을 구별해서 알고 있으면서 화면에서는 합쳐 버린다.**

## Global Constraints

- Java 소스 파일 금지 — `.java` 파일이 0개여야 한다
- `:domain`은 아무것도 의존하지 않는다 — 안드로이드 타입도 "Travelpayouts" 문자열도,
  "gate"·"예약처 이름" 같은 데이터 소스 개념도 등장하지 않는다. `:data`는 `:domain`만 의존
- 비동기는 Coroutines/Flow. RxJava·LiveData 금지. KSP만, kapt 금지
- 가격은 `Won` value class
- `AppResult.Empty`는 오류가 아니다
- **새 의존성 금지**
- 색은 `FlightDealTheme.colors`로 읽는다
- **`java.time`의 새 메서드를 쓸 때는 도입 API 레벨을 확인할 것.** `:domain`은 순수 JVM
  모듈이라 안드로이드 린트가 보지 않고 테스트는 JDK에서 돈다 — `LocalDate.ofInstant`(API 34)를
  써서 워커가 안드로이드 13 이하 전 기기에서 죽은 적이 있다. `Clock`을 받는 팩토리를 쓸 것
- 주석은 한국어로, **왜**를 적을 것
- 커밋에 Co-Authored-By나 Claude/AI 트레일러 금지

---

## Task 1: 조회 시점을 한 곳에서 정한다

**Files:**
- Modify: `domain/.../model/` (상수 자리는 판단에 맡긴다 — `Airport.DESTINATIONS` 옆이 자연스럽다)
- Modify: `data/.../remote/TravelpayoutsFlightPriceRepository.kt`
- Modify: `presentation/.../calendar/CalendarViewModel.kt`
- Test: `presentation/src/test/.../calendar/CalendarViewModelTest.kt`

- [ ] **Step 1: 상수를 도메인으로**

```kotlin
/**
 * 지금으로부터 몇 달 뒤를 기본으로 보여줄지.
 *
 * 데이터 소스가 실사용자 검색 기록 캐시라 가까운 날짜는 듬성듬성하고 두 달쯤 뒤가
 * 가장 촘촘하다. 딜 피드의 조회와 캘린더의 첫 화면이 **같은 값을 봐야 한다** —
 * 다르면 같은 노선인데 두 화면의 가격이 안 맞고, 사용자는 어느 쪽이 맞는지 알 수 없다.
 */
const val DEFAULT_LEAD_MONTHS = 2L
```

`TravelpayoutsFlightPriceRepository`의 `private const val LEAD_MONTHS`를 지우고 이것을 쓴다.
**두 곳에 남기지 마라.**

- [ ] **Step 2: 캘린더 기본 달**

`CalendarViewModel`의 `_month` 초기값을 `YearMonth.now(clock).plusMonths(DEFAULT_LEAD_MONTHS)`로.

`canGoToPreviousMonth`는 지금처럼 이번 달까지 내려갈 수 있게 둔다 — 사용자가 더 가까운
날짜를 보고 싶어할 수 있고, 그 달이 듬성듬성한 것은 사실 그대로다.

- [ ] **Step 3: 테스트**

```kotlin
    @Test
    fun `처음 열면 딜 피드와 같은 달을 본다`() {
        // 이번 달로 열면 월말에 격자가 거의 비어 "가격 정보가 없는 앱"으로 보인다.
        val clock = Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC)
        ...
        assertEquals(YearMonth.of(2026, 10), viewModel.month.value)
    }

    @Test
    fun `이번 달까지는 되돌아갈 수 있다`() { ... }
```

- [ ] **Step 4: 커밋** — `fix: 캘린더가 딜 피드와 같은 달에서 시작한다`

---

## Task 2: 예약 불가로 비운 날을 구별해서 보여준다

**Files:**
- Modify: `domain/.../model/MonthCalendar.kt`, `domain/.../repository/FlightPriceRepository.kt`
- Create: `domain/.../model/CalendarDeals.kt` (이름은 판단에 맡긴다)
- Modify: `domain/.../usecase/GetMonthCalendarUseCase.kt`
- Modify: `data/.../remote/TravelpayoutsFlightPriceRepository.kt`, `data/.../fake/FakeFlightPriceRepository.kt`
- Modify: `presentation/.../calendar/DayCell.kt`, `CalendarScreen.kt`
- Test: `:data`, `:domain` 양쪽

- [ ] **Step 1: 조회가 두 가지를 함께 돌려준다**

`calendarDeals`의 반환 타입을 바꾼다:

```kotlin
/**
 * @param deals 날짜당 하나씩, 한국에서 예약 가능한 최저가. 출발일 오름차순.
 * @param unbookableDates 가격은 있었지만 **한국에서 예약할 수 있는 곳이 하나도 없던** 날.
 *   화면이 "값이 없는 날"과 구별해서 보여줄 수 있어야 한다 — 둘은 사용자에게 다른 뜻이다.
 */
data class CalendarDeals(
    val deals: List<PriceQuote>,
    val unbookableDates: Set<LocalDate>,
)
```

**도메인에 "예약처"나 "gate"라는 개념을 들이지 마라.** `unbookableDates`는 "이 날은
살 수 없다"는 사실만 말하고, 왜 그런지는 `:data`의 사정이다.

`:data` 구현은 이미 두 정보를 다 갖고 있다 — 달 전체에 `GatePolicy`를 적용한 결과와
원래 응답의 날짜 집합의 차집합이 곧 `unbookableDates`다. **새 요청을 보내지 마라.**

- [ ] **Step 2: `MonthCalendar`에 싣는다**

```kotlin
    /** 가격은 있었지만 한국에서 예약할 수 있는 곳이 없던 날. 빈 날과 구별해 표시한다. */
    val unbookableDates: Set<LocalDate>,
```

`GetMonthCalendarUseCase`는 이것도 요청한 달로 걸러야 한다 — `byDate`를 거르는 것과
같은 이유다.

**`cheapestDate`와 `median`은 `deals`만으로 계산한다.** 살 수 없는 가격이 중앙값을
움직이면 배지와 강조가 살 수 없는 값에 끌려간다.

- [ ] **Step 3: 테스트**

`:data`:
```kotlin
    @Test fun `예약 가능한 곳이 없던 날은 unbookableDates에 담긴다`()
    @Test fun `예약 가능한 날은 unbookableDates에 들어가지 않는다`()
```
`:domain`:
```kotlin
    @Test fun `중앙값은 예약 가능한 가격만으로 계산한다`()
    @Test fun `요청한 달 밖의 unbookable 날짜는 걸러낸다`()
```

각각 수정 전에 실패하는 것을 확인할 것.

- [ ] **Step 4: 화면**

`DayCell`에 세 번째 상태를 만든다:
- 값이 있는 날 — 지금 그대로
- **예약 불가인 날** — 날짜는 `TextSecondary`로, 가격 자리에 `—`를 `TextSecondary` 11.sp로.
  **누를 수 없다**
- 값이 없는 날 — 지금 그대로 (날짜만)

격자 아래 캡션 문구를 바꾼다. 지금 "한국에서 예약할 수 없는 예약처만 있는 날은
비워둬요"인데, 이제 비우지 않으므로 사실과 다르다 —
"한국에서 예약할 수 없는 날은 —로 표시해요" 정도로.

- [ ] **Step 5: 빌드·테스트·기기 확인**

`emulator-5554`에 설치하고 검색(달력) 탭을 연다.

1. **처음 열었을 때 두 달 뒤 달이 나오고 격자가 차 있는지**
2. `—`로 표시된 날이 실제로 있는지 — 리뷰 측정으로는 편도 31일 중 17일이 그랬다.
   목적지와 왕복/편도를 바꿔가며 확인한다
3. `—` 칸을 눌러도 아무 일도 일어나지 않는지 (예약 페이지가 열리면 안 된다)
4. 딜 피드의 도쿄 가격과 캘린더 같은 날짜의 값이 **일치하는지** 대조한다.
   이제 두 화면이 같은 달을 보므로 바로 비교된다

스크린샷을 남긴다. 라이트·다크 둘 다.

- [ ] **Step 6: 커밋** — `feat: 예약할 수 없는 날을 빈 칸과 구별해 표시한다`
