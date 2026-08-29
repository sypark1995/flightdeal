# 위젯과 홈 화면 바로가기 구현 계획

**Goal:** 앱을 열지 않고도 추적 중인 가격을 보고, 앱 아이콘을 길게 눌러 원하는 화면으로 바로 간다.

**왜.** 가격 추적은 **기다리는 일**이다. 6시간마다 워커가 돌고 변동이 있을 때만 알림이 온다.
그 사이 "지금 얼마지?"를 확인하려면 앱을 열고 탭을 옮겨야 한다. 홈 화면에 있으면
그 과정이 사라진다 — 가격 추적 앱이 위젯과 잘 맞는 이유다.

## Global Constraints

- Java 소스 파일 금지 — `.java` 파일이 0개여야 한다. XML 리소스는 괜찮다
- `:domain`은 아무것도 의존하지 않는다. `:data`는 `:domain`만 의존
- 비동기는 Coroutines/Flow. RxJava·LiveData 금지. KSP만, kapt 금지
- 가격은 `Won` value class
- **의존성은 Glance 하나만 추가한다** (`androidx.glance:glance-appwidget`).
  버전 카탈로그를 통할 것
- **`java.time`의 새 메서드는 도입 API 레벨을 확인할 것** (`:domain`은 린트 사각지대)
- 주석은 한국어로, **왜**를 적을 것
- 커밋에 Co-Authored-By나 Claude/AI 트레일러 금지

---

## Task 1: 위젯에 보여줄 것을 정하는 순수 함수

**Files:**
- Create: `presentation/.../widget/WidgetContent.kt`
- Test: `presentation/src/test/.../widget/WidgetContentTest.kt`

**안드로이드 import가 하나도 없어야 한다.** Glance 타입도 여기서 쓰지 마라 —
그 순간 JVM 테스트에서 못 부른다.

```kotlin
/** 위젯 한 줄. */
data class WidgetRow(
    val label: String,          // "인천 → 도쿄"
    val price: Won?,            // 아직 관측이 없으면 null
    val previous: Won?,         // 마지막으로 값이 달랐던 관측. 없으면 화살표를 안 그린다
    val hasDeparted: Boolean,
)

/**
 * 위젯은 좁다. 다 보여주려 하면 아무것도 안 읽힌다.
 *
 * - 출발일이 지난 여정은 **뒤로** 민다. 값이 갱신되지 않으므로 먼저 볼 이유가 없다
 * - 그 안에서는 등록이 최근인 것부터
 * - [limit]개까지만
 */
fun widgetRows(items: List<TrackedItem>, limit: Int): List<WidgetRow>
```

- [ ] **Step 1: 실패하는 테스트를 먼저 쓴다**

```kotlin
    @Test fun `지난 여정은 뒤로 민다`()
    @Test fun `limit개까지만 남긴다`()
    @Test fun `관측이 없으면 가격이 null이다`()
    @Test fun `값이 그대로인 관측이 쌓여도 마지막 변동을 previous로 준다`()
```

**네 번째는 추적 화면이 이미 푼 문제와 같은 것이다.** 스냅샷은 값이 안 바뀌어도
폴링마다 쌓이므로 "마지막 두 개"를 비교하면 변동이 사라진다. `TrackingViewModel`이
어떻게 하는지 읽고 **같은 규칙**을 쓸 것 — 두 화면이 같은 노선에 대해 다른 화살표를
보여주면 안 된다.

- [ ] **Step 2: 실패 확인 → 구현 → 통과 확인**
- [ ] **Step 3: 커밋** — `feat: 위젯에 보여줄 줄을 고르는 함수 추가`

---

## Task 2: Glance 위젯

**Files:**
- Modify: `gradle/libs.versions.toml`, `presentation/build.gradle.kts`
- Create: `presentation/.../widget/PriceWidget.kt`, `PriceWidgetReceiver.kt`
- Create: `presentation/src/main/res/xml/price_widget_info.xml`
- Modify: `presentation/src/main/AndroidManifest.xml`
- Modify: `presentation/.../worker/PriceCheckWorker.kt`

- [ ] **Step 1: 의존성과 등록**

`androidx.glance:glance-appwidget` 최신 안정판. `AppWidgetProvider` 대신
`GlanceAppWidgetReceiver`를 쓴다.

`price_widget_info.xml`: `minWidth` 250dp × `minHeight` 110dp 정도(2×2 이상),
`resizeMode="horizontal|vertical"`, `updatePeriodMillis="0"` —
**주기 갱신을 시스템에 맡기지 않는다.** 워커가 값을 새로 쓸 때 우리가 갱신한다.
시스템 주기는 최소 30분이라 6시간 주기와 어긋나 배터리만 쓴다.

- [ ] **Step 2: 데이터 읽기**

Glance의 `provideGlance`는 Composable이 아니라 suspend 함수다. Hilt 주입이 안 되므로
`EntryPointAccessors`로 `SingletonComponent`에서 저장소를 꺼낸다.

