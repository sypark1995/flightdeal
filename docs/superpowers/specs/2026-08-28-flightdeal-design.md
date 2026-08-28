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
    suspend fun cheapestDeals(origin: Airport, limit: Int): AppResult<List<PriceQuote>>
    suspend fun calendarPrices(route: Route, month: YearMonth): AppResult<List<PriceQuote>>
    suspend fun priceStats(route: Route, month: YearMonth): AppResult<PriceStats>
}

interface TrackedRouteRepository {
    fun observeAll(): Flow<List<TrackedRoute>>
    suspend fun add(route: Route, departDate: LocalDate, returnDate: LocalDate?, targetPrice: Won?): Long
    suspend fun update(tracked: TrackedRoute)
    suspend fun remove(id: Long)
}

interface PriceHistoryRepository {
    suspend fun append(snapshot: PriceSnapshot)
    fun observeHistory(trackedRouteId: Long, days: Int): Flow<List<PriceSnapshot>>
    suspend fun latest(trackedRouteId: Long): PriceSnapshot?
}
```

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

기본 출발 공항(기본값 ICN), 알림 on/off, 마지막 동기 시각.

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
  ├─ 추적 목록 조회 (Room)
  ├─ 각 노선의 최저가 조회 (FlightPriceRepository)
  ├─ PriceSnapshot 저장
  ├─ 90일 초과 스냅샷 삭제
  ├─ DetectPriceChangesUseCase로 직전 스냅샷과 비교
  └─ 변동이 하나라도 있으면 → 알림 1건으로 묶어 발송
```

### 설계 근거

- **6시간 주기.** WorkManager 최소 주기는 15분이지만 항공권 가격은 분 단위로 바뀌지 않는다.
  15분 주기는 배터리와 API 쿼터만 소모하고 Doze 모드에서 어차피 밀린다. Doze로 인해
  실행이 몇 시간 지연될 수 있음을 수용한다.
- **변동이 있으면 무조건 알림.** 목표가 도달 여부와 무관하게 알린다. 6시간 주기라
  노선 3개 기준 하루 최대 12회이며, 실제로는 가격이 바뀐 경우에만 발송되므로 훨씬 적다.
- **알림 묶음.** 한 번의 워커 실행에서 여러 노선이 바뀌면 `NotificationCompat` 그룹으로
  묶어 1건만 발송한다. 노선을 많이 추적하는 사용자에게도 하루 최대 4회 배치가 된다.
- **실패 처리.** `Result.retry()` + 지수 백오프. 연속 3회 실패하면 `Result.failure()`로
  종료하고 다음 정기 주기를 기다린다.

### 권한

Android 13(API 33) 이상은 `POST_NOTIFICATIONS` 런타임 권한이 필요하다.
**앱 최초 실행 시점이 아니라 사용자가 첫 노선을 추적 등록하는 시점에 요청한다.**
권한을 거부해도 앱의 나머지 기능은 정상 동작해야 한다.

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

0. **Travelpayouts 가입, API 키 발급, ICN → 도쿄 / 방콕 / 다낭 실제 응답 확인.**
   응답 스키마와 한국 노선 데이터 밀도를 검증하고 §6.1의 소스를 확정한다.
1. 프로젝트 골격 — 3모듈, Hilt, Version Catalog, KSP, 기본 Navigation 그래프
2. 도메인 모델 + Repository 인터페이스 + `FakeFlightPriceRepository` + UseCase 단위 테스트
3. 특가 피드 화면 (Fake 데이터) — 여기서 레이아웃과 팔레트가 실제로 보인다
4. Travelpayouts 실연동 + 응답 파싱 테스트
5. Room 이력 + 추적 목록/상세 화면 + 가격 추이 그래프
6. `PriceCheckWorker` + 알림 + 권한 요청
7. 날짜별 최저가 캘린더, 목적지 탐색, 예약처 딥링크

## 12. 2단계 이후 (범위 밖)

1단계 구조가 아래로 확장 가능하도록 설계돼 있다.

- **자체 서버 + FCM 푸시** — `FlightPriceRepository` 구현체를 하나 더 추가하는 형태.
  서버가 폴링하면 Doze 지연과 단말별 쿼터 문제가 사라진다.
- **계정과 기기 간 동기화** — 추적 목록을 서버로 옮긴다.
- **어필리에이트 수익화** — 딥링크에 marker를 붙여 커미션을 받는 구조.
  캐치프로그와 동일한 모델이며 1단계 딥링크 구현이 그대로 이어진다.
