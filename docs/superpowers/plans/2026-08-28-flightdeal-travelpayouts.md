# Travelpayouts 실연동 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `FakeFlightPriceRepository`를 실제 Travelpayouts 응답으로 대체하고, 특가 피드에 왕복/편도 전환을 붙인다.

**Architecture:** `:data`에 Retrofit API 인터페이스, DTO, 도메인 매퍼, 실 구현체를 만든다. `RepositoryModule`의 바인딩을 갈아끼우면 화면과 도메인은 손대지 않고 실데이터로 넘어간다. Fake는 지우지 않는다 — 테스트와 오프라인 개발에 계속 쓴다.

**Tech Stack:** Retrofit 2 · OkHttp · Gson · MockWebServer · Kotlin 2.1 · Hilt · KSP

## 검증된 사실 (추측 아님)

`docs/research/2026-08-28-travelpayouts-icn-검증.md`에 근거가 있다. 실제 토큰으로 확인했다.

| 항목 | 값 |
|---|---|
| 엔드포인트 | `GET https://api.travelpayouts.com/aviasales/v3/prices_for_dates` |
| 성공 응답 | `{"success": true, "data": [...], "currency": "krw"}` |
| 빈 결과 | `success: true`, `data: []` |
| 잘못된 노선 | **HTTP 400** + `{"error":"...","data":null,"status":400,"success":false}` |
| 잘못된 토큰 | **HTTP 401** + 평문 `Unauthorized` — **JSON이 아니다** |
| 기본값 | 편도. `return_at` 필드가 아예 오지 않는다 |
| 왕복 | `one_way=false` + `return_at=YYYY-MM` |
| `link` | 상대 경로 `/search/ICN0610TYO1?t=...` |
| `airline` | IATA 코드 (`"ZE"`, `"TW"`) — 사람이 읽을 이름이 아니다 |
| 데이터 밀도 | ICN→TYO 31일, BKK 25일, DAD 20일 (2026-10 기준) |

**`v1/prices/calendar`는 쓰지 않는다.** `depart_date`로 달을 지정해도 무시하고 오늘부터 1년치를 돌려주며, 딥링크가 없다.

## Global Constraints

기존 프로젝트 제약을 그대로 승계한다.

- 패키지 루트: `com.sypark.flightdeal`; compileSdk/targetSdk 36, minSdk 26; Java 17
- **Java 소스 파일을 만들지 않는다.** 프로젝트에 `.java` 파일이 하나도 없어야 한다
- `:domain`은 아무것도 의존하지 않는다. **`:domain`에 Travelpayouts라는 단어가 등장해서는 안 된다.** 안드로이드 프레임워크 타입도 마찬가지
- `:data`는 `:domain`만 의존한다
- 비동기는 Coroutines/Flow로 통일한다. RxJava 금지, LiveData 금지
- 어노테이션 처리는 KSP를 쓴다. kapt 금지
- 가격은 `Won` value class로 다룬다. raw `Int`로 가격을 주고받지 않는다
- 의존성은 전부 `gradle/libs.versions.toml`에 선언한다. 모듈 build 파일에 버전 문자열을 직접 쓰지 않는다
- 강조색 `#4338E0`, 강조 배경 `#EDEBFF`, 배경 `#FFFFFF`, 서피스 `#F4F5F9`, 경계선 `#EAECF3`, 본문 `#0F1115`, 보조 텍스트 `#8A8FA3`. 가격 하락 초록 `#0E9E6E`, 상승 빨강 `#D93A3A` — 브랜드색과 섞지 않는다
- 커밋 메시지: `feat/fix/build/chore/ci/docs/style/refactor/test/perf` 접두사 + 한국어 제목. **커밋에 Claude를 참여자로 기록하지 않는다**
- `local.properties`는 커밋하지 않는다
- 빌드에 JDK 17이 필요하다: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`

### 추가할 버전

```toml
retrofit = "2.11.0"
okhttp = "4.12.0"
gson = "2.11.0"
```

해석에 실패하면 최신 안정 버전을 확인해 올리고, 무엇을 왜 바꿨는지 보고한다.

---

## File Structure

| 파일 | 책임 |
|---|---|
| `:domain` `model/TripType.kt` | 왕복/편도 구분. 도메인 어휘이므로 여기 산다 |
| `:domain` `repository/FlightPriceRepository.kt` (수정) | `cheapestDeals`에 `tripType` 추가 |
| `:data` `remote/TravelpayoutsApi.kt` | Retrofit 인터페이스. 엔드포인트 하나 |
| `:data` `remote/dto/PricesForDatesResponse.kt` | 응답 DTO. 필드 이름은 API 그대로 |
| `:data` `remote/AirlineNames.kt` | IATA 코드 → 한국어 항공사명 |
| `:data` `remote/GatePolicy.kt` | 예약처 화이트리스트와 보충 규칙 |
| `:data` `remote/DeepLinkBuilder.kt` | 상대 경로 + 도메인 + marker 조립 |
| `:data` `remote/PriceQuoteMapper.kt` | DTO → `PriceQuote` |
| `:data` `remote/TravelpayoutsFlightPriceRepository.kt` | 실 구현체. 에러 매핑 |
| `:data` `di/NetworkModule.kt` | OkHttp / Retrofit / Api 제공 |
| `:data` `di/RepositoryModule.kt` (수정) | 바인딩 교체 |
| `:presentation` `feed/DealFeedViewModel.kt` (수정) | `tripType` 상태 |
| `:presentation` `feed/DealFeedScreen.kt` (수정) | 왕복/편도 토글 |

매퍼를 네 파일로 쪼갠 이유: 항공사명·예약처 정책·딥링크는 각각 독립적으로 바뀌고 각각 따로 테스트된다. 한 파일에 뭉치면 예약처 하나 추가할 때 딥링크 테스트까지 읽어야 한다.

---

## Task 1: 도메인에 왕복/편도 개념 추가

먼저 도메인부터 바꾼다. 이걸 나중에 하면 매퍼와 구현체를 두 번 고치게 된다.

**Files:**
- Create: `domain/src/main/java/com/sypark/flightdeal/domain/model/TripType.kt`
- Modify: `domain/src/main/java/com/sypark/flightdeal/domain/repository/FlightPriceRepository.kt`
- Modify: `domain/src/main/java/com/sypark/flightdeal/domain/usecase/GetDealFeedUseCase.kt`
- Modify: `data/src/main/java/com/sypark/flightdeal/data/fake/FakeFlightPriceRepository.kt`
- Modify: `domain/src/test/java/com/sypark/flightdeal/domain/usecase/GetDealFeedUseCaseTest.kt`
- Modify: `presentation/src/test/java/com/sypark/flightdeal/feed/DealFeedViewModelTest.kt`
- Modify: `presentation/src/main/java/com/sypark/flightdeal/feed/DealFeedViewModel.kt`

**Interfaces:**
- Consumes: 기존 `FlightPriceRepository`, `GetDealFeedUseCase`, `DealFeedViewModel`
- Produces:
  - `enum class TripType { ROUND_TRIP, ONE_WAY }`
  - `FlightPriceRepository.cheapestDeals(origin: Airport, limit: Int, tripType: TripType): AppResult<List<PriceQuote>>`
  - `GetDealFeedUseCase.invoke(origin: Airport, tripType: TripType, limit: Int = 20): AppResult<List<DealItem>>`

- [ ] **Step 1: `TripType` 작성**

`domain/src/main/java/com/sypark/flightdeal/domain/model/TripType.kt`:

```kotlin
package com.sypark.flightdeal.domain.model