```kotlin
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PriceWidgetEntryPoint {
    fun trackedRoutes(): TrackedRouteRepository
    fun history(): PriceHistoryRepository
    fun clock(): Clock
}
```

**`observeAll()`이 아니라 `getAll()`로 한 번만 읽는다.** 위젯은 갱신 시점에
한 장을 그리는 것이지 계속 구독하는 화면이 아니다.

- [ ] **Step 3: 그리기**

- 제목 `추적 중인 항공권`
- 최대 3줄. 각 줄: `인천 → 도쿄` / `304,619원` / `▼` (하락 초록, 상승 빨강)
- 지난 여정은 `· 지난`을 붙이고 흐리게
- 추적이 없으면 `추적 중인 항공권이 없어요`
- **탭하면 앱의 추적 화면이 열린다.** `MainActivity`가 이미
  `EXTRA_OPEN_TRACKING`을 받으므로 그것을 그대로 쓴다
- 색은 라이트/다크 모두 읽히게 한다. Glance는 앱의 `CompositionLocal` 팔레트를
  쓸 수 없으므로 **`res/values`·`res/values-night`의 색 리소스를 참조한다.**
  값은 `Color.kt`의 팔레트와 같게 맞출 것 — 어긋나면 위젯만 다른 앱처럼 보인다

- [ ] **Step 4: 갱신 시점**

`PriceCheckWorker`가 한 번 돈 뒤 위젯을 갱신한다. **알림이 떴는지와 무관하게
갱신한다** — 값이 바뀌었으면 위젯은 새 값을 보여야 하고, 알림은 채널이 꺼져 있을
수도 있다. 둘은 다른 사건이다.

추적을 걸거나 해제했을 때도 갱신한다. `TrackRouteUseCase`/`UntrackRouteUseCase`는
`:domain`이라 안드로이드를 모른다 — **ViewModel 쪽에서 부를 것.**

- [ ] **Step 5: 기기 확인 — logcat까지**

`emulator-5554`에서:
1. 위젯을 홈 화면에 추가한다
   (`adb shell am start -a android.appwidget.action.APPWIDGET_PICK`이 잘 안 되면
   런처에서 직접 길게 눌러 추가할 것. 안 되면 그렇다고 보고하면 된다)
2. 추적 중인 노선과 가격이 보이는지
3. 탭하면 앱의 추적 화면이 열리는지
4. 추적을 하나 해제하면 위젯에서도 사라지는지
5. 라이트·다크 각각에서 읽히는지
6. 추적이 하나도 없을 때 빈 문구가 뜨는지

`logcat -c` 후 위를 다 하고 `com.sypark.flightdeal`로 걸러
`FATAL|AndroidRuntime|NoSuchMethod|Exception`을 확인한다.
**깨끗해도 무엇을 봤는지 보고할 것.**

- [ ] **Step 6: 커밋** — `feat: 추적 가격 위젯 추가`

---

## Task 3: 홈 화면 바로가기

**Files:**
- Create: `presentation/src/main/res/xml/shortcuts.xml`
- Modify: `presentation/src/main/AndroidManifest.xml`, `MainActivity.kt`, `FlightDealNavHost.kt`

앱 아이콘을 길게 누르면 `추적`과 `달력`으로 바로 간다.

- [ ] **Step 1: 목적지 지정을 일반화한다**

`MainActivity`는 지금 `EXTRA_OPEN_TRACKING: Boolean`을 받는다. 목적지가 둘 이상이
되므로 **탭을 가리키는 문자열 하나로 바꾼다** (`EXTRA_OPEN_ROUTE`).

**알림의 PendingIntent가 이 경로를 쓰고 있고 기기에서 검증된 동작이다.**
바꾼 뒤 알림 탭이 여전히 추적 화면을 여는지 반드시 확인할 것.
회전 시 되돌아가지 않도록 인텐트 extra를 읽고 지우는 처리도 그대로 유지한다.

- [ ] **Step 2: `shortcuts.xml`**

정적 바로가기 둘 — `추적 중인 항공권`, `날짜별 최저가`. 각각 `MainActivity`를
`EXTRA_OPEN_ROUTE`와 함께 연다. 아이콘은 기본 리소스를 쓴다.

- [ ] **Step 3: 기기 확인**

`adb -s emulator-5554 shell cmd shortcut get-shortcuts 0 com.sypark.flightdeal`로
등록됐는지 보고, 런처에서 아이콘을 길게 눌러 둘 다 실제로 해당 화면을 여는지 확인한다.
**알림 탭도 다시 확인할 것** — Step 1에서 건드린 경로다.

- [ ] **Step 4: 커밋** — `feat: 홈 화면 바로가기 추가`

---

## 막히면

Glance가 이 툴체인(Kotlin 2.1, compileSdk 36)과 부딪히면 **오래 싸우지 말고 멈춰서
보고할 것.** RemoteViews + XML 레이아웃으로 가는 길이 있고, 그건 판단이 필요한
갈림길이라 혼자 정할 일이 아니다.
