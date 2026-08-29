# 목표가 설정 구현 계획

**Goal:** 추적 중인 여정에 목표가를 정하고, 바꾸고, 해제할 수 있게 한다.

**왜.** 목표가는 **이미 동작한다.** `TrackedRoute.targetPrice`가 있고,
`DetectPriceChangesUseCase`가 `reachedTarget = current <= target`을 판정하고,
알림이 " · 목표가 도달"을 붙이고, 추이 그래프가 목표가 선을 그린다.
**설정할 화면만 없어서 항상 null이다.** 만들어 둔 기능이 죽어 있다.

## Global Constraints

- Java 소스 파일 금지 — `.java` 파일이 0개여야 한다
- `:domain`은 아무것도 의존하지 않는다. 안드로이드 타입 금지. `:data`는 `:domain`만 의존
- 비동기는 Coroutines/Flow. RxJava·LiveData 금지. KSP만, kapt 금지
- 가격은 `Won` value class
- **새 의존성 금지**
- 색은 `FlightDealTheme.colors`로 읽는다
- **`java.time`의 새 메서드를 쓸 때는 도입 API 레벨을 확인할 것** — `:domain`은 순수 JVM
  모듈이라 린트가 보지 않고 테스트는 JDK에서 돈다. `LocalDate.ofInstant`(API 34)로
  워커가 안드로이드 13 이하 전 기기에서 죽은 적이 있다
- 주석은 한국어로, **왜**를 적을 것
- 커밋에 Co-Authored-By나 Claude/AI 트레일러 금지

---

## Task 1: 입력 파싱 (순수 함수)

**Files:**
- Create: `presentation/.../tracking/TargetPriceInput.kt`
- Test: `presentation/src/test/.../tracking/TargetPriceInputTest.kt`

**안드로이드 import가 하나도 없어야 한다.**

```kotlin
/** 사용자가 친 문자열을 목표가로 바꾼다. 쓸 수 없는 입력이면 null. */
fun parseTargetPrice(text: String): Won?
```

**규칙:**

1. 숫자가 아닌 문자는 버린다. `"304,619원"` → `304619`. 사용자는 쉼표를 치기도 하고
   붙여넣기로 "원"이 딸려오기도 한다
2. 비었거나 0이면 null. 목표가 0원은 뜻이 없다
3. **`Int` 범위를 넘으면 null.** `Won.amount`가 `Int`라 자릿수를 계속 치면 넘친다.
   `toIntOrNull()`을 쓰면 조용히 null이 되지만, **그 전에 `Long`으로 받아 판정해야**
   "너무 큰 값"과 "쓸 수 없는 입력"을 구별할 수 있다. 여기서는 둘 다 null로 다루되
   **자릿수 제한을 입력 필드에서 걸어 애초에 못 치게 한다** (최대 9자리, 9억 원)

- [ ] **Step 1: 실패하는 테스트를 먼저 쓴다**

```kotlin
    @Test fun `쉼표와 원을 걷어낸다`() {
        assertEquals(Won(304_619), parseTargetPrice("304,619원"))
    }

    @Test fun `비었으면 null이다`() {
        assertNull(parseTargetPrice(""))
        assertNull(parseTargetPrice("   "))
    }

    @Test fun `0은 목표가가 될 수 없다`() {
        assertNull(parseTargetPrice("0"))
        assertNull(parseTargetPrice("0원"))
    }

    @Test fun `Int 범위를 넘으면 null이다`() {
        // Won.amount가 Int다. 자릿수를 계속 치면 넘친다.
        assertNull(parseTargetPrice("99999999999"))
    }

    @Test fun `숫자가 하나도 없으면 null이다`() {
        assertNull(parseTargetPrice("원"))
        assertNull(parseTargetPrice("abc"))
    }
```

- [ ] **Step 2: 실패 확인 → 구현 → 통과 확인**
- [ ] **Step 3: 커밋** — `feat: 목표가 입력 파싱 추가`

---

## Task 2: 저장 경로

**Files:**
- Modify: `domain/.../repository/TrackedRouteRepository.kt`
- Create: `domain/.../usecase/SetTargetPriceUseCase.kt`
- Modify: `data/.../local/TrackedRouteDao.kt`, `RoomTrackedRouteRepository.kt`
- Modify: `FlightPriceRepository` 구현체가 아니라 **`TrackedRouteRepository` 구현체 전부**
  (테스트 더블 포함 — `: TrackedRouteRepository`로 grep할 것)
- Test: `:data`의 Robolectric DAO 테스트, `:domain`의 UseCase 테스트

- [ ] **Step 1: 인터페이스**

```kotlin
    /** 목표가를 정하거나(null이면) 해제한다. */
    suspend fun setTargetPrice(id: Long, target: Won?)
```

