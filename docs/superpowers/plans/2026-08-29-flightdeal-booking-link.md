# 예약 딥링크 구현 계획

**Goal:** 딜 카드를 누르면 그 항공권을 실제로 예약할 수 있는 페이지가 열린다.

**왜 지금인가.** 앱이 최저가를 찾아주지만 **거기서 끝난다.**
`DealFeedScreen`의 `onClick`은 `{ /* 딥링크는 이후 계획서에서 */ }`다.
사용자가 할 수 있는 마지막 행동이 없는 상태다.

인프라는 이미 다 있다 — `DeepLinkBuilder`가 상대 경로에 도메인과 marker를 붙이고,
`PriceQuoteMapper`가 그것을 `PriceQuote.deepLink`에 넣어 도메인까지 실어 보낸다.
**비어 있는 건 클릭 핸들러 하나뿐이다.**

**Architecture:** Chrome Custom Tabs로 앱 안에서 연다. 브라우저를 새로 띄우면 사용자가
앱을 떠나고, 돌아오려면 작업 전환을 해야 한다. Custom Tabs는 뒤로 가기 한 번이면 딜 피드다.

## Global Constraints

- Java 소스 파일 금지 — `.java` 파일이 0개여야 한다
- `:domain`은 아무것도 의존하지 않는다. 안드로이드 타입 금지
- 비동기는 Coroutines/Flow. RxJava·LiveData 금지. KSP만, kapt 금지
- 가격은 `Won` value class
- **의존성은 `androidx.browser` 하나만 추가한다.** 버전 카탈로그(`gradle/libs.versions.toml`)를
  통할 것 — 모듈 build 파일에 버전 문자열을 직접 쓰지 마라
- 주석은 한국어로, **왜**를 적을 것
- 커밋에 Co-Authored-By나 Claude/AI 트레일러 금지

---

## Task 1: 예약 페이지 열기

**Files:**
- Modify: `gradle/libs.versions.toml`, `presentation/build.gradle.kts`
- Create: `presentation/src/main/java/com/sypark/flightdeal/booking/BookingLauncher.kt`
- Modify: `presentation/src/main/java/com/sypark/flightdeal/feed/DealFeedScreen.kt`
- Modify: `presentation/src/main/java/com/sypark/flightdeal/feed/DealCard.kt` (경로는 확인할 것)

- [ ] **Step 1: 의존성**

`libs.versions.toml`에 `androidx.browser:browser:1.8.0`을 `browser = "1.8.0"` 버전과 함께 더하고,
`presentation/build.gradle.kts`에 `implementation(libs.androidx.browser)`를 건다.

- [ ] **Step 2: `BookingLauncher`**

```kotlin
package com.sypark.flightdeal.booking

/**
 * 예약 페이지를 앱 안에서 연다.
 *
 * 외부 브라우저를 띄우면 사용자가 앱을 떠나고, 돌아오려면 작업 전환을 해야 한다.
 * Custom Tabs는 뒤로 가기 한 번이면 딜 피드로 돌아온다.
 */
object BookingLauncher {

    fun open(context: Context, url: String) {
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .build()

        try {
            intent.launchUrl(context, Uri.parse(url))
        } catch (e: ActivityNotFoundException) {
            // Custom Tabs를 지원하는 브라우저가 없는 기기가 있다. 그때는 아무 브라우저나 쓴다.
            // 여기서 막히면 사용자는 찾은 항공권을 예약할 방법이 없다.
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                .onFailure { Log.w(TAG, "예약 페이지를 열 수 있는 앱이 없다", it) }
        }
    }
}
```

`CustomTabsIntent.Builder().setDefaultColorSchemeParams(...)`로 툴바를 `Indigo`에 맞출 것.
색 상수는 `:presentation`의 테마에서 가져온다.

- [ ] **Step 3: 카드에 연결한다**

`DealFeedScreen`의 `onClick = { /* 딥링크는 이후 계획서에서 */ }`를 실제 호출로 바꾼다.
`LocalContext.current`는 이미 그 Composable 안에 있다.

**`deepLink`가 null인 경우를 반드시 다룰 것.** API가 `link`를 안 주는 견적이 있다.
그때는 카드를 눌러도 조용히 아무 일도 없으면 안 된다 — 사용자는 앱이 고장난 줄 안다.
`deepLink == null`이면 카드에 "예약처 연결 없음"을 `TextSecondary` 11.sp로 표시하고
누를 수 없게 한다. **누를 수 없다는 것이 눌리기 전에 보여야 한다.**

- [ ] **Step 4: 표시 가격이 참고가임을 예약 직전에 한 번 더 알린다**

소스가 실사용자 검색 기록 기반 7일 캐시라 실제 예약 가능한 운임과 다를 수 있다.
카드 하단에 이미 그런 문구가 있는지 확인하고, 없으면 딜 카드에 11.sp `TextSecondary`로
"표시 가격은 참고가예요"를 둔다. 이미 있으면 **중복해서 넣지 마라.**

- [ ] **Step 5: 빌드**

```bash
./gradlew :domain:test :data:testDebugUnitTest :presentation:testDebugUnitTest :presentation:assembleDebug
```

- [ ] **Step 6: 기기 확인**

`emulator-5554`에 설치하고 딜 카드를 눌러 Aviasales 예약 페이지가 **앱 안에서** 열리는지,
뒤로 가기 한 번에 피드로 돌아오는지 확인한다. 스크린샷을 남긴다.

URL에 `marker=`가 붙는지도 확인할 것. `local.properties`의 `TRAVELPAYOUTS_MARKER`가
비어 있으면 marker 없이 열린다 — 그건 정상 동작이고(링크는 열린다), 다만 커미션이
안 붙는다는 뜻이다. **어느 쪽인지 관측해서 보고할 것.**

- [ ] **Step 7: 커밋** — `feat: 딜을 누르면 예약 페이지를 앱 안에서 연다`