/**
 * 왕복이 기본이다. 여행자가 실제로 사는 형태이고, 가격 추적도 왕복 기준이어야 의미가 있다.
 * 같은 화면에 둘을 섞지 않는다 — 10만원(편도)과 30만원(왕복)이 나란히 놓이면 비교가 무의미하다.
 */
enum class TripType { ROUND_TRIP, ONE_WAY }
```

- [ ] **Step 2: Repository 인터페이스에 파라미터 추가**

`FlightPriceRepository.kt`의 `cheapestDeals` 시그니처를 바꾼다. 나머지 두 메서드는 그대로 둔다.

```kotlin
    /** 출발지 기준 특가 목록. 홈 피드용. */
    suspend fun cheapestDeals(
        origin: Airport,
        limit: Int,
        tripType: TripType,
    ): AppResult<List<PriceQuote>>
```

`com.sypark.flightdeal.domain.model.TripType` import를 추가한다.

- [ ] **Step 3: UseCase 테스트를 새 시그니처로 고치고 왕복 케이스 추가**

`GetDealFeedUseCaseTest.kt`의 `StubRepository`에서 `cheapestDeals`를 고친다.

```kotlin
        override suspend fun cheapestDeals(origin: Airport, limit: Int, tripType: TripType) = deals
```

기존 호출부 `useCase(incheon)`를 전부 `useCase(incheon, TripType.ROUND_TRIP)`로 바꾼다.
그리고 tripType이 그대로 전달되는지 확인하는 테스트를 추가한다.

```kotlin
    @Test
    fun `요청한 여정 종류를 Repository에 그대로 전달한다`() = runTest {
        var seen: TripType? = null
        val repo = object : FlightPriceRepository {
            override suspend fun cheapestDeals(origin: Airport, limit: Int, tripType: TripType):
                AppResult<List<PriceQuote>> {
                seen = tripType
                return AppResult.Success(listOf(quote(189_000)))
            }
            override suspend fun calendarPrices(route: Route, month: YearMonth):
                AppResult<List<PriceQuote>> = AppResult.Empty
            override suspend fun priceStats(route: Route, month: YearMonth):
                AppResult<PriceStats> = AppResult.Empty
        }
        val useCase = GetDealFeedUseCase(repo, CalculateDiscountUseCase())

        useCase(incheon, TripType.ONE_WAY)

        assertEquals(TripType.ONE_WAY, seen)
    }
```

`com.sypark.flightdeal.domain.model.TripType` import를 추가한다.

- [ ] **Step 4: 테스트 실패 확인**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :domain:test
```

기대: 컴파일 실패. `GetDealFeedUseCase.invoke`가 아직 `tripType`을 받지 않는다.

- [ ] **Step 5: UseCase 수정**

`GetDealFeedUseCase.kt`:

```kotlin
    suspend operator fun invoke(
        origin: Airport,
        tripType: TripType,
        limit: Int = DEFAULT_LIMIT,
    ): AppResult<List<DealItem>> {
        return when (val deals = repository.cheapestDeals(origin, limit, tripType)) {
            is AppResult.Success -> AppResult.Success(attachDiscounts(deals.data))
            AppResult.Empty -> AppResult.Empty
            is AppResult.NetworkError -> deals
            is AppResult.Unknown -> deals
        }
    }
```

`TripType` import를 추가한다. `attachDiscounts`는 건드리지 않는다.

- [ ] **Step 6: Fake 구현체 수정**

`FakeFlightPriceRepository.kt`의 `cheapestDeals`에 파라미터를 받고, 편도면 `returnDate`를 지운다.

```kotlin
    override suspend fun cheapestDeals(
        origin: Airport,
        limit: Int,
        tripType: TripType,
    ): AppResult<List<PriceQuote>> = respond {
        FakeDealFixtures.deals()
            .take(limit)
            .map { if (tripType == TripType.ONE_WAY) it.copy(returnDate = null) else it }
            .takeIf { it.isNotEmpty() }
    }
```

`TripType` import를 추가한다.

- [ ] **Step 7: ViewModel과 그 테스트 수정**

`DealFeedViewModel.kt`에서 호출부를 고친다. 토글 UI는 Task 7에서 붙이므로 지금은 왕복 고정이다.

```kotlin
                when (val result = getDealFeed(Airport.INCHEON, TripType.ROUND_TRIP)) {
```

`DealFeedViewModelTest.kt`의 세 로컬 Repository(`SlowFirstRepository`, `ThrowingRepository`,
`UnknownErrorRepository`)에서 `cheapestDeals` 시그니처에 `tripType: TripType`을 추가한다.

- [ ] **Step 8: 전체 테스트**

```bash
./gradlew :domain:test :data:test :presentation:testDebugUnitTest :presentation:assembleDebug
```

기대: `:domain` 32(31+1), `:data` 20, `:presentation` 10. BUILD SUCCESSFUL.

- [ ] **Step 9: 커밋**

```bash
git add domain data presentation
git commit -m "feat: 왕복·편도 구분을 도메인에 추가"
```

---

## Task 2: 네트워크 계층 설정과 API 인터페이스

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `data/build.gradle.kts`
- Create: `data/src/main/java/com/sypark/flightdeal/data/remote/dto/PricesForDatesResponse.kt`
- Create: `data/src/main/java/com/sypark/flightdeal/data/remote/TravelpayoutsApi.kt`
- Create: `data/src/main/java/com/sypark/flightdeal/data/di/NetworkModule.kt`

**Interfaces:**
- Consumes: `BuildConfig.TRAVELPAYOUTS_TOKEN`, `BuildConfig.TRAVELPAYOUTS_MARKER` (`:data`에 이미 선언돼 있다)
- Produces:
  - `PricesForDatesResponse(success: Boolean, data: List<PriceDto>?, currency: String?)`
  - `PriceDto` — 아래 Step 2 참조
  - `TravelpayoutsApi.pricesForDates(...): PricesForDatesResponse`
  - Hilt가 제공하는 `TravelpayoutsApi`

- [ ] **Step 1: Version Catalog와 build 파일**

`gradle/libs.versions.toml`의 `[versions]`에 추가한다.

```toml
retrofit = "2.11.0"
okhttp = "4.12.0"
gson = "2.11.0"
```

`[libraries]`에 추가한다.

```toml
retrofit = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
retrofit-converter-gson = { module = "com.squareup.retrofit2:converter-gson", version.ref = "retrofit" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-logging = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }
gson = { module = "com.google.code.gson:gson", version.ref = "gson" }
```

`data/build.gradle.kts`의 `dependencies`에 추가한다.

```kotlin
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.gson)
    debugImplementation(libs.okhttp.logging)

    testImplementation(libs.okhttp.mockwebserver)
```

`debugImplementation`으로 두는 이유: 로깅 인터셉터가 릴리스 빌드에 들어가면 토큰이 붙은
URL이 로그캣에 그대로 찍힌다.

- [ ] **Step 2: DTO 작성**

`data/src/main/java/com/sypark/flightdeal/data/remote/dto/PricesForDatesResponse.kt`.
필드 이름은 실제 응답 그대로다. 검증 문서 §응답 스키마에 근거가 있다.