DAO:
```kotlin
    @Query("UPDATE tracked_route SET targetPrice = :target WHERE id = :id")
    suspend fun updateTargetPrice(id: Long, target: Int?)
```

- [ ] **Step 2: UseCase**

```kotlin
/**
 * 목표가를 바꿔도 **통보 기준선은 건드리지 않는다.**
 * 기준선은 "마지막으로 알린 가격"이고 목표가는 "사용자가 원하는 가격"이다.
 * 목표가를 바꿨다고 그동안의 변동 판정을 초기화하면, 다음 폴링에서
 * 없던 변동이 잡힌다.
 */
class SetTargetPriceUseCase @Inject constructor(
    private val trackedRoutes: TrackedRouteRepository,
) {
    suspend operator fun invoke(id: Long, target: Won?) = trackedRoutes.setTargetPrice(id, target)
}
```

- [ ] **Step 3: 테스트**

```kotlin
    @Test fun `목표가를 저장한다`()
    @Test fun `null을 넣으면 목표가가 해제된다`()
    @Test fun `목표가를 바꿔도 통보 기준선은 그대로다`()
```

**세 번째가 중요하다.** 기준선이 함께 초기화되면 다음 폴링에서 가짜 변동이 잡힌다 —
이 프로젝트가 이미 여러 번 겪은 계열이다.

- [ ] **Step 4: 커밋** — `feat: 목표가 저장 경로 추가`

---

## Task 3: 화면

**Files:**
- Create: `presentation/.../tracking/TargetPriceDialog.kt`
- Modify: `presentation/.../tracking/TrackedRouteCard.kt`, `TrackingScreen.kt`, `TrackingViewModel.kt`
- Test: `presentation/src/test/.../tracking/TrackingViewModelTest.kt`

- [ ] **Step 1: 카드에 진입점**

`TrackedRouteCard`는 지금 목표가가 있을 때만 `"목표가 {금액}"`을 글자로 보여준다.
누를 수 있게 하고, **없을 때도 `"목표가 설정"`을 보여준다.** 없으면 설정할 방법이
없다는 게 지금 문제다.

- 48dp 이상 터치 영역
- `FlightDealTheme.colors.indigo`로 눌 수 있음을 나타낸다
- **카드 펼침 클릭과 겹치면 안 된다** — 해제 버튼이 이미 같은 문제를 풀어 뒀으니
  그 방식을 따를 것. 눌러서 확인할 것

- [ ] **Step 2: 다이얼로그**

`AlertDialog` + `OutlinedTextField`:
- `keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)`
- 기존 목표가가 있으면 그 값으로 채워 연다. 없으면 **현재가로 채운다** —
  빈 칸보다 고칠 값이 있는 편이 빠르고, 얼마쯤이 현실적인지도 알려준다
- **최대 9자리로 제한한다.** `Won.amount`가 `Int`라 그 이상은 넘친다
- 입력이 현재가 이상이면 안내를 띄운다:
  `"지금 가격보다 높아요. 바로 도달로 표시돼요."` — 막지는 않는다.
  사용자의 선택이고, 다만 결과를 알려준다
- 버튼: `저장` / `해제`(기존 목표가가 있을 때만) / `취소`
- `parseTargetPrice`가 null을 주면 `저장`을 누를 수 없게 한다

- [ ] **Step 3: ViewModel**

```kotlin
    fun setTarget(id: Long, target: Won?) {
        viewModelScope.launch { setTargetPrice(id, target) }
    }
```

테스트:
```kotlin
    @Test fun `목표가를 저장하면 저장소에 전달된다`()
    @Test fun `해제하면 null이 전달된다`()
```

- [ ] **Step 4: 빌드·테스트**

- [ ] **Step 5: 기기 확인 — logcat까지 볼 것**

`emulator-5554`에서:
1. 추적 카드의 `목표가 설정`을 눌러 다이얼로그가 뜨는지
2. 현재가로 채워져 열리는지
3. 현재가보다 높은 값을 넣으면 안내가 뜨는지
4. 저장 후 카드에 목표가가 보이고, **카드를 펼치면 그래프에 목표가 점선이 그려지는지**
   (범위 안일 때만 — `PriceChartGeometry`가 범위 밖이면 안 그린다)
5. 해제하면 사라지는지
6. 앱을 강제 종료했다 다시 열어도 남아 있는지 (Room에 실제로 저장됐는지)

**`adb -s emulator-5554 shell logcat -c` 후 위를 다 해보고
`logcat -d | grep -iE "FATAL|AndroidRuntime|NoSuchMethod|Exception"`으로 확인할 것.**
화면이 멀쩡해 보여도 예외가 삼켜지고 있을 수 있다 — 이 프로젝트에서 실제로 있었다.

라이트·다크 스크린샷을 남긴다.

- [ ] **Step 6: 커밋** — `feat: 추적 카드에서 목표가를 설정한다`
