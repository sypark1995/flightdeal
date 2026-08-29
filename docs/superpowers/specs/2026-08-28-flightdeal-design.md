# flightdeal — 항공권 최저가 앱 설계

작성일: 2026-08-28

## 1. 개요

인천 출발 항공권의 특가를 모아 보여주고, 사용자가 지정한 노선의 가격 변동을 감지해
알림을 주는 안드로이드 앱. 국내 여행 리워드 앱 캐치프로그와 같은 장르이며,
기술 스택은 기존 프로젝트 `hey_ticket`(OpenTicket)의 구조를 따르되 툴체인만 최신화한다.

### 해결하려는 문제

항공권 가격은 수시로 바뀌지만 사용자가 매일 검색창을 열어 확인할 수는 없다.
"지금 이 노선이 평소보다 싼가"와 "내가 노리던 노선이 떨어졌나"에 답하는 것이 이 앱의 목적이다.

### 범위 밖 (1단계)

- 자체 백엔드 서버. 1단계는 앱 단독으로 동작하며, 서버 폴링 + FCM 푸시는 2단계로 미룬다.
- 로그인, 계정, 기기 간 동기화. 모든 데이터는 로컬에만 저장한다.
- 항공권 직접 예약·결제. 예약은 제휴 예약처로 딥링크만 연결한다.

## 2. 배경 조사 결과

### 2.1 "실시간 요금 변동 감지"의 실체

항공사나 GDS가 가격 변동을 푸시로 전달하는 공개 API는 존재하지 않는다.
캐치프로그를 포함한 이 장르의 앱들은 모두 **폴링 + 가격 이력 DB + 임계치 판정** 구조를 쓴다.
캐치프로그가 "평균가 대비 최대 70% 저렴"을 주장하려면 평균가를 계산할 가격 이력 DB가
있어야 하는데, 이는 서버가 지속적으로 가격을 조회해 축적하고 있다는 뜻이다.

따라서 이 앱의 "실시간 감지"도 준실시간 폴링으로 구현한다. 1단계에서는 그 이력 DB가
서버가 아니라 단말의 Room에 쌓인다.

### 2.2 데이터 소스 선정

2026년 7월 17일자로 **Amadeus Self-Service API가 완전히 종료**되어, 수년간 개인 개발자의
진입점이던 경로가 사라졌다. Kiwi Tequila는 초대제로 전환됐다. 남은 선택지 중
**Travelpayouts(Aviasales) Data API**를 1단계 소스로 채택한다.

- 어필리에이트 가입만으로 무료 사용, 호출당 과금 없음
- 날짜별 최저가 캘린더(`/v1/prices/calendar`, 300 RPM), 가격 추이, 인기 노선 제공
- 캐치프로그의 제휴 커미션 모델과 동일한 계열의 소스

**알려진 제약 — 화면에 표기해야 한다:** 실사용자 검색 기록 기반 캐시 데이터이며 7일간
보관된다. 따라서 표시되는 가격은 그 순간 실제 예약 가능한 운임과 다를 수 있다.
UI에 "참고가"임을 명시하고, 정확한 가격은 딥링크로 연결된 예약처에서 확인하도록 한다.

**미검증 리스크:** 한국 출발 노선(ICN → 도쿄/방콕/다낭 등)의 데이터 밀도가 확인되지 않았다.
캐시가 실사용자 검색에 의존하므로 한산한 노선은 데이터가 얇거나 비어 있을 수 있다.
구현 0단계에서 실제 응답을 확인해 검증하며, 부실할 경우 `:data` 모듈의 Repository
구현체만 교체한다(§5 참조).

## 3. 기술 스택

`hey_ticket`의 구조를 유지하고 툴체인만 2026년 기준으로 올린다.