```kotlin
package com.sypark.flightdeal.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PricesForDatesResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: List<PriceDto>? = null,
    @SerializedName("currency") val currency: String? = null,
)

/**
 * 실제 응답 필드를 그대로 옮긴다. 31건 전부 모든 필드가 채워져 오지만,
 * API가 언제든 필드를 빠뜨릴 수 있으므로 nullable로 받고 매퍼에서 판단한다.
 *
 * [returnAt]은 편도 조회 시 아예 오지 않는다.
 * [link]는 `/search/...` 형태의 상대 경로다.
 * [airline]은 `"ZE"` 같은 IATA 코드이지 사람이 읽을 이름이 아니다.
 */
data class PriceDto(
    @SerializedName("origin") val origin: String? = null,
    @SerializedName("destination") val destination: String? = null,
    @SerializedName("origin_airport") val originAirport: String? = null,
    @SerializedName("destination_airport") val destinationAirport: String? = null,
    @SerializedName("departure_at") val departureAt: String? = null,
    @SerializedName("return_at") val returnAt: String? = null,
    @SerializedName("price") val price: Int? = null,
    @SerializedName("airline") val airline: String? = null,
    @SerializedName("flight_number") val flightNumber: String? = null,
    @SerializedName("gate") val gate: String? = null,
    @SerializedName("transfers") val transfers: Int? = null,
    @SerializedName("return_transfers") val returnTransfers: Int? = null,
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("link") val link: String? = null,
)
```

- [ ] **Step 3: API 인터페이스 작성**

`data/src/main/java/com/sypark/flightdeal/data/remote/TravelpayoutsApi.kt`:

```kotlin
package com.sypark.flightdeal.data.remote

import com.sypark.flightdeal.data.remote.dto.PricesForDatesResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface TravelpayoutsApi {

    /**
     * 한 노선·한 달의 날짜별 최저가.
     *
     * @param departureAt `"2026-10"` 형태. 날짜까지 주면 그날만 온다.
     * @param returnAt 왕복일 때만 준다. 편도면 null.
     * @param oneWay false면 왕복. [returnAt]과 함께 줘야 한다.
     */
    @GET("aviasales/v3/prices_for_dates")
    suspend fun pricesForDates(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("departure_at") departureAt: String,
        @Query("return_at") returnAt: String? = null,
        @Query("one_way") oneWay: Boolean = true,
        @Query("currency") currency: String = "krw",
        @Query("sorting") sorting: String = "price",
        @Query("limit") limit: Int = 1000,
    ): PricesForDatesResponse
}
```

토큰은 쿼리에 두지 않는다. 매 호출마다 붙이는 것을 잊을 수 있으므로 인터셉터가 붙인다.

- [ ] **Step 4: NetworkModule 작성**

`data/src/main/java/com/sypark/flightdeal/data/di/NetworkModule.kt`:

```kotlin
package com.sypark.flightdeal.data.di

import com.sypark.flightdeal.data.BuildConfig
import com.sypark.flightdeal.data.remote.TravelpayoutsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://api.travelpayouts.com/"

    /**
     * 피드 한 번에 (노선 수 + 1)개의 요청이 병렬로 나간다. limit 기본값이 20이므로
     * 최대 21개다. 레이트 리밋이 걸린 API에 그대로 쏟으면 429를 맞는다.
     * 동시 실행 상한은 HTTP 사정이므로 도메인이 아니라 여기서 조인다.
     */
    private const val MAX_REQUESTS_PER_HOST = 4

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .dispatcher(
            Dispatcher().apply {
                maxRequestsPerHost = MAX_REQUESTS_PER_HOST
                maxRequests = MAX_REQUESTS_PER_HOST
            }
        )
        .addInterceptor { chain ->
            // 토큰을 매 호출마다 붙이는 것을 잊지 않도록 한 곳에 모은다.
            val url = chain.request().url.newBuilder()
                .addQueryParameter("token", BuildConfig.TRAVELPAYOUTS_TOKEN)
                .build()
            chain.proceed(chain.request().newBuilder().url(url).build())
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideTravelpayoutsApi(retrofit: Retrofit): TravelpayoutsApi =
        retrofit.create(TravelpayoutsApi::class.java)
}
```

- [ ] **Step 5: 빌드 확인**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :data:assembleDebug
```

기대: BUILD SUCCESSFUL. `com.sypark.flightdeal.data.BuildConfig`가 해석되지 않으면
`data/build.gradle.kts`에 `buildFeatures { buildConfig = true }`가 있는지 확인한다.

- [ ] **Step 6: 커밋**

```bash
git add gradle data
git commit -m "build: Travelpayouts 네트워크 계층 설정 추가"
```

---

## Task 3: 항공사명·예약처 정책·딥링크

매퍼가 쓰는 세 조각을 먼저 만든다. 각각 순수 함수라 JVM 테스트로 끝난다.

**Files:**
- Create: `data/src/main/java/com/sypark/flightdeal/data/remote/AirlineNames.kt`
- Create: `data/src/main/java/com/sypark/flightdeal/data/remote/GatePolicy.kt`
- Create: `data/src/main/java/com/sypark/flightdeal/data/remote/DeepLinkBuilder.kt`
- Test: `data/src/test/java/com/sypark/flightdeal/data/remote/GatePolicyTest.kt`
- Test: `data/src/test/java/com/sypark/flightdeal/data/remote/DeepLinkBuilderTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `AirlineNames.of(iata: String?): String?`
  - `GatePolicy.prioritize(items: List<T>, gateOf: (T) -> String?, minCount: Int): List<T>`
  - `DeepLinkBuilder.build(relativeLink: String?, marker: String): String?`

- [ ] **Step 1: 항공사명 표 작성**

`data/src/main/java/com/sypark/flightdeal/data/remote/AirlineNames.kt`.
API는 `"ZE"` 같은 IATA 코드를 준다. 화면에 그대로 띄우면 아무도 못 알아본다.

```kotlin
package com.sypark.flightdeal.data.remote

/**
 * IATA 항공사 코드를 한국어 이름으로. 인천 출발 노선에 실제로 나타나는 것 위주다.
 * 표에 없으면 코드를 그대로 돌려준다 — 빈칸보다는 낫다.
 */
object AirlineNames {

    private val NAMES = mapOf(
        // 국적사
        "KE" to "대한항공", "OZ" to "아시아나항공", "TW" to "티웨이항공",
        "LJ" to "진에어", "7C" to "제주항공", "ZE" to "이스타항공",
        "BX" to "에어부산", "RS" to "에어서울", "RF" to "에어프레미아",
        // 일본
        "JL" to "일본항공", "NH" to "전일본공수", "MM" to "피치항공",
        // 동남아
        "TG" to "타이항공", "VJ" to "비엣젯항공", "VN" to "베트남항공",
        "SQ" to "싱가포르항공", "PR" to "필리핀항공", "MH" to "말레이시아항공",
        "GA" to "가루다인도네시아", "AK" to "에어아시아",
        // 중화권
        "CI" to "중화항공", "BR" to "에바항공", "CX" to "캐세이퍼시픽",
        "HX" to "홍콩항공", "UO" to "홍콩익스프레스",
        "CZ" to "중국남방항공", "MU" to "중국동방항공", "CA" to "중국국제항공",
    )

    fun of(iata: String?): String? = iata?.let { NAMES[it] ?: it }
}
```

- [ ] **Step 2: 예약처 정책 테스트 작성**

`data/src/test/java/com/sypark/flightdeal/data/remote/GatePolicyTest.kt`:

