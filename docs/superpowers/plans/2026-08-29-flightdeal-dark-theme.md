# 다크 모드 구현 계획

**Goal:** 기기가 다크 모드면 앱도 다크로 나온다.

**왜.** 지금 앱은 라이트 전용이다. `MainActivity`에 그렇게 적혀 있다 —
`// 앱이 라이트 전용이므로 시스템 uiMode가 아니라 앱 배경에 맞춰 고정한다`.
다크 모드를 쓰는 사용자가 밤에 앱을 열면 눈부신 흰 화면이 그대로 뜬다.
항공권 값을 침대에서 확인하는 앱이라 더 그렇다.

**Architecture — 지금 구조가 다크를 못 받는 이유.**
색이 `Color.kt`의 최상위 `val`이고, 각 Composable이 그걸 **직접 import해서** 쓴다:

```kotlin
import com.sypark.flightdeal.ui.theme.Background
...
.background(Background)
```

최상위 `val`은 테마에 따라 달라질 수 없다. `MaterialTheme.colorScheme`이 이미 있고
`Theme.kt`가 그 값들로 `lightColorScheme`을 만들지만, **정작 화면들은 그걸 안 읽고
원본 상수를 직접 읽는다.** 그래서 `darkColorScheme`을 추가해도 화면은 하나도 안 바뀐다.

이 앱의 색 이름(`Background`, `TextPrimary`, `PriceDown` 등)은 Material의 이름과
일대일로 대응하지 않는다. `PriceDown`/`PriceUp` 같은 건 Material 색 역할에 자리가 없다.
그러므로 **`CompositionLocal`로 앱 고유의 팔레트를 내려보낸다.**

## Global Constraints

- Java 소스 파일 금지 — `.java` 파일이 0개여야 한다
- `:domain`과 `:data`는 건드리지 않는다. 전부 `:presentation`이다
- **새 의존성 금지**
- 주석은 한국어로, **왜**를 적을 것
- 커밋에 Co-Authored-By나 Claude/AI 트레일러 금지
- **기존 테스트의 단언을 바꾸지 마라.** 색은 테스트가 보지 않으므로
  통과 개수가 그대로여야 한다 (`:domain` 67 · `:data` 94 · `:presentation` 59)

---

## Task 1: 팔레트를 테마에서 내려보낸다

**Files:**
- Modify: `presentation/.../ui/theme/Color.kt`, `Theme.kt`
- Modify: 색을 쓰는 화면 12개 (아래 목록)

- [ ] **Step 1: 팔레트 타입과 두 벌의 값**

`Color.kt`에:

```kotlin
/**
 * 앱 고유 팔레트. Material의 `colorScheme`으로 다 표현되지 않아 따로 둔다 —
 * `PriceDown`/`PriceUp`은 Material 색 역할에 자리가 없고, 이 앱에서는
 * "값이 내렸다/올랐다"라는 뜻을 나르는 핵심 색이다.
 */
@Immutable
data class FlightDealColors(
    val indigo: Color,
    val indigoSubtle: Color,
    val background: Color,
    val surface: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val priceDown: Color,
    val priceUp: Color,
)

val LightPalette = FlightDealColors(
    indigo = Color(0xFF4338E0),
    indigoSubtle = Color(0xFFEDEBFF),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF4F5F9),
    outline = Color(0xFFEAECF3),
    textPrimary = Color(0xFF0F1115),
    textSecondary = Color(0xFF8A8FA3),
    priceDown = Color(0xFF0E9E6E),
    priceUp = Color(0xFFD93A3A),
)

/**
 * 다크에서 라이트 색을 그대로 뒤집지 않는다.
 *
 * 인디고를 어두운 배경에 그대로 쓰면 대비가 모자라 글자가 뭉갠다. 밝게 올린다.
 * 초록·빨강도 마찬가지다 — 어두운 배경에서는 채도를 낮추고 명도를 올려야
 * 같은 세기로 읽힌다.
 */
val DarkPalette = FlightDealColors(
    indigo = Color(0xFF9A93FF),
    indigoSubtle = Color(0xFF262247),
    background = Color(0xFF101114),
    surface = Color(0xFF1A1C21),
    outline = Color(0xFF2A2D34),
    textPrimary = Color(0xFFF2F3F5),
    textSecondary = Color(0xFF9199AB),
    priceDown = Color(0xFF3DD9A0),
    priceUp = Color(0xFFFF7B7B),
)
```