| 항목 | hey_ticket (2023) | flightdeal (2026) |
|---|---|---|
| 아키텍처 | Clean Architecture 3모듈 | 동일 |
| DI | Hilt | 동일 |
| 네트워크 | Retrofit + OkHttp + Gson | 동일 |
| 로컬 DB | Room | 동일 |
| 설정 저장 | DataStore Preferences | 동일 |
| 목록 | Paging 3 | **제외** (§3.1) |
| 화면 전환 | Navigation Component + SafeArgs | **Navigation Compose** |
| UI | XML DataBinding + ViewBinding | **Jetpack Compose + Material 3** (§3.3) |
| 백그라운드 | WorkManager | 동일 |
| 이미지 | Glide | **Coil** (Compose용) |
| 비동기 | Coroutines + RxJava3 혼용 | **Coroutines/Flow로 통일, RxJava 제거** |
| 어노테이션 처리 | kapt | **KSP** |
| 의존성 선언 | build.gradle 하드코딩 | **Version Catalog (libs.versions.toml)** |
| Kotlin / AGP | 1.8.0 / 7.4.2 | **2.x / 8.x** |
| compileSdk / targetSdk | 33 / 33 | **36 / 36** (프로젝트 생성 시점 최신 안정 버전 재확인) |
| minSdk | 21 | **26** |

RxJava를 제거하는 이유: `hey_ticket`에서 Coroutines와 RxJava3가 같은 계층에 섞여 있어
비동기 흐름을 추적하기 어려웠다. 신규 프로젝트에서는 하나로 통일한다.

### 3.1 승계하지 않는 것

- **Paging 3.** `hey_ticket`에는 있었으나 이 앱에는 페이징 경계가 없다. 특가 피드는
  상위 N건이고 캘린더는 한 달치가 한 번에 온다. 무한 스크롤이 필요한 화면이 생기면
  그때 추가한다.
- **material-calendarview.** `hey_ticket`이 쓰던
  `com.github.prolificinteractive:material-calendarview`는 오래 갱신되지 않았다.
  날짜별 최저가 히트맵은 `kizitonwose/Calendar`의 **Compose 버전**으로 구현한다.
- **Shimmer 라이브러리.** 로딩 스켈레톤은 Compose의 `rememberInfiniteTransition`으로
  25줄 안에 직접 그린다. View 전용 의존성을 하나 들이지 않아도 된다.

### 3.2 추가하는 것

- **Chrome Custom Tabs** (`androidx.browser`) — 제휴 예약처 딥링크
- **가격 추이 그래프는 라이브러리를 쓰지 않는다.** 30개 남짓한 점을 잇는 스파크라인
  하나뿐이라 차트 라이브러리를 통째로 들이는 것은 과하다. Compose `Canvas`의
  `drawPath`로 직접 그린다.

### 3.3 XML DataBinding에서 Compose로

1단계 구현 중에 UI 계층을 Compose로 바꿨다. 셋이 한꺼번에 터졌고 원인이 하나였다.

1. Kotlin `@BindingAdapter` 함수를 DataBinding 컴파일러가 발견하지 못한다. 바이트코드에
   메서드와 어노테이션이 다 있는데도 `Cannot find a setter`가 난다. 번들된
   `kotlinx-metadata-jvm`이 Kotlin 2.1(K2) 메타데이터를 못 읽는 것으로 보인다.
2. `Won`이 `@JvmInline value class`라 JVM 게터 이름이 뒤섞인다(`getPrice-XXXXXXX`).
   XML 표현식은 이 접근자를 해석하지 못한다.
3. 우회하려면 Java 파일이 필요하다.

Compose에서는 셋 다 존재하지 않는다. 화면이 Kotlin 함수이므로 value class도 어노테이션
처리도 접근자 이름 해석도 개입하지 않는다. **프로젝트에 Java 소스를 두지 않는다.**

이 전환으로 `:domain`과 `:data`는 한 줄도 바뀌지 않았고, `DealFeedViewModel`과 그 테스트도
그대로 살아남았다. `StateFlow`를 노출할 뿐이라 뷰 계층이 무엇이든 상관없었다.

## 4. 모듈 구조

```
:presentation   → :domain, :data     앱 모듈. Composable / ViewModel / Navigation Compose
:data           → :domain            Travelpayouts API, Room, DataStore, Repository 구현체
:domain         (의존성 없음)          모델, Repository 인터페이스, UseCase
```