```kotlin
package com.sypark.flightdeal.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class GatePolicyTest {

    private data class Row(val gate: String?, val id: Int)

    private fun prioritize(rows: List<Row>, minCount: Int) =
        GatePolicy.prioritize(rows, { it.gate }, minCount).map { it.id }

    @Test
    fun `한국에서 쓸 수 있는 예약처를 앞으로 보낸다`() {
        val rows = listOf(
            Row("Kupi.com", 1), Row("Trip.com", 2), Row("Aviakassa", 3), Row("Kiwi.com", 4),
        )
        assertEquals(listOf(2, 4, 1, 3), prioritize(rows, minCount = 4))
    }

    @Test
    fun `허용 예약처가 충분하면 나머지는 버린다`() {
        val rows = listOf(
            Row("Trip.com", 1), Row("Kiwi.com", 2), Row("Aviakassa", 3), Row("Kupi.com", 4),
        )
        assertEquals(listOf(1, 2), prioritize(rows, minCount = 2))
    }

    @Test
    fun `허용 예약처가 모자라면 나머지로 채운다`() {
        val rows = listOf(
            Row("Trip.com", 1), Row("Aviakassa", 2), Row("Kupi.com", 3),
        )
        // 화면이 텅 비는 것보다 낫다. 한산한 노선에서 실제로 일어난다.
        assertEquals(listOf(1, 2, 3), prioritize(rows, minCount = 3))
    }

    @Test
    fun `허용 예약처가 하나도 없어도 빈 목록을 돌려주지 않는다`() {
        val rows = listOf(Row("Aviakassa", 1), Row("Kupi.com", 2))
        assertEquals(listOf(1, 2), prioritize(rows, minCount = 2))
    }

    @Test
    fun `예약처가 null이면 허용 목록으로 치지 않는다`() {
        val rows = listOf(Row(null, 1), Row("Trip.com", 2))
        assertEquals(listOf(2, 1), prioritize(rows, minCount = 2))
    }

    @Test
    fun `빈 입력은 빈 출력이다`() {
        assertEquals(emptyList<Int>(), prioritize(emptyList(), minCount = 5))
    }
}
```

세 번째와 네 번째 테스트가 이 정책의 핵심이다. 완전 차단이 아니라 **우선순위 + 보충**이다.
다낭처럼 데이터가 얇은 노선에서 화면이 비는 것을 막는다.

- [ ] **Step 3: 테스트 실패 확인**

```bash
./gradlew :data:test --tests "*GatePolicyTest*"
```

기대: 컴파일 실패. `Unresolved reference: GatePolicy`

- [ ] **Step 4: 예약처 정책 구현**

`data/src/main/java/com/sypark/flightdeal/data/remote/GatePolicy.kt`:

```kotlin
package com.sypark.flightdeal.data.remote

/**
 * Aviasales는 러시아에서 출발한 서비스라 예약처 구성이 CIS 시장에 치우쳐 있다.
 * Aviakassa, Kupi.com, Biletix, Clickavia, Авиасейлс 같은 곳은 한국 사용자가
 * 결제까지 가기 어렵다. 실측으로 노선당 절반 조금 넘게가 여기 해당했다.
 *
 * 그렇다고 완전히 걸러내면 한산한 노선은 화면이 빈다. 그래서 우선순위를 주되,
 * 모자라면 나머지로 채운다.
 */
object GatePolicy {

    /** 한국에서 실제로 예약까지 이어지는 예약처. 확인되는 대로 늘린다. */
    private val PREFERRED = setOf("Trip.com", "Kiwi.com")

    /**
     * [PREFERRED]에 해당하는 항목을 앞으로 보내고, 그것만으로 [minCount]를 채우지 못하면
     * 나머지를 원래 순서대로 뒤에 붙인다.
     */
    fun <T> prioritize(items: List<T>, gateOf: (T) -> String?, minCount: Int): List<T> {
        val (preferred, rest) = items.partition { gateOf(it) in PREFERRED }
        if (preferred.size >= minCount) return preferred
        return preferred + rest
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
./gradlew :data:test --tests "*GatePolicyTest*"
```

기대: PASS (6건)

- [ ] **Step 6: 딥링크 테스트 작성**

`data/src/test/java/com/sypark/flightdeal/data/remote/DeepLinkBuilderTest.kt`:

```kotlin
package com.sypark.flightdeal.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkBuilderTest {

    @Test
    fun `상대 경로에 도메인과 마커를 붙인다`() {
        val url = DeepLinkBuilder.build("/search/ICN0610TYO1?t=abc", marker = "123456")!!
        assertTrue(url.startsWith("https://www.aviasales.com/search/ICN0610TYO1?t=abc"))
        assertTrue(url.contains("marker=123456"))
    }

    @Test
    fun `쿼리가 없는 경로에도 마커를 붙인다`() {
        val url = DeepLinkBuilder.build("/search/ICN0610TYO1", marker = "123456")!!
        assertEquals("https://www.aviasales.com/search/ICN0610TYO1?marker=123456", url)
    }

    @Test
    fun `마커가 비어 있으면 붙이지 않는다`() {
        // 마커 미발급 상태에서도 링크 자체는 동작해야 한다. 커미션만 안 붙는다.
        val url = DeepLinkBuilder.build("/search/ICN0610TYO1?t=abc", marker = "")!!
        assertEquals("https://www.aviasales.com/search/ICN0610TYO1?t=abc", url)
    }

    @Test
    fun `링크가 없으면 null이다`() {
        assertNull(DeepLinkBuilder.build(null, marker = "123456"))
    }

    @Test
    fun `이미 절대 URL이면 도메인을 덧붙이지 않는다`() {
        val url = DeepLinkBuilder.build("https://www.aviasales.com/search/x", marker = "1")!!
        assertEquals("https://www.aviasales.com/search/x?marker=1", url)
    }
}
```

- [ ] **Step 7: 테스트 실패 확인**

```bash
./gradlew :data:test --tests "*DeepLinkBuilderTest*"
```

기대: 컴파일 실패. `Unresolved reference: DeepLinkBuilder`

- [ ] **Step 8: 딥링크 구현**

`data/src/main/java/com/sypark/flightdeal/data/remote/DeepLinkBuilder.kt`:

```kotlin
package com.sypark.flightdeal.data.remote

/**
 * API의 `link`는 `/search/ICN0610TYO1?t=...` 형태의 상대 경로다.
 * 도메인을 붙여야 열리고, marker를 붙여야 커미션이 계정에 잡힌다.
 */
object DeepLinkBuilder {

    private const val BASE = "https://www.aviasales.com"

    fun build(relativeLink: String?, marker: String): String? {
        if (relativeLink.isNullOrBlank()) return null

        val absolute = if (relativeLink.startsWith("http")) relativeLink else BASE + relativeLink
        if (marker.isBlank()) return absolute

        val separator = if (absolute.contains('?')) '&' else '?'
        return "$absolute${separator}marker=$marker"
    }
}
```

- [ ] **Step 9: 테스트 통과 확인**

```bash
./gradlew :data:test
```

기대: PASS (기존 20 + GatePolicy 6 + DeepLink 5 = 31건)

- [ ] **Step 10: 커밋**

```bash
git add data
git commit -m "feat: 항공사명 표와 예약처 우선순위, 딥링크 조립 추가"
```

---

## Task 4: DTO → 도메인 매퍼

실제 응답 픽스처로 검증한다. 이 태스크가 계획서 전체에서 가장 중요하다.

**Files:**
- Create: `data/src/main/java/com/sypark/flightdeal/data/remote/PriceQuoteMapper.kt`
- Test: `data/src/test/java/com/sypark/flightdeal/data/remote/PriceQuoteMapperTest.kt`
- Create: `data/src/test/resources/fixtures/v3-ICN-TYO.json` (복사)
- Create: `data/src/test/resources/fixtures/v3-ICN-DAD.json` (복사)
- Create: `data/src/test/resources/fixtures/v3-ICN-TYO-roundtrip.json` (복사)

**Interfaces:**
- Consumes: `PriceDto`, `AirlineNames`, `DeepLinkBuilder`, `Won`, `Airport`, `Route`, `PriceQuote`
- Produces: `PriceQuoteMapper.toDomain(dto: PriceDto, foundAt: Instant, marker: String): PriceQuote?`

