# 출발지 선택 구현 계획

**Goal:** 인천 고정이던 출발지를 사용자가 고를 수 있게 한다. 고른 값은 앱을 다시 열어도 남는다.

---

## 먼저 읽을 것 — 도시 코드를 선택지에 넣으면 안 된다

Travelpayouts 응답은 모든 행에 두 가지 출발지를 준다. 실제 캡처를 확인했다:

| 픽스처 | `origin` | `origin_airport` |
|---|---|---|
| v3-ICN-TYO | `SEL` ×31 | `ICN` ×31 |
| v3-ICN-BKK | `SEL` ×25 | `ICN` ×25 |

`SEL`은 **도시 코드**로 인천(ICN)과 김포(GMP)를 함께 가리킨다.
`PriceQuoteMapper`는 `originAirport ?: origin`으로 공항 코드를 우선한다.
지금은 우리가 `ICN`으로 조회하니 저장되는 것도 `ICN`이라 우연히 맞아떨어진다.

**선택지에 `SEL`을 넣으면 그 우연이 깨진다.** `SEL`로 조회하면 응답에 인천발과
김포발이 섞여 오고, 매퍼는 행마다 `ICN` 또는 `GMP`를 저장한다. 그러면

- 카드 두 장이 똑같이 "서울 → 도쿄"인데 실제로는 다른 공항이다
- 할인 배지의 기준선(`priceStats`)이 카드마다 다른 분포에서 계산된다 —
  `GMP→TYO`는 `ICN→TYO`보다 훨씬 얇은 표본이다
- 같은 날짜·같은 목적지인데 공항이 달라 **추적 항목이 두 줄로 갈린다**

이 앱이 반복해서 겪은 "비교하는 두 값을 다른 규칙으로 고른다"의 또 한 사례다.

**그래서 선택지는 공항 코드만 쓴다 — ICN, GMP, PUS, CJU.**
조회한 것과 저장되는 것이 항상 같고, 여행자에게도 인천과 김포는 실제로 다른 정보다.

**따라서 `Airport.INCHEON`의 `cityKo`를 `"서울"`에서 `"인천"`으로 바꾼다.**
김포도 서울이라 둘 다 "서울 → 도쿄"로 뜨면 구별이 안 된다.
`Airport`의 동등성은 IATA만 보고 DB도 IATA만 저장하므로 **저장된 데이터는 안전하다.**

## Global Constraints

- Java 소스 파일 금지 — `.java` 파일이 0개여야 한다
- `:domain`은 아무것도 의존하지 않는다. 안드로이드 타입 금지, "Travelpayouts" 문자열 금지
- `:data`는 `:domain`만 의존한다
- 비동기는 Coroutines/Flow. RxJava·LiveData 금지. KSP만, kapt 금지
- 가격은 `Won` value class
- **의존성은 DataStore 하나만 추가한다** (`androidx.datastore:datastore-preferences`).
  버전 카탈로그를 통할 것. 설계 문서 §6.3이 "쓰지 않기로 했다"고 적혀 있는데,
  그때는 저장할 상태가 없었고 지금은 생겼다 — 문서도 함께 고친다
- 색은 `FlightDealTheme.colors`로 읽는다
- **`java.time`의 새 메서드는 도입 API 레벨을 확인할 것** (`:domain`은 린트 사각지대)
- 주석은 한국어로, **왜**를 적을 것
- 커밋에 Co-Authored-By나 Claude/AI 트레일러 금지

---

## Task 1: 출발지 목록과 이름

**Files:**
- Modify: `domain/.../model/Airport.kt`
- Modify: `data/.../remote/AirportNames.kt`
- Test: `:data`의 `AirportNamesTest`(없으면 만들 것)

- [ ] **Step 1: 목록**

```kotlin
        /** 기본 출발지. */
        val INCHEON = Airport("ICN", "인천", "대한민국")

        /**
         * 고를 수 있는 출발 공항.
         *
         * **도시 코드(`SEL`)를 넣지 마라.** 그건 인천과 김포를 함께 가리켜서,
         * 조회는 `SEL`로 하고 저장은 행마다 `ICN`/`GMP`로 갈린다. 같은 노선이
         * 두 줄로 나뉘고 할인 기준선도 카드마다 다른 분포에서 계산된다.
         */
        val ORIGINS = listOf(
            INCHEON,
            Airport("GMP", "김포", "대한민국"),
            Airport("PUS", "부산", "대한민국"),
            Airport("CJU", "제주", "대한민국"),
        )
```

- [ ] **Step 2: 이름 표가 어긋나지 않게 한다**

`AirportNames.ADDITIONAL_CITIES`에 `"GMP" to "서울"`, `"PUS" to "부산"`,
`"CJU" to "제주"`가 이미 있다. 이제 `Airport.ORIGINS`가 같은 코드의 이름을 들고 있으므로
**중복이다.** `CITIES`가 `Airport.DESTINATIONS + Airport.INCHEON`을 읽는 자리를
`Airport.DESTINATIONS + Airport.ORIGINS`로 바꾸고, `ADDITIONAL_CITIES`에서
`GMP`/`PUS`/`CJU`를 지운다. `SEL`은 남긴다 — 응답의 `origin` 필드에 실제로 오는 값이고
매퍼가 폴백으로 쓸 수 있다.