의존 방향은 안쪽으로만 흐른다. `:domain`은 안드로이드 프레임워크에 의존하지 않으므로
JVM 단위 테스트만으로 검증할 수 있다.

## 5. 도메인 계층

`:domain`에는 Travelpayouts라는 단어가 등장하지 않는다. 소스 교체가 가능한 경계다.

### 5.1 모델

```kotlin
@JvmInline value class Won(val amount: Int)

data class Airport(val iata: String, val cityKo: String, val countryKo: String)
data class Route(val origin: Airport, val destination: Airport)

data class PriceQuote(
    val route: Route,
    val departDate: LocalDate,
    val returnDate: LocalDate?,
    val price: Won,
    val airline: String?,
    val foundAt: Instant,
    val deepLink: String?,
)

data class PriceStats(val median: Won, val min: Won, val max: Won, val sampleCount: Int)

data class TrackedRoute(
    val id: Long,
    val route: Route,
    val departDate: LocalDate,
    val returnDate: LocalDate?,
    val targetPrice: Won?,
    val createdAt: Instant,
)

data class PriceSnapshot(val trackedRouteId: Long, val price: Won, val capturedAt: Instant)
```

`Won`을 value class로 감싸는 이유: 가격을 raw `Int`로 다루면 다른 정수(할인율, 개수 등)와
혼동될 여지가 있다. 컴파일 타임에 막는다. 런타임 비용은 없다.

### 5.2 Repository 인터페이스

```kotlin
interface FlightPriceRepository {
    suspend fun cheapestDeals(origin: Airport, limit: Int, tripType: TripType): AppResult<List<PriceQuote>>

    /** 통계용. 예약처로 거르지 않은 시장 전체 분포다 — 할인 배지의 기준선. */
    suspend fun calendarPrices(route: Route, month: YearMonth, tripType: TripType): AppResult<List<PriceQuote>>
    suspend fun priceStats(route: Route, month: YearMonth, tripType: TripType): AppResult<PriceStats>

    /** 화면용. 날짜당 하나씩, 한국에서 예약 가능한 최저가. */
    suspend fun calendarDeals(route: Route, month: YearMonth, tripType: TripType): AppResult<CalendarDeals>

    /** 추적 중인 여정 하나의 현재가. 등록 기준가와 **같은 규칙**으로 고른다. */
    suspend fun trackedPrice(route: Route, departDate: LocalDate, returnDate: LocalDate?, tripType: TripType): AppResult<Won>
}

interface TrackedRouteRepository {
    fun observeAll(): Flow<List<TrackedRoute>>
    suspend fun getAll(): List<TrackedRoute>
    suspend fun add(
        route: Route, departDate: LocalDate, returnDate: LocalDate?,
        tripType: TripType, targetPrice: Won?, notifiedPrice: Won?,
    ): TrackRegistration
    /** 알림이 실제로 전달된 뒤에만 부른다. */
    suspend fun markNotified(id: Long, price: Won)
    suspend fun remove(id: Long)
}

interface PriceHistoryRepository {
    suspend fun append(snapshot: PriceSnapshot)
    fun observeHistory(trackedRouteId: Long, days: Int): Flow<List<PriceSnapshot>>
    suspend fun latest(trackedRouteId: Long): PriceSnapshot?
    fun observeCount(): Flow<Int>
    suspend fun clearAll()
    suspend fun pruneOlderThan(days: Int)
}
```

**`tripType`이 모든 조회에 들어가 있는 것이 핵심이다.** 왕복과 편도는 가격대가 세 배쯤
차이 나므로, 어느 쪽인지 모른 채 비교하면 매번 가짜 변동이 잡힌다. `calendarPrices`와
`calendarDeals`가 나뉘어 있는 것도 같은 이유다 — 아래 13장을 볼 것.

### 5.3 UseCase