- [ ] **Step 1: 픽스처를 테스트 리소스로 복사**

```bash
cd /Users/sypark/AndroidStudioProjects/flightdeal
mkdir -p data/src/test/resources/fixtures
cp docs/research/fixtures/v3-ICN-TYO.json data/src/test/resources/fixtures/
cp docs/research/fixtures/v3-ICN-DAD.json data/src/test/resources/fixtures/
cp docs/research/fixtures/v3-ICN-TYO-roundtrip.json data/src/test/resources/fixtures/
```

`docs/research/fixtures/`의 원본은 그대로 둔다. 근거 자료이고, 테스트 리소스는 사본이다.

- [ ] **Step 2: 매퍼 테스트 작성**

`data/src/test/java/com/sypark/flightdeal/data/remote/PriceQuoteMapperTest.kt`:

```kotlin
package com.sypark.flightdeal.data.remote

import com.google.gson.Gson
import com.sypark.flightdeal.data.remote.dto.PriceDto
import com.sypark.flightdeal.data.remote.dto.PricesForDatesResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PriceQuoteMapperTest {

    private val foundAt = Instant.parse("2026-08-28T00:00:00Z")

    private fun load(name: String): PricesForDatesResponse =
        Gson().fromJson(
            javaClass.getResourceAsStream("/fixtures/$name")!!.reader(),
            PricesForDatesResponse::class.java,
        )

    @Test
    fun `실제 편도 응답을 전부 변환한다`() {
        val response = load("v3-ICN-TYO.json")
        assertTrue(response.success)

        val quotes = response.data!!.mapNotNull {
            PriceQuoteMapper.toDomain(it, foundAt, marker = "123456")
        }

        // 31건 전부 필수 필드가 채워져 있음을 실측으로 확인했다.
        assertEquals(31, quotes.size)
    }

    @Test
    fun `첫 항목의 값이 응답과 일치한다`() {
        val dto = load("v3-ICN-TYO.json").data!!.first()
        val quote = PriceQuoteMapper.toDomain(dto, foundAt, marker = "123456")!!

        assertEquals("ICN", quote.route.origin.iata)
        assertEquals("TYO", quote.route.destination.iata)
        assertEquals(dto.price, quote.price.amount)
        assertEquals(LocalDate.parse(dto.departureAt!!.substring(0, 10)), quote.departDate)
        assertEquals(foundAt, quote.foundAt)
        assertNotNull(quote.deepLink)
        assertTrue(quote.deepLink!!.contains("marker=123456"))
    }

    @Test
    fun `편도 응답은 귀국일이 없다`() {
        val dto = load("v3-ICN-TYO.json").data!!.first()
        assertNull(PriceQuoteMapper.toDomain(dto, foundAt, "1")!!.returnDate)
    }

    @Test
    fun `왕복 응답은 귀국일이 채워진다`() {
        val dto = load("v3-ICN-TYO-roundtrip.json").data!!.first()
        val quote = PriceQuoteMapper.toDomain(dto, foundAt, "1")!!

        assertNotNull(quote.returnDate)
        assertTrue(quote.returnDate!!.isAfter(quote.departDate))
    }

    @Test
    fun `항공사 코드를 한국어 이름으로 바꾼다`() {
        val dto = PriceDto(
            originAirport = "ICN", destination = "TYO",
            departureAt = "2026-10-06T15:15:00+09:00", price = 100354,
            airline = "KE", link = "/search/x",
        )
        assertEquals("대한항공", PriceQuoteMapper.toDomain(dto, foundAt, "1")!!.airline)
    }

    @Test
    fun `모르는 항공사 코드는 코드를 그대로 쓴다`() {
        val dto = PriceDto(
            originAirport = "ICN", destination = "TYO",
            departureAt = "2026-10-06T15:15:00+09:00", price = 100354,
            airline = "XX", link = "/search/x",
        )
        assertEquals("XX", PriceQuoteMapper.toDomain(dto, foundAt, "1")!!.airline)
    }

    @Test
    fun `가격이 없으면 버린다`() {
        val dto = PriceDto(
            originAirport = "ICN", destination = "TYO",
            departureAt = "2026-10-06T15:15:00+09:00", price = null, link = "/search/x",
        )
        assertNull(PriceQuoteMapper.toDomain(dto, foundAt, "1"))
    }

    @Test
    fun `출발일이 없으면 버린다`() {
        val dto = PriceDto(
            originAirport = "ICN", destination = "TYO",
            departureAt = null, price = 100354, link = "/search/x",
        )
        assertNull(PriceQuoteMapper.toDomain(dto, foundAt, "1"))
    }

    @Test
    fun `출발일 형식이 깨졌으면 예외를 던지지 않고 버린다`() {
        val dto = PriceDto(
            originAirport = "ICN", destination = "TYO",
            departureAt = "not-a-date", price = 100354, link = "/search/x",
        )
        assertNull(PriceQuoteMapper.toDomain(dto, foundAt, "1"))
    }

    @Test
    fun `데이터가 얇은 노선도 변환된다`() {
        val response = load("v3-ICN-DAD.json")
        val quotes = response.data!!.mapNotNull { PriceQuoteMapper.toDomain(it, foundAt, "1") }
        assertEquals(20, quotes.size)
    }
}
```

깨진 날짜에서 예외 대신 null을 돌려주게 하는 것이 중요하다. 한 건이 이상하다고
피드 전체가 오류로 바뀌면 안 된다.

- [ ] **Step 3: 테스트 실패 확인**

```bash
./gradlew :data:test --tests "*PriceQuoteMapperTest*"
```

기대: 컴파일 실패. `Unresolved reference: PriceQuoteMapper`

- [ ] **Step 4: 매퍼 구현**

`data/src/main/java/com/sypark/flightdeal/data/remote/PriceQuoteMapper.kt`:

```kotlin
package com.sypark.flightdeal.data.remote

import com.sypark.flightdeal.data.remote.dto.PriceDto
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.Won
import java.time.Instant
import java.time.LocalDate

object PriceQuoteMapper {

    /**
     * @param foundAt 응답에 관측 시각이 없으므로 조회 시각을 넣는다.
     * @return 필수 필드가 빠졌거나 형식이 깨진 항목은 null. 한 건이 이상하다고
     *   피드 전체를 오류로 만들지 않는다.
     */
    fun toDomain(dto: PriceDto, foundAt: Instant, marker: String): PriceQuote? {
        val price = dto.price ?: return null
        val departDate = parseDate(dto.departureAt) ?: return null
        val originIata = dto.originAirport ?: dto.origin ?: return null
        val destinationIata = dto.destination ?: dto.destinationAirport ?: return null

        return PriceQuote(
            route = Route(
                origin = Airport(originIata, AirportNames.cityOf(originIata), ""),
                destination = Airport(destinationIata, AirportNames.cityOf(destinationIata), ""),
            ),
            departDate = departDate,
            returnDate = parseDate(dto.returnAt),
            price = Won(price),
            airline = AirlineNames.of(dto.airline),
            foundAt = foundAt,
            deepLink = DeepLinkBuilder.build(dto.link, marker),
        )
    }

    /** ISO 8601 앞 10자가 날짜다. 형식이 깨지면 null. */
    private fun parseDate(raw: String?): LocalDate? {
        if (raw == null || raw.length < 10) return null
        return runCatching { LocalDate.parse(raw.substring(0, 10)) }.getOrNull()
    }
}
```

- [ ] **Step 5: 도시 이름 표 작성**