**기존 최상위 `val Indigo` 등은 지운다.** 남겨두면 어떤 화면은 고정 색을,
어떤 화면은 팔레트를 읽어 다크에서 반만 바뀐다.

- [ ] **Step 2: `CompositionLocal`과 테마**

`Theme.kt`에:

```kotlin
/**
 * 기본값을 라이트로 둔다. 프리뷰나 테스트가 테마 밖에서 Composable을 그려도
 * 색이 없어 죽지 않게 한다.
 */
val LocalFlightDealColors = staticCompositionLocalOf { LightPalette }

/** `FlightDealTheme.colors.background` 꼴로 쓰기 위한 접근자. */
object FlightDealTheme {
    val colors: FlightDealColors
        @Composable get() = LocalFlightDealColors.current
}

@Composable
fun FlightDealTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(LocalFlightDealColors provides palette) {
        MaterialTheme(colorScheme = palette.toColorScheme(darkTheme), content = content)
    }
}
```

`toColorScheme`은 `darkColorScheme`/`lightColorScheme`에 팔레트 값을 채워 넣는다.
Material 컴포넌트(`AlertDialog`, `Snackbar`, `NavigationBar`, `TextButton`)가
그걸 읽으므로 **여기를 빼먹으면 다이얼로그와 스낵바만 밝은 채로 남는다.**

- [ ] **Step 3: 화면 12개를 팔레트로 바꾼다**

`MainActivity.kt`, `PlaceholderScreen.kt`, `FlightDealNavHost.kt`, `CalendarScreen.kt`,
`DayCell.kt`, `BookingLauncher.kt`, `FeedMessage.kt`, `DealSkeleton.kt`,
`DealFeedScreen.kt`, `DealCard.kt`, `PriceChart.kt`, `TrackedRouteCard.kt`,
`TrackingScreen.kt`

`import com.sypark.flightdeal.ui.theme.Background` 같은 줄을 지우고
`FlightDealTheme.colors.background`로 바꾼다.

**`BookingLauncher`는 Composable이 아니다.** Custom Tabs 툴바 색을 `Indigo` 상수로
쓰고 있을 텐데, 팔레트를 읽을 수 없다. 호출하는 쪽에서 색을 넘겨주게 고칠 것 —
그래야 다크 모드에서 툴바만 밝은 보라로 튀지 않는다.

`PriceChart`는 `Canvas` 안에서 색을 쓴다. `Canvas`는 Composable 스코프가 아니므로
`Canvas` **밖에서** 색을 변수로 읽어 넘길 것.

- [ ] **Step 4: 시스템 바**

`MainActivity`의 `enableEdgeToEdge`가 라이트로 고정돼 있다. 다크에서는
`SystemBarStyle.dark(...)`를 쓰지 않으면 흰 배경에 흰 아이콘이 그려진다.
`resources.configuration.uiMode`로 판정해서 넘긴다. 주석의 "앱이 라이트 전용이므로"도
사실이 아니게 되므로 고쳐 쓸 것.

- [ ] **Step 5: 빌드·테스트**

색 변경은 테스트가 보지 않는다. **통과 개수가 그대로여야 한다** —
줄었다면 무언가를 잘못 지운 것이다.

- [ ] **Step 6: 기기에서 두 모드를 다 확인한다**

```bash
adb -s emulator-5554 shell cmd uimode night yes   # 다크
adb -s emulator-5554 shell cmd uimode night no    # 라이트
```

**네 화면을 모두 본다** — 특가 / 추적(카드 펼쳐 그래프까지) / 달력 / 내정보.
그리고 다이얼로그(해제 확인)와 스낵바(추적 버튼)도 각각 다크에서 확인한다.
이 둘은 Material 컴포넌트라 팔레트가 아니라 `colorScheme`을 읽으므로
따로 빠뜨리기 쉽다.

각 모드 스크린샷을 남긴다. 끝나면 `night no`로 되돌린다.

- [ ] **Step 7: 커밋** — `feat: 다크 모드 지원`