- `GetDealFeedUseCase` — 특가 피드 조회. 각 `PriceQuote`에 `PriceStats` 중앙값 대비 할인율을 계산해 부착
- `CalculateDiscountUseCase` — 순수 함수. `(price, stats) -> 할인율 %`
- `DetectPriceChangesUseCase` — 순수 함수. `(직전 스냅샷, 신규 가격) -> 변동 목록`
- `TrackRouteUseCase` / `UntrackRouteUseCase`
- `GetPriceHistoryUseCase` — 추이 그래프용

순수 함수로 분리한 두 UseCase가 이 앱의 핵심 로직이며, 테스트 대상의 중심이다.

### 5.4 결과 타입

```kotlin
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data object Empty : AppResult<Nothing>
    data class NetworkError(val cause: Throwable) : AppResult<Nothing>
    data class Unknown(val cause: Throwable) : AppResult<Nothing>
}
```

`Empty`를 오류에서 분리한 것이 중요하다. Travelpayouts는 캐시 기반이므로 한산한 노선에
대해 **정상적으로** 빈 응답을 반환한다. 이를 오류로 처리하면 사용자는 앱 고장으로 오해한다.

## 6. 데이터 계층

### 6.1 Repository 구현체

`FlightPriceRepository`의 구현체를 둘 둔다.

- `TravelpayoutsFlightPriceRepository` — Retrofit `TravelpayoutsApi` + DTO → 도메인 매퍼
- `FakeFlightPriceRepository` — 번들된 로컬 JSON을 읽어 반환

Hilt qualifier로 빌드 배리언트에서 주입 대상을 전환한다. 이 구성이 세 가지를 해결한다.

1. API 키 발급 전에도 UI 개발을 진행할 수 있다
2. 한국 노선 데이터가 부실해 소스를 바꿔야 할 때 이 파일 하나만 교체하면 된다
3. ViewModel 테스트에서 네트워크 없이 결정론적 데이터를 쓸 수 있다

2단계에서 자체 서버를 붙일 때도 같은 자리에 구현체를 하나 더 추가하면 된다.

### 6.2 Room

| 테이블 | 용도 |
|---|---|
| `TrackedRouteEntity` | 추적 중인 노선 |
| `PriceSnapshotEntity` | 가격 이력. 추이 그래프와 "어제 대비" 계산의 근거 |

`PriceSnapshotEntity`는 계속 쌓이므로 **90일이 지난 행은 워커 실행 시 함께 삭제**한다.

### 6.3 DataStore

설계 초안은 기본 출발 공항, 알림 on/off, 마지막 동기 시각을 DataStore에 두려 했다.
구현 초기에는 셋 다 필요 없다고 판단해 의존성 자체를 넣지 않았다.

- **알림 on/off** — 안드로이드 알림 설정이 이미 유일한 진실이고 워커가 그것을 읽어
  판단한다. 앱에 별도 스위치를 두면 "앱에서는 켜짐, 시스템에서는 꺼짐"이 생기고
  사용자는 알림이 안 오는 이유를 영영 알 수 없다. 내정보 화면은 **읽어서 보여주고,
  바꾸는 것은 시스템 설정으로 넘긴다.**
- **마지막 동기 시각** — `price_snapshot`의 최신 `capturedAt`이 곧 그 값이다.
  같은 사실을 두 곳에 두지 않는다.

**출발 공항은 달라졌다.** 인천 고정이 정체성이던 시절에는 저장할 상태가 없었지만,
출발지 선택 기능(김포/부산/제주 추가)이 생기며 "고른 값이 앱을 다시 열어도 남아야
한다"는 요구가 생겼다. `SettingsRepository`가 `androidx.datastore:datastore-preferences`
하나로 이 하나의 값(선택한 공항의 IATA 문자열)만 저장한다.

### 6.4 API 키 관리

`local.properties`에 `TRAVELPAYOUTS_TOKEN`과 `TRAVELPAYOUTS_MARKER`를 두고
`buildConfigField`로 주입한다. `hey_ticket`이 `KAKAO_API_KEY`, `KOPIS_SERVICE_KEY`를
다루던 방식과 동일하다. `local.properties`는 커밋하지 않는다.