매퍼가 `AirportNames.cityOf`를 쓴다. `AirlineNames.kt` 옆에 같은 형태로 만든다.

`data/src/main/java/com/sypark/flightdeal/data/remote/AirportNames.kt`:

```kotlin
package com.sypark.flightdeal.data.remote

/**
 * IATA 코드를 한국어 도시명으로. 화면에 "TYO" 대신 "도쿄"가 뜨게 한다.
 * 표에 없으면 코드를 그대로 돌려준다.
 */
object AirportNames {

    private val CITIES = mapOf(
        "ICN" to "서울", "SEL" to "서울", "GMP" to "서울", "PUS" to "부산", "CJU" to "제주",
        "TYO" to "도쿄", "NRT" to "도쿄", "HND" to "도쿄",
        "OSA" to "오사카", "KIX" to "오사카", "FUK" to "후쿠오카", "CTS" to "삿포로",
        "OKA" to "오키나와", "NGO" to "나고야",
        "BKK" to "방콕", "DMK" to "방콕", "HKT" to "푸껫", "CNX" to "치앙마이",
        "DAD" to "다낭", "SGN" to "호치민", "HAN" to "하노이", "PQC" to "푸꾸옥",
        "TPE" to "타이베이", "KHH" to "가오슝",
        "HKG" to "홍콩", "MFM" to "마카오",
        "SIN" to "싱가포르", "KUL" to "쿠알라룸푸르", "CEB" to "세부", "MNL" to "마닐라",
        "DPS" to "발리", "BKI" to "코타키나발루",
        "PEK" to "베이징", "PVG" to "상하이", "SHA" to "상하이", "CAN" to "광저우",
        "GUM" to "괌", "SPN" to "사이판",
    )

    fun cityOf(iata: String): String = CITIES[iata] ?: iata
}
```

- [ ] **Step 6: 테스트 통과 확인**

```bash
./gradlew :data:test
```

기대: PASS (31 + 매퍼 10 = 41건)

- [ ] **Step 7: 커밋**

```bash
git add data
git commit -m "feat: Travelpayouts 응답을 도메인 모델로 변환하는 매퍼 추가"
```

---

## Task 5: 실 Repository 구현체

**Files:**
- Create: `data/src/main/java/com/sypark/flightdeal/data/remote/TravelpayoutsFlightPriceRepository.kt`
- Test: `data/src/test/java/com/sypark/flightdeal/data/remote/TravelpayoutsFlightPriceRepositoryTest.kt`

**Interfaces:**
- Consumes: `TravelpayoutsApi`, `PriceQuoteMapper`, `GatePolicy`, `FlightPriceRepository`, `TripType`
- Produces: `TravelpayoutsFlightPriceRepository(api, marker, clock)` implementing `FlightPriceRepository`

- [ ] **Step 1: 테스트 작성**

MockWebServer로 실제 픽스처를 돌려주게 하고, 오류 상태 코드도 재현한다.

`data/src/test/java/com/sypark/flightdeal/data/remote/TravelpayoutsFlightPriceRepositoryTest.kt`:

```kotlin
package com.sypark.flightdeal.data.remote

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TripType
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

class TravelpayoutsFlightPriceRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: TravelpayoutsFlightPriceRepository

    private val clock = Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC)
    private val incheon = Airport("ICN", "서울", "대한민국")
    private val route = Route(incheon, Airport("TYO", "도쿄", "일본"))

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TravelpayoutsApi::class.java)
        // 목적지를 하나로 고정한다. 기본값 6개를 쓰면 요청이 6번 나가 큐가 모자란다.
        repository = TravelpayoutsFlightPriceRepository(
            api = api, marker = "123456", clock = clock, destinations = listOf("TYO"),
        )
    }

    @After
    fun tearDown() = server.shutdown()

    private fun enqueueFixture(name: String) {
        val body = javaClass.getResourceAsStream("/fixtures/$name")!!.readBytes().decodeToString()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
    }

    @Test
    fun `실제 응답을 특가 목록으로 변환한다`() = runTest {
        enqueueFixture("v3-ICN-TYO.json")

        val result = repository.cheapestDeals(incheon, limit = 10, tripType = TripType.ONE_WAY)

        assertTrue(result is AppResult.Success)
        val deals = (result as AppResult.Success).data
        // 목적지 하나당 그 달의 최저가 하나. destinations를 TYO 하나로 줬으므로 1건이다.
        assertEquals(1, deals.size)
        assertTrue(deals.all { it.deepLink!!.contains("marker=123456") })
    }

    @Test
    fun `왕복 요청은 one_way false와 return_at을 보낸다`() = runTest {
        enqueueFixture("v3-ICN-TYO-roundtrip.json")

        repository.cheapestDeals(incheon, limit = 5, tripType = TripType.ROUND_TRIP)

        val url = server.takeRequest().requestUrl!!
        assertEquals("false", url.queryParameter("one_way"))
        assertTrue(url.queryParameter("return_at") != null)
    }

    @Test
    fun `편도 요청은 one_way true를 보내고 return_at을 보내지 않는다`() = runTest {
        enqueueFixture("v3-ICN-TYO.json")

        repository.cheapestDeals(incheon, limit = 5, tripType = TripType.ONE_WAY)

        val url = server.takeRequest().requestUrl!!
        assertEquals("true", url.queryParameter("one_way"))
        assertEquals(null, url.queryParameter("return_at"))
    }

    @Test
    fun `빈 결과는 Empty다`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true,"data":[]}"""))

        val result = repository.cheapestDeals(incheon, limit = 10, tripType = TripType.ONE_WAY)

        assertEquals(AppResult.Empty, result)
    }

    @Test
    fun `success가 false면 Empty로 취급하지 않고 Unknown이다`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"error":"bad request","data":null,"status":400,"success":false}""")
        )

        val result = repository.cheapestDeals(incheon, limit = 10, tripType = TripType.ONE_WAY)

        assertTrue(result is AppResult.Unknown)
    }

    @Test
    fun `401은 Unknown이다 재시도해도 소용없다`() = runTest {
        // 실제 API는 401에 JSON이 아니라 평문 Unauthorized를 돌려준다.
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        val result = repository.cheapestDeals(incheon, limit = 10, tripType = TripType.ONE_WAY)

        assertTrue(result is AppResult.Unknown)
    }

    @Test
    fun `연결이 끊기면 NetworkError다 재시도할 만하다`() = runTest {
        server.shutdown()

        val result = repository.cheapestDeals(incheon, limit = 10, tripType = TripType.ONE_WAY)

        assertTrue(result is AppResult.NetworkError)
    }

    @Test
    fun `분포는 그 달의 가격들로 계산한다`() = runTest {
        enqueueFixture("v3-ICN-TYO.json")

        val result = repository.priceStats(route, YearMonth.of(2026, 10))

        assertTrue(result is AppResult.Success)
        assertEquals(31, (result as AppResult.Success).data.sampleCount)
    }

    @Test
    fun `캘린더 조회는 그 달의 날짜별 가격을 돌려준다`() = runTest {
        enqueueFixture("v3-ICN-TYO.json")

        val result = repository.calendarPrices(route, YearMonth.of(2026, 10))

        assertEquals(31, (result as AppResult.Success).data.size)
    }
}
```