`AirportNames`의 KDoc이 이 규칙을 이미 설명하고 있으니 `ORIGINS`도 포함되게 고친다.

테스트:
```kotlin
    @Test fun `출발 공항 이름이 Airport와 어긋나지 않는다`() {
        Airport.ORIGINS.forEach { assertEquals(it.cityKo, AirportNames.cityOf(it.iata)) }
    }
```

- [ ] **Step 3: 커밋** — `feat: 고를 수 있는 출발 공항 목록 추가`

---

## Task 2: 선택을 저장한다

**Files:**
- Modify: `gradle/libs.versions.toml`, `data/build.gradle.kts`
- Create: `domain/.../repository/SettingsRepository.kt`
- Create: `data/.../local/DataStoreSettingsRepository.kt`
- Modify: `data/.../di/RepositoryModule.kt`
- Test: `:data` (Robolectric)

- [ ] **Step 1: 인터페이스**

```kotlin
interface SettingsRepository {
    /** 고른 출발 공항. 고른 적이 없으면 [Airport.INCHEON]. */
    fun observeOrigin(): Flow<Airport>
    suspend fun setOrigin(origin: Airport)
}
```

- [ ] **Step 2: 구현**

IATA 문자열만 저장한다. 읽을 때 `Airport.ORIGINS`에서 찾고,
**없으면 조용히 기본값으로 돌아간다:**

```kotlin
    // 목록에서 빠진 공항이 저장돼 있을 수 있다 — 앱 버전이 올라가며 선택지가
    // 줄면 그렇게 된다. 그때 예외를 던지면 앱이 열리지 않는다. 기본값으로 돌아간다.
    Airport.ORIGINS.firstOrNull { it.iata == stored } ?: Airport.INCHEON
```

- [ ] **Step 3: 테스트**

```kotlin
    @Test fun `고른 적이 없으면 인천이다`()
    @Test fun `고른 공항이 남는다`()
    @Test fun `목록에 없는 값이 저장돼 있으면 인천으로 돌아간다`()
```

- [ ] **Step 4: 커밋** — `feat: 출발지 선택을 저장한다`

---

## Task 3: 두 화면이 같은 출발지를 본다

**Files:**
- Create: `presentation/.../ui/OriginSelector.kt`
- Modify: `presentation/.../feed/DealFeedViewModel.kt`, `DealFeedScreen.kt`
- Modify: `presentation/.../calendar/CalendarViewModel.kt`, `CalendarScreen.kt`
- Test: 두 ViewModel 테스트

- [ ] **Step 1: 공용 선택기**

제목 아래에 `"인천 출발 ▾"` 꼴로 두고, 누르면 네 공항을 고르는 다이얼로그를 연다.
칩을 한 줄 더 얹지 않는다 — 피드에는 이미 왕복/편도 칩과 검색창이 있다.

- [ ] **Step 2: 두 ViewModel이 같은 것을 읽는다**

`DealFeedViewModel`과 `CalendarViewModel`이 각각 `Airport.INCHEON`을 하드코딩하고 있다.
둘 다 `SettingsRepository.observeOrigin()`을 읽게 바꾼다.

**출발지가 바뀌면 두 화면 다 다시 조회해야 한다.** 한쪽만 갱신되면 같은 노선인데
화면마다 다른 출발지의 가격이 뜬다 — 이 앱이 반복해서 겪은 결함이다.

`DealFeedViewModel`의 재진입 처리(이전 요청 취소)를 그대로 따를 것.

- [ ] **Step 3: 테스트**

```kotlin
    @Test fun `출발지가 바뀌면 다시 조회한다`()
    @Test fun `저장된 출발지로 처음 조회한다`()
```

- [ ] **Step 4: 빌드·테스트**

- [ ] **Step 5: 기기 확인 — logcat까지**

`emulator-5554`에서:
1. 피드 제목 아래 `인천 출발 ▾`이 보이는지
2. 눌러 김포/부산/제주로 바꾸면 **가격이 실제로 바뀌는지**
3. **달력 탭도 같은 출발지를 보는지** — 피드에서 부산으로 바꾸고 달력으로 갔을 때
4. 앱을 강제 종료했다 다시 열어도 선택이 남는지
5. 추적을 걸고 카드에 출발 도시가 맞게 뜨는지 (`인천 → 도쿄`, `부산 → 도쿄`)

**네 출발지 각각에 대해 실제로 데이터가 오는지 확인해 보고할 것.**
`PUS`/`CJU`는 국제선 표본이 얇아 빈 응답일 수 있다. 빈 응답은 오류가 아니므로
선택지에서 빼지는 말고, **어느 출발지가 실제로 값을 주는지 관측해서 보고**하면 된다.

`adb -s emulator-5554 shell logcat -c` 후 위를 다 해보고
`logcat -d | grep -iE "FATAL|AndroidRuntime|NoSuchMethod|Exception"`으로 확인할 것.
`com.sypark.flightdeal`로 한 번 더 걸러 앱 자신의 예외만 볼 것.

라이트·다크 스크린샷을 남긴다.

- [ ] **Step 6: 커밋** — `feat: 출발지를 고를 수 있게 한다`