**선언 위치는 `:data`다.** 키를 쓰는 것은 `:data`의 Repository 구현체인데, `:data`는
`:presentation`을 의존하지 않으므로(그리고 의존해서도 안 되므로) `:presentation`의
`BuildConfig`를 읽을 수 없다. 키를 앱 모듈에 두면 §6.1의 "구현체만 교체하면 된다"는
전제가 깨진다.

## 7. 가격 추적 엔진

```
PriceCheckWorker (PeriodicWorkRequest, 6시간, NetworkType.CONNECTED, requiresBatteryNotLow)
  │
  ├─ 90일 초과 스냅샷 삭제
  ├─ 추적 목록 조회 (Room) — 출발일이 지난 여정은 제외
  ├─ 각 여정의 현재가 조회 (trackedPrice — 등록 기준가와 같은 규칙)
  ├─ PriceSnapshot 저장 (관측 이력. 그래프의 재료)
  ├─ DetectPriceChangesUseCase로 **통보 기준선**(tracked_route.notifiedPrice)과 비교
  ├─ 변동이 있으면 → 알림 1건으로 묶어 발송
  └─ **실제로 화면에 뜬 변동만** 기준선을 앞으로 옮긴다
```

**관측 이력과 통보 기준선은 다른 것이다.** 둘을 같이 취급하면 변동이 조용히 사라진다 —
`Result.retry()`가 워커를 다시 돌릴 때 1차 시도의 스냅샷이 비교 대상이 되어 전달하려던
변동을 스스로 지우고, 알림 채널이 꺼져 있어도 기준선만 앞으로 가서 나중에 채널을 켜도
그 하락은 다시 뜨지 않는다. 전달에 실패하면 기준선이 그대로 남아 다음 실행에서 같은
변동이 다시 잡힌다 — **놓치는 것보다 중복이 낫다.**

### 설계 근거

- **6시간 주기.** WorkManager 최소 주기는 15분이지만 항공권 가격은 분 단위로 바뀌지 않는다.
  15분 주기는 배터리와 API 쿼터만 소모하고 Doze 모드에서 어차피 밀린다. Doze로 인해
  실행이 몇 시간 지연될 수 있음을 수용한다.
- **변동이 있으면 무조건 알림.** 목표가 도달 여부와 무관하게 알린다. 6시간 주기라
  노선 3개 기준 하루 최대 12회이며, 실제로는 가격이 바뀐 경우에만 발송되므로 훨씬 적다.
- **알림 묶음.** 한 번의 워커 실행에서 여러 노선이 바뀌면 `NotificationCompat` 그룹으로
  묶어 1건만 발송한다. 노선을 많이 추적하는 사용자에게도 하루 최대 4회 배치가 된다.
- **실패 처리.** `Result.retry()` + 지수 백오프. 연속 3회 실패하면 `Result.success()`를
  돌려준다. **`Result.failure()`를 쓰면 안 된다** — 주기 작업에서 그건 terminal state라
  이후 실행이 취소된다. 이번 회차를 포기하는 것과 예약 자체를 끝내는 것은 다른 일이고,
  API 장애 몇 시간에 6시간 주기가 영구히 멈추면 안 된다.

### 권한

Android 13(API 33) 이상은 `POST_NOTIFICATIONS` 런타임 권한이 필요하다.
**앱 최초 실행 시점이 아니라 사용자가 첫 노선을 추적 등록하는 시점에 요청한다.**
권한을 거부해도 앱의 나머지 기능은 정상 동작해야 한다.

**minSdk가 26이므로 `checkSelfPermission(POST_NOTIFICATIONS)`로 판정하면 안 된다.**
그 권한은 API 33부터 존재하고, 그 아래에서는 시스템이 정의조차 모르는 권한이라
항상 거부로 나온다. 지원하는 API 11개 중 7개에서 알림이 통째로 버려지고, 에러는
어디에도 남지 않는다. API 30 에뮬레이터에서 `dumpsys package`로 확인한 사실이다 —
그 권한이 *요청 목록*에는 있고 *부여 목록*에는 없다.