401을 `NetworkError`가 아니라 `Unknown`으로 보내는 이유가 있다. `NetworkError`는 화면에
재시도 버튼을 띄우는데, 토큰이 잘못됐으면 백 번 눌러도 같은 결과다.

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :data:test --tests "*TravelpayoutsFlightPriceRepositoryTest*"
```

기대: 컴파일 실패. `Unresolved reference: TravelpayoutsFlightPriceRepository`

- [ ] **Step 3: 구현체 작성**

`data/src/main/java/com/sypark/flightdeal/data/remote/TravelpayoutsFlightPriceRepository.kt`:

```kotlin
package com.sypark.flightdeal.data.remote

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.PriceStats
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.repository.FlightPriceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class TravelpayoutsFlightPriceRepository(
    private val api: TravelpayoutsApi,
    private val marker: String,
    private val clock: Clock,
    private val destinations: List<String> = DEFAULT_DESTINATIONS,
) : FlightPriceRepository {

    /** 예약처는 특가 목록을 고를 때만 쓴다. 도메인 밖으로 새 나가지 않는다. */
    private data class GatedQuote(val gate: String?, val quote: PriceQuote)

    override suspend fun cheapestDeals(
        origin: Airport,
        limit: Int,
        tripType: TripType,
    ): AppResult<List<PriceQuote>> = call {
        val month = YearMonth.now(clock).plusMonths(LEAD_MONTHS)

        // 목적지마다 그 달의 최저가 하나씩. sorting=price로 요청하므로 첫 항목이 가장 싸다.
        val candidates = destinations.mapNotNull { destination ->
            fetch(origin.iata, destination, month, tripType).firstOrNull()
        }

        GatePolicy.prioritize(candidates, { it.gate }, minCount = limit)
            .take(limit)
            .map { it.quote }
    }

    override suspend fun calendarPrices(
        route: Route,
        month: YearMonth,
    ): AppResult<List<PriceQuote>> = call {
        // 캘린더는 그날의 최저가를 보여주는 화면이다. 예약처로 거르지 않는다.
        fetch(route.origin.iata, route.destination.iata, month, TripType.ONE_WAY)
            .map { it.quote }
    }

    override suspend fun priceStats(route: Route, month: YearMonth): AppResult<PriceStats> {
        return when (val prices = calendarPrices(route, month)) {
            is AppResult.Success ->
                PriceStats.from(prices.data.map { it.price })
                    ?.let { AppResult.Success(it) }
                    ?: AppResult.Empty
            AppResult.Empty -> AppResult.Empty
            is AppResult.NetworkError -> prices
            is AppResult.Unknown -> prices
        }
    }

    /**
     * 예약처를 quote에 붙여서 돌려준다. DTO와 도메인 객체를 따로 들고 다니다
     * 인덱스로 짝지으면, 변환에 실패한 항목이 하나만 있어도 전부 어긋난다.
     */
    private suspend fun fetch(
        originIata: String,
        destinationIata: String,
        month: YearMonth,
        tripType: TripType,
    ): List<GatedQuote> {
        val monthText = month.format(MONTH_FORMAT)
        val roundTrip = tripType == TripType.ROUND_TRIP

        val response = api.pricesForDates(
            origin = originIata,
            destination = destinationIata,
            departureAt = monthText,
            returnAt = if (roundTrip) monthText else null,
            oneWay = !roundTrip,
        )
        if (!response.success) throw IllegalStateException("API returned success=false")

        val foundAt = clock.instant()
        return response.data.orEmpty().mapNotNull { dto ->
            PriceQuoteMapper.toDomain(dto, foundAt, marker)?.let { GatedQuote(dto.gate, it) }
        }
    }

    /**
     * 예외를 [AppResult]로 옮긴다.
     *
     * 연결 실패만 [AppResult.NetworkError]다 — 재시도할 가치가 있는 유일한 경우다.
     * 401(토큰 오류)이나 400(잘못된 노선)은 재시도해도 결과가 같으므로 [AppResult.Unknown]이다.
     */
    private suspend fun <T> call(block: suspend () -> List<T>): AppResult<List<T>> =
        withContext(Dispatchers.IO) {
            try {
                block().let { if (it.isEmpty()) AppResult.Empty else AppResult.Success(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                AppResult.NetworkError(e)
            } catch (e: HttpException) {
                AppResult.Unknown(e)
            } catch (e: Exception) {
                AppResult.Unknown(e)
            }
        }

    companion object {
        private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

        /** 지금 사면 비싸다. 두 달 뒤가 특가가 나오는 구간이다. */
        private const val LEAD_MONTHS = 2L

        /** 피드에 띄울 인기 목적지. 설정 화면이 생기면 사용자가 고르게 한다. */
        val DEFAULT_DESTINATIONS = listOf("TYO", "BKK", "DAD", "TPE", "HKG", "SIN")
    }
}
```

`LocalDate` import는 쓰이지 않으므로 넣지 않는다.

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew :data:test
```

기대: PASS (41 + Repository 9 = 50건)

- [ ] **Step 5: 커밋**

```bash
git add data
git commit -m "feat: Travelpayouts Repository 구현체 추가"
```

---

## Task 6: 바인딩 교체와 실기기 확인

**Files:**
- Modify: `data/src/main/java/com/sypark/flightdeal/data/di/RepositoryModule.kt`

**Interfaces:**
- Consumes: `TravelpayoutsApi` (Task 2), `TravelpayoutsFlightPriceRepository` (Task 5)
- Produces: 실데이터로 동작하는 앱

- [ ] **Step 1: 바인딩 교체**

`RepositoryModule.kt`:

```kotlin
package com.sypark.flightdeal.data.di

import com.sypark.flightdeal.data.BuildConfig
import com.sypark.flightdeal.data.remote.TravelpayoutsApi
import com.sypark.flightdeal.data.remote.TravelpayoutsFlightPriceRepository
import com.sypark.flightdeal.domain.repository.FlightPriceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Fake는 지우지 않는다. 테스트와 오프라인 개발에 계속 쓴다.
 * 여기 반환값만 바꾸면 앱 전체가 Fake와 실데이터 사이를 오간다.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideFlightPriceRepository(api: TravelpayoutsApi): FlightPriceRepository =
        TravelpayoutsFlightPriceRepository(
            api = api,
            marker = BuildConfig.TRAVELPAYOUTS_MARKER,
            clock = Clock.systemDefaultZone(),
        )
}
```

**이 파일 하나만 바뀐다.** 화면도 도메인도 손대지 않는다. 그것이 이 아키텍처의 요점이다.

- [ ] **Step 2: 빌드와 전체 테스트**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :domain:test :data:test :presentation:testDebugUnitTest :presentation:assembleDebug
```

기대: 전체 PASS, BUILD SUCCESSFUL.

- [ ] **Step 3: 에뮬레이터에서 실데이터 확인**

```bash
~/Library/Android/sdk/platform-tools/adb devices   # 없으면 Pixel_API_33 부팅
./gradlew :presentation:installDebug
~/Library/Android/sdk/platform-tools/adb shell am start -n com.sypark.flightdeal/.MainActivity
~/Library/Android/sdk/platform-tools/adb exec-out screencap -p > .superpowers/sdd/task-6-real-data.png
```

확인할 것:

| 항목 | 기대 |
|---|---|
| 카드 | 도쿄·방콕·다낭 등 실제 목적지, **Fake의 189,000원이 아닌 값** |
| 항공사 | "대한항공", "티웨이항공" 등 한국어. `"KE"` 같은 코드가 보이면 매퍼가 안 걸린 것 |
| 배지 | 목적지마다 다른 할인율 |
| 참고가 문구 | 검색바 아래에 그대로 |

로그캣도 본다. 401이 나면 `local.properties`의 토큰을 확인한다.

```bash
~/Library/Android/sdk/platform-tools/adb logcat -d -s DealFeed:* | tail -20
```

**스크린샷을 못 찍었으면 그렇다고 보고한다. 보지 않은 화면을 묘사하지 않는다.**

- [ ] **Step 4: 커밋**

```bash
git add data
git commit -m "feat: 특가 피드를 Travelpayouts 실데이터로 전환"
```

---

## Task 7: 왕복·편도 토글

**Files:**
- Modify: `presentation/src/main/java/com/sypark/flightdeal/feed/DealFeedViewModel.kt`
- Modify: `presentation/src/main/java/com/sypark/flightdeal/feed/DealFeedScreen.kt`
- Modify: `presentation/src/test/java/com/sypark/flightdeal/feed/DealFeedViewModelTest.kt`

**Interfaces:**
- Consumes: `TripType` (Task 1), `DealFeedViewModel` (기존)
- Produces: `DealFeedViewModel.tripType: StateFlow<TripType>`, `DealFeedViewModel.setTripType(TripType)`

- [ ] **Step 1: ViewModel 테스트 작성**

`DealFeedViewModelTest.kt`에 추가한다.

```kotlin
    @Test
    fun `기본은 왕복이다`() = runTest {
        val viewModel = viewModel(FakeFlightPriceRepository.Behavior.Normal)

        assertEquals(TripType.ROUND_TRIP, viewModel.tripType.value)
    }

    @Test
    fun `여정 종류를 바꾸면 다시 조회한다`() = runTest {
        val repo = CountingRepository()
        val viewModel = DealFeedViewModel(GetDealFeedUseCase(repo, CalculateDiscountUseCase()))
        advanceUntilIdle()
        val before = repo.calls

        viewModel.setTripType(TripType.ONE_WAY)
        advanceUntilIdle()

        assertEquals(TripType.ONE_WAY, viewModel.tripType.value)
        assertEquals(before + 1, repo.calls)
    }

    @Test
    fun `같은 여정 종류를 다시 고르면 조회하지 않는다`() = runTest {
        val repo = CountingRepository()
        val viewModel = DealFeedViewModel(GetDealFeedUseCase(repo, CalculateDiscountUseCase()))
        advanceUntilIdle()
        val before = repo.calls

        viewModel.setTripType(TripType.ROUND_TRIP)
        advanceUntilIdle()

        assertEquals(before, repo.calls)
    }
```

테스트용 Repository를 같은 파일에 추가한다.

```kotlin
    private inner class CountingRepository : FlightPriceRepository {
        var calls = 0
            private set

        override suspend fun cheapestDeals(
            origin: Airport,
            limit: Int,
            tripType: TripType,
        ): AppResult<List<PriceQuote>> {
            calls++
            return AppResult.Success(listOf(quote(189_000)))
        }

        override suspend fun calendarPrices(route: Route, month: YearMonth):
            AppResult<List<PriceQuote>> = AppResult.Empty

        override suspend fun priceStats(route: Route, month: YearMonth):
            AppResult<PriceStats> = AppResult.Empty
    }
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :presentation:testDebugUnitTest --tests "*DealFeedViewModelTest*"
```

기대: 컴파일 실패. `Unresolved reference: tripType`

- [ ] **Step 3: ViewModel 수정**

`DealFeedViewModel.kt`에 추가한다.

```kotlin
    private val _tripType = MutableStateFlow(TripType.ROUND_TRIP)
    val tripType: StateFlow<TripType> = _tripType.asStateFlow()

    /** 같은 값이면 조회하지 않는다. 토글을 두 번 눌렀다고 왕복을 다시 받을 이유가 없다. */
    fun setTripType(tripType: TripType) {
        if (_tripType.value == tripType) return
        _tripType.value = tripType
        refresh()
    }
```

`refresh()` 안의 호출부를 고친다.

```kotlin
                when (val result = getDealFeed(Airport.INCHEON, _tripType.value)) {
```

`TripType` import를 추가한다.

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew :presentation:testDebugUnitTest
```

기대: PASS (10 + 3 = 13건)

- [ ] **Step 5: 토글 UI 작성**

`DealFeedScreen.kt`의 참고가 캡션 아래, 목록 위에 넣는다.

```kotlin
        val tripType by viewModel.tripType.collectAsStateWithLifecycle()

        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TripTypeChip(
                label = "왕복",
                selected = tripType == TripType.ROUND_TRIP,
                onClick = { viewModel.setTripType(TripType.ROUND_TRIP) },
            )
            TripTypeChip(
                label = "편도",
                selected = tripType == TripType.ONE_WAY,
                onClick = { viewModel.setTripType(TripType.ONE_WAY) },
            )
        }
```

같은 파일 아래에 칩을 정의한다.

```kotlin
@Composable
private fun TripTypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.White else TextSecondary,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .background(
                color = if (selected) Indigo else Surface,
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}
```

`androidx.compose.foundation.clickable`, `androidx.compose.ui.graphics.Color`,
`com.sypark.flightdeal.ui.theme.Indigo`, `Surface` import를 추가한다.

- [ ] **Step 6: 에뮬레이터에서 확인**

```bash
./gradlew :presentation:installDebug
~/Library/Android/sdk/platform-tools/adb shell am start -n com.sypark.flightdeal/.MainActivity
```

확인할 것:

| 항목 | 기대 |
|---|---|
| 초기 상태 | "왕복"이 인디고 배경 + 흰 글씨, "편도"는 회색 |
| 편도 탭 | 스켈레톤이 잠깐 뜨고 **더 싼 가격**으로 바뀐다 (편도이므로) |
| 왕복 복귀 | 다시 비싼 값으로 |
| 연타 | 같은 칩을 두 번 눌러도 다시 로딩하지 않는다 |

스크린샷 두 장을 `.superpowers/sdd/task-7-roundtrip.png`, `task-7-oneway.png`로 남긴다.

- [ ] **Step 7: 커밋**

```bash
git add presentation
git commit -m "feat: 특가 피드에 왕복·편도 전환 추가"
```

---

## 완료 기준

- [ ] `RepositoryModule` 한 파일만 바꿔서 Fake ↔ 실데이터가 전환된다
- [ ] 실제 Travelpayouts 데이터가 화면에 뜬다. 항공사가 한국어로 나온다
- [ ] 왕복이 기본이고 편도 토글이 동작한다
- [ ] 401·400은 재시도 버튼 없는 오류, 연결 실패는 재시도 가능한 오류로 구분된다
- [ ] 예약처 우선순위가 적용되되 한산한 노선에서 화면이 비지 않는다
- [ ] 딥링크에 marker가 붙는다 (마커 미발급 상태에서도 링크는 열린다)
- [ ] `:domain`에 Travelpayouts라는 단어가 없다
- [ ] `.java` 파일이 하나도 없다
- [ ] 전체 테스트 통과. `:domain` 32, `:data` 50, `:presentation` 13

## 다음 계획서

- **계획서 3 (spec 5~6단계):** Room 가격 이력, 추적 목록/상세, 스파크라인, `PriceCheckWorker`, 알림, `POST_NOTIFICATIONS` 권한
- **계획서 4 (spec 7단계):** 날짜별 최저가 캘린더, 목적지 탐색, Custom Tabs 딥링크

## 미해결로 남긴 것

- **네트워크 오류 시 기존 목록이 사라진다.** spec §8은 캐시 데이터를 유지하고 스낵바로만 알리라고 한다. 현재 4상태 모델로는 "콘텐츠 + 일시적 오류"를 표현할 수 없다. 화면이 더 늘기 전에 결정한다
- **마커 미발급.** 대시보드 좌측 하단 Partner ID. 없어도 링크는 열리고 커미션만 안 붙는다
- **다크 팔레트 없음.** 라이트 고정