판정은 `NotificationManagerCompat.areNotificationsEnabled()`(앱 전체)와
채널 `importance != IMPORTANCE_NONE`(채널별)을 **둘 다** 본다. 앱 전체만 보면
사용자가 "가격 변동" 채널 하나만 껐을 때 참이 나오는데, 그 상태에서 `notify()`는
조용히 버려진다. 권한 요청 자체도 `Build.VERSION.SDK_INT >= TIRAMISU`로 감싼다.

### 할인율 배지의 콜드 스타트

"평균가 대비 −38%" 배지는 로컬 이력이 아니라 **API 응답에 포함된 날짜별 가격 배열의
중앙값**을 기준으로 계산한다. 따라서 설치 첫날부터 동작한다. Room 이력은 그 위에
"어제 대비 변동"을 얹는 용도다.

### 추이 그래프의 콜드 스타트

할인율 배지와 달리, 가격 추이 그래프는 로컬 이력이 필요하므로 방금 추적 등록한 노선은
그릴 점이 없다. 다음과 같이 처리한다.

- 추적 등록 시점에 **첫 스냅샷을 즉시 저장한다.** 워커의 첫 실행을 6시간 기다리지 않는다.
- 스냅샷이 2개 미만이면 그래프 자리에 `"가격을 모으는 중이에요"`를 표시한다.
  빈 그래프를 그리지 않는다.

## 8. 화면

4탭 / 7화면. 단일 `ComponentActivity` + Navigation Compose. Fragment를 쓰지 않는다.

| 탭 | Composable | 내용 |
|---|---|---|
| 특가 | `DealFeedScreen` | 홈. 특가 카드 피드 |
| 추적 | `TrackedListScreen` | 추적 노선 목록 + 어제 대비 변동 |
| | `TrackedDetailScreen` | 30일 가격 추이 그래프, 목표가 설정 |
| 검색 | `SearchScreen` | 출발/도착/날짜 선택 |
| | `PriceCalendarScreen` | 날짜별 최저가 달력 (히트맵) |
| | `ExploreScreen` | 예산 필터 목적지 탐색 |
| 내정보 | `SettingsScreen` | 기본 출발지, 알림 설정 |

딜 카드를 누르면 **Chrome Custom Tabs**로 제휴 예약처에 연결한다.

### 상태 표현

모든 목록 화면은 네 가지 상태를 명시적으로 렌더링한다.

| 상태 | 표현 |
|---|---|
| 로딩 | Compose `rememberInfiniteTransition`으로 직접 그린 스켈레톤 |
| 성공 | 콘텐츠 |
| 빈 데이터 | "이 노선은 아직 가격 데이터가 없어요" — 오류가 아님을 분명히 |
| 네트워크 오류 | 마지막 캐시 데이터를 그대로 표시하고 스낵바로만 알림 |

## 9. 디자인

### 레이아웃

홈은 **특가 딜 피드** 방향. 상단 검색바 아래로 목적지 카드가 세로로 흐르고,
각 카드에 목적지 이미지 / 할인 배지 / 큰 가격 / 취소선 원가가 들어간다.

### 팔레트 — 인디고 × 화이트

| 역할 | 값 |
|---|---|
| 강조 | `#4338E0` |
| 강조 배경 (배지) | `#EDEBFF` |
| 배경 | `#FFFFFF` |
| 서피스 / 입력 | `#F4F5F9` |
| 경계선 | `#EAECF3` |
| 본문 | `#0F1115` |

**가격 하락은 항상 초록, 상승은 항상 빨강으로 고정한다.** 색이 정보를 나르는 자리이므로
브랜드 강조색과 분리한다. 이 규칙은 팔레트가 바뀌어도 유지된다.

가격 숫자가 화면에서 가장 큰 타이포 요소가 되도록 위계를 잡는다.

### 다크 팔레트

| 역할 | 값 |
|---|---|
| 강조 | `#9A93FF` |
| 강조 배경 (배지) | `#262247` |
| 배경 | `#101114` |
| 서피스 | `#1A1C21` |
| 경계선 | `#2A2D34` |
| 본문 | `#F2F3F5` |
| 하락 / 상승 | `#3DD9A0` / `#FF7B7B` |

**라이트 색을 뒤집어 만들지 않았다.** 인디고를 어두운 배경에 그대로 쓰면 대비가 모자라
글자가 뭉갠다. 초록·빨강도 채도를 낮추고 명도를 올려야 같은 세기로 읽힌다.

색은 최상위 `val`이 아니라 `CompositionLocal`로 내려보낸다. 최상위 상수는 테마에 따라
달라질 수 없어서, 그대로 두면 `darkColorScheme`을 추가해도 화면이 하나도 안 바뀐다.
Material 컴포넌트(다이얼로그·스낵바·NavigationBar)는 앱 팔레트가 아니라
`MaterialTheme.colorScheme`을 읽으므로 **두 곳을 다 채워야 한다.**

윈도 배경(`res/values`·`res/values-night`)도 같은 값으로 맞춘다. 안 맞추면 다크 모드에서
콜드 스타트와 시스템 전환마다 흰 화면이 번쩍인다.

### 상표 관련

캐치프로그의 로고, 브랜드 컬러, 캐릭터, 아이콘셋 등 고유 자산은 사용하지 않는다.
레이아웃 구조와 인터랙션 패턴은 이 장르의 공통 문법이므로 참고하되,
시각적 아이덴티티는 위 자체 팔레트로 구성한다.

## 10. 테스트

| 대상 | 방법 |
|---|---|
| `CalculateDiscountUseCase`, `DetectPriceChangesUseCase` | `:domain` JVM 단위 테스트. 순수 함수라 경계값까지 검증 |
| Travelpayouts 응답 파싱 | MockWebServer + 실제 응답을 고정한 JSON 픽스처 |
| 빈 응답 → `AppResult.Empty` 매핑 | 위와 동일. 회귀하기 쉬운 지점이라 반드시 포함 |
| Room DAO | 인메모리 DB |
| `PriceCheckWorker` | `TestListenableWorkerBuilder` |
| ViewModel | `FakeFlightPriceRepository` 주입 |

## 11. 구현 순서

0단계의 결과에 따라 이후 단계가 달라질 수 있다.
**아래 0~7단계는 모두 완료됐다.** 그 밖에 계획에 없던 것으로 다크 모드,
내정보 화면, 경유·비행시간 표시가 추가됐다.

0. **Travelpayouts 가입, API 키 발급, ICN → 도쿄 / 방콕 / 다낭 실제 응답 확인.**
   응답 스키마와 한국 노선 데이터 밀도를 검증하고 §6.1의 소스를 확정한다.
1. 프로젝트 골격 — 3모듈, Hilt, Version Catalog, KSP, 기본 Navigation 그래프
2. 도메인 모델 + Repository 인터페이스 + `FakeFlightPriceRepository` + UseCase 단위 테스트
3. 특가 피드 화면 (Fake 데이터) — 여기서 레이아웃과 팔레트가 실제로 보인다
4. Travelpayouts 실연동 + 응답 파싱 테스트
5. Room 이력 + 추적 목록/상세 화면 + 가격 추이 그래프
6. `PriceCheckWorker` + 알림 + 권한 요청
7. 날짜별 최저가 캘린더, 예약처 딥링크 (Custom Tabs)

목적지 탐색은 만들지 않았다. 목적지가 여섯 개뿐이라 캘린더 상단의 칩으로 충분하고,
별도 화면은 같은 일을 두 곳에서 하게 만든다.

## 12. 구현하며 드러난 것 — 이 앱에서 반복된 결함 두 가지

설계 단계에서 예상하지 못했고, 구현 중 다섯 번 넘게 같은 모양으로 반복됐다.
다음에 이 코드를 건드리는 사람이 가장 먼저 읽어야 할 부분이다.

### 12.1 비교하는 두 값을 서로 다른 규칙으로 고른다

- 왕복 가격을 편도 분포와 견줘 **할인 배지가 영영 뜨지 않았다**
- 등록 기준가는 한국에서 결제 가능한 예약처 중 최저가인데 폴링은 전체 최저가를 골라
  **첫 실행마다 있지도 않은 하락을 알렸다**
- 폴링이 귀국일을 맞추지 않아 10/16 왕복이 10/20 견적과 매칭돼 **유령 알림**이 반복됐다
- 조회가 도는 동안 토글만 먼저 바뀌어, 왕복 견적이 편도로 저장되고
  **이후 폴링이 영원히 매칭되지 않는 죽은 추적 항목**이 만들어졌다
- 캘린더가 예약처 규칙을 날짜별로 적용해, 딜 피드라면 절대 보여주지 않을
  **한국에서 결제 불가능한 예약처**를 편도 31일 중 17일에 띄웠다

**규칙:** "이 항공권 얼마인가"에 답하는 규칙은 **한 곳에만 두고 모든 화면이 그것을 부른다.**
지금 그 자리는 `trackedPrice()`와 `calendarDeals()`이며, 둘 다 딜 피드와 같은
`GatePolicy`를 같은 세분도로 적용한다. 그리고 **화면 상태에 "이게 왕복이냐"를 묻지 마라** —
`quote.returnDate != null`이 답이고, 데이터가 이미 아는 사실은 어긋날 수 없다.

통계와 표시를 나눈 것도 같은 원칙이다. `calendarPrices`는 일부러 예약처를 거르지 않는데
할인 배지의 기준선은 시장 전체 분포여야 맞기 때문이고, `calendarDeals`는 사용자가 눌러
결제할 값이라 거른다. **용도가 다르면 메서드를 나누고 KDoc에 못 박는다.**

### 12.2 `:domain`이 순수 JVM 모듈이라 안드로이드 API 레벨을 아무도 검사하지 않는다

`LocalDate.ofInstant`(API 34)를 써서 minSdk 26인 앱의 `PriceCheckWorker`가
**안드로이드 13 이하 전 기기에서 `NoSuchMethodError`로 죽었다.** 가격 추적과 알림,
이 앱의 핵심이 통째로 동작하지 않는 상태였다.

테스트는 JDK 17에서 돌아 통과했고, 안드로이드 린트는 JVM 모듈을 들여다보지 않는다.
**기기에서 logcat을 읽는 것 외에 드러날 방법이 없었다.**

**규칙:** `:domain`에서 `java.time`의 메서드를 쓸 때는 도입 API 레벨을 확인한다.
`Clock`을 받는 팩토리(`LocalDate.now(clock)`, `YearMonth.now(clock)`)를 쓰면 안전하다 —
그건 API 26부터다.

### 12.3 그래서 검증 절차에 이것이 들어간다

테스트 통과와 코드 리뷰만으로는 위 결함들이 걸러지지 않았다. 실제로 잡아낸 것은
**기기에 올려 화면을 보고 logcat을 읽는 것**이었다. 기능마다 그 절차를 거친다.

## 13. 2단계 이후 (범위 밖)

1단계 구조가 아래로 확장 가능하도록 설계돼 있다.

- **자체 서버 + FCM 푸시** — `FlightPriceRepository` 구현체를 하나 더 추가하는 형태.
  서버가 폴링하면 Doze 지연과 단말별 쿼터 문제가 사라진다.
- **계정과 기기 간 동기화** — 추적 목록을 서버로 옮긴다.
- **어필리에이트 수익화** — 딥링크는 1단계에서 이미 나갔고 `DeepLinkBuilder`가
  marker를 붙일 자리도 준비돼 있다. **다만 marker가 아직 미발급이라 실제로는 붙지 않는다** —
  링크는 정상적으로 열리되 커미션이 계정에 잡히지 않는다. Travelpayouts 대시보드
  좌측 하단 Partner ID를 발급받아 `local.properties`의 `TRAVELPAYOUTS_MARKER`에
  넣으면 코드 변경 없이 동작한다.
