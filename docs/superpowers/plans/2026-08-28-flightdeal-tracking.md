# 가격 추적과 알림 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사용자가 노선을 추적 등록하면 6시간마다 가격을 확인해 변동 시 알림을 보낸다.

**Architecture:** Room에 추적 노선과 가격 이력을 쌓는다. `PriceCheckWorker`가 주기적으로 깨어나 `FlightPriceRepository`로 현재가를 조회하고, `DetectPriceChangesUseCase`(이미 있음, 테스트 9건)로 판정한 뒤 알림을 묶어 보낸다. 화면·도메인 계층의 기존 구조는 그대로 쓴다.

**Tech Stack:** Room · WorkManager · Hilt(hilt-work) · Coroutines/Flow · Jetpack Compose · KSP

## 이 계획서가 답하는 질문

앱의 출발점이었던 "항공권 가격이 바뀌는 걸 알 수 있나"에 실제로 답하는 단계다.
실시간 푸시는 존재하지 않으므로 **폴링 + 가격 이력 + 임계치 판정**으로 만든다.

## 앞 단계에서 이미 있는 것

새로 만들지 말 것. 전부 테스트가 붙어 있다.

| | 위치 | 비고 |
|---|---|---|
| `DetectPriceChangesUseCase` | `:domain` usecase | 순수 함수, 테스트 9건. 알림 판정 로직 전부가 여기 있다 |
| `TrackedRoute`, `PriceSnapshot`, `PriceChange`, `Direction` | `:domain` model | Task 1에서 `TripType`을 추가한다 |
| `FlightPriceRepository` | `:domain` repository | `cheapestDeals` / `calendarPrices` / `priceStats`, 셋 다 `tripType`을 받는다 |
| `TravelpayoutsFlightPriceRepository` | `:data` remote | 병렬 조회, 5분 캐시, 오류 매핑 완료 |
| `AirportNames`, `AirlineNames` | `:data` remote | IATA → 한국어 |
| `formatWon` | `:presentation` feed | `"189,000원"` |
| `DealCard`, `DealFeedScreen`, `FlightDealNavHost` | `:presentation` | Compose |

현재 테스트: `:domain` 33, `:data` 56, `:presentation` 14. **`:data`는 debug/release 유닛테스트를
둘 다 돌린다. `testDebugUnitTest`만 센다** — 합산하면 두 배가 된다.

## Global Constraints

- 패키지 루트: `com.sypark.flightdeal`; compileSdk/targetSdk 36, minSdk 26; Java 17
- **Java 소스 파일을 만들지 않는다.** 프로젝트에 `.java` 파일이 하나도 없어야 한다
- `:domain`은 아무것도 의존하지 않는다. 안드로이드 프레임워크 타입도, **Travelpayouts라는 단어도** 등장하면 안 된다. `:data`는 `:domain`만 의존한다
- 비동기는 Coroutines/Flow로 통일한다. RxJava 금지, LiveData 금지
- 어노테이션 처리는 KSP를 쓴다. kapt 금지
- 가격은 `Won` value class로 다룬다. raw `Int`로 가격을 주고받지 않는다
- 의존성은 전부 `gradle/libs.versions.toml`에 선언한다. 모듈 build 파일에 버전 문자열을 직접 쓰지 않는다
- 강조색 `#4338E0`, 강조 배경 `#EDEBFF`, 배경 `#FFFFFF`, 서피스 `#F4F5F9`, 경계선 `#EAECF3`, 본문 `#0F1115`, 보조 텍스트 `#8A8FA3`. **가격 하락은 항상 초록 `#0E9E6E`, 상승은 항상 빨강 `#D93A3A`** — 브랜드 강조색과 섞지 않는다
- 커밋 메시지: `feat/fix/build/chore/ci/docs/style/refactor/test/perf` 접두사 + 한국어 제목. **커밋에 Claude를 참여자로 기록하지 않는다**
- `local.properties`는 커밋하지 않는다. **저장소는 공개다** — 토큰이 어떤 파일에도 들어가면 안 된다
- 빌드에 JDK 17이 필요하다: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`

### 추가할 버전

```toml
room = "2.6.1"
work = "2.10.0"
hiltWork = "1.2.0"
```

해석에 실패하면 최신 안정 버전을 확인해 올리고, 무엇을 왜 바꿨는지 보고한다.

---

## File Structure

| 파일 | 책임 |
|---|---|
| `:domain` `model/TrackedRoute.kt` (수정) | `tripType` 추가 |
| `:domain` `model/PriceSnapshot.kt` (수정) | `tripType` 추가 |
| `:domain` `model/Airport.kt` (수정) | IATA만으로 동일성 판정 |
| `:domain` `repository/TrackedRouteRepository.kt` | 추적 노선 CRUD |
| `:domain` `repository/PriceHistoryRepository.kt` | 가격 이력 |
| `:domain` `usecase/TrackRouteUseCase.kt` | 등록 + 첫 스냅샷 |
| `:domain` `usecase/UntrackRouteUseCase.kt` | 해제 + 이력 삭제 |
| `:domain` `usecase/CheckTrackedPricesUseCase.kt` | 워커가 부르는 유일한 진입점 |
| `:data` `local/FlightDealDatabase.kt` | Room DB |
| `:data` `local/entity/TrackedRouteEntity.kt` | 추적 노선 테이블 |
| `:data` `local/entity/PriceSnapshotEntity.kt` | 이력 테이블 |
| `:data` `local/TrackedRouteDao.kt` | 추적 노선 DAO |
| `:data` `local/PriceSnapshotDao.kt` | 이력 DAO |
| `:data` `local/RoomTrackedRouteRepository.kt` | 인터페이스 구현 |
| `:data` `local/RoomPriceHistoryRepository.kt` | 인터페이스 구현 |
| `:data` `di/DatabaseModule.kt` | Room 제공 |
| `:presentation` `tracking/TrackingViewModel.kt` | 추적 목록 상태 |
| `:presentation` `tracking/TrackingScreen.kt` | 추적 목록 화면 |
| `:presentation` `tracking/TrackedRouteCard.kt` | 추적 항목 카드 |
| `:presentation` `worker/PriceCheckWorker.kt` | 6시간 주기 조회 |
| `:presentation` `worker/PriceChangeNotifier.kt` | 알림 발송 |
| `:presentation` `worker/WorkScheduler.kt` | 워커 등록/해제 |

`worker`를 `:presentation`에 두는 이유: `WorkManager`와 `NotificationManager`는 안드로이드
프레임워크이고, 알림 문구는 화면 문구와 같은 성격이다. `:data`에 두면 데이터 계층이
사용자에게 말을 걸게 된다.

---

## Task 1: 여정 종류를 저장하고 노선 동일성을 고친다

실연동 때 겪은 버그가 저장 계층에서 그대로 재현되는 것을 막는 준비 작업이다.
이걸 나중에 하면 Room 마이그레이션까지 곁들여야 한다.

**Files:**
- Modify: `domain/src/main/java/com/sypark/flightdeal/domain/model/TrackedRoute.kt`
- Modify: `domain/src/main/java/com/sypark/flightdeal/domain/model/PriceSnapshot.kt`
- Modify: `domain/src/main/java/com/sypark/flightdeal/domain/model/Airport.kt`
- Modify: `domain/src/test/java/com/sypark/flightdeal/domain/usecase/DetectPriceChangesUseCaseTest.kt`
- Test: `domain/src/test/java/com/sypark/flightdeal/domain/model/AirportTest.kt`

**Interfaces:**
- Consumes: 기존 `TrackedRoute`, `PriceSnapshot`, `Airport`, `Route`
- Produces:
  - `TrackedRoute(id, route, departDate, returnDate, tripType: TripType, targetPrice, createdAt)`
  - `PriceSnapshot(trackedRouteId, price, tripType: TripType, capturedAt)`
  - `Airport` — `iata`만으로 `equals`/`hashCode`

### 왜 필요한가

**하나.** 지금 여정 종류는 `returnDate != null`로만 구분되고 한 번의 화면 로드 동안만 산다.
Room에 이력을 쌓고 `DetectPriceChangesUseCase`로 비교하기 시작하면 **왕복 스냅샷과 편도
스냅샷을 견주어 가짜 "가격 하락" 알림**을 쏜다. 편도는 왕복의 3분의 1 수준이므로 매번
"−60% 하락!"이 뜬다. 실연동에서 같은 성격의 버그를 이미 겪었다 — 분포를 편도로 계산해
할인 배지가 영영 안 뜬 건. **응답에서 추론하지 말고 요청에서 받아 저장한다.**

**둘.** `Airport`가 `data class`라 `cityKo`/`countryKo`까지 동일성에 들어간다. 매퍼가 만든
`Airport("ICN", "서울", "")`와 `Airport.INCHEON`(`countryKo = "대한민국"`)은 같지 않다.
지금은 매퍼가 만든 것끼리만 비교해서 드러나지 않지만, 워커가 조회한 노선을 저장된
`TrackedRoute`와 맞춰보는 순간 영영 매칭되지 않는다. 공항의 정체성은 IATA 코드다.

- [ ] **Step 1: `Airport` 동일성 테스트 작성**

`domain/src/test/java/com/sypark/flightdeal/domain/model/AirportTest.kt`:

```kotlin
package com.sypark.flightdeal.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AirportTest {

    @Test
    fun `IATA가 같으면 표시 이름이 달라도 같은 공항이다`() {
        // 매퍼는 국가명을 채우지 않고, 상수는 채운다. 그래도 같은 인천이다.
        assertEquals(Airport("ICN", "서울", ""), Airport("ICN", "서울", "대한민국"))
    }

    @Test
    fun `IATA가 같으면 해시도 같다`() {
        assertEquals(
            Airport("ICN", "서울", "").hashCode(),
            Airport("ICN", "인천", "대한민국").hashCode(),
        )
    }

    @Test
    fun `IATA가 다르면 다른 공항이다`() {
        assertNotEquals(Airport("ICN", "서울", ""), Airport("GMP", "서울", ""))
    }

    @Test
    fun `Map 키로 쓸 수 있다`() {
        val map = mapOf(Airport("ICN", "서울", "") to 1)

        // 워커가 조회한 공항으로 저장된 추적 노선을 찾을 수 있어야 한다.
        assertEquals(1, map[Airport.INCHEON])
    }

    @Test
    fun `노선도 IATA로만 비교된다`() {
        val a = Route(Airport("ICN", "서울", ""), Airport("TYO", "도쿄", ""))
        val b = Route(Airport.INCHEON, Airport("TYO", "동경", "일본"))

        assertEquals(a, b)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :domain:test --tests "*AirportTest*"
```

기대: `IATA가 같으면 표시 이름이 달라도 같은 공항이다`가 FAIL.
`data class`의 기본 `equals`가 세 필드를 모두 비교한다.

- [ ] **Step 3: `Airport` 동일성 구현**

`Airport.kt`를 아래로 바꾼다. `Route`는 그대로 둔다 — `Airport`가 고쳐지면
`Route`의 `data class` 동일성도 따라서 IATA 기준이 된다.

```kotlin
package com.sypark.flightdeal.domain.model

/**
 * 공항의 정체성은 IATA 코드다. [cityKo]와 [countryKo]는 화면에 쓰는 표시용이며
 * 출처에 따라 다르게 채워진다 — 매퍼는 국가명을 비우고, 상수는 채운다.
 * 그 차이로 같은 공항이 서로 다른 것이 되면 저장된 추적 노선을 영영 찾지 못한다.
 */
class Airport(
    val iata: String,
    val cityKo: String,
    val countryKo: String,
) {
    override fun equals(other: Any?): Boolean = other is Airport && other.iata == iata

    override fun hashCode(): Int = iata.hashCode()

    override fun toString(): String = "Airport($iata, $cityKo)"

    companion object {
        /** 기본 출발지. 설정 화면이 생기면 DataStore에서 읽어온다. */
        val INCHEON = Airport("ICN", "서울", "대한민국")
    }
}

data class Route(
    val origin: Airport,
    val destination: Airport,
)
```

`data class`를 버리므로 `copy`가 사라진다. `Airport`를 `copy`하는 곳이 있으면
생성자 호출로 바꾼다.

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew :domain:test --tests "*AirportTest*"
```

기대: PASS (5건)

- [ ] **Step 5: 모델에 `tripType` 추가**

`TrackedRoute.kt`:

```kotlin
package com.sypark.flightdeal.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * @param tripType 왕복과 편도는 가격대가 세 배쯤 차이 난다. 어느 쪽으로 등록했는지
 *   기억하지 않으면 다음 조회에서 다른 종류를 받아와 가짜 변동으로 읽힌다.
 *   등록 요청에서 받아 저장한다 — 응답에서 추론하지 않는다.
 */
data class TrackedRoute(
    val id: Long,
    val route: Route,
    val departDate: LocalDate,
    val returnDate: LocalDate?,
    val tripType: TripType,
    val targetPrice: Won?,
    val createdAt: Instant,
)
```

`PriceSnapshot.kt`의 `PriceSnapshot`만 바꾼다. `Direction`과 `PriceChange`는 그대로 둔다.

```kotlin
/**
 * @param tripType 이 가격이 어떤 종류의 운임이었는지. 추적 항목의 종류가 바뀔 수 있으므로
 *   스냅샷도 자기 종류를 들고 있어야 한다. 종류가 다른 스냅샷끼리 비교하면
 *   매번 "60% 하락"이 뜬다.
 */
data class PriceSnapshot(
    val trackedRouteId: Long,
    val price: Won,
    val tripType: TripType,
    val capturedAt: Instant,
)
```

- [ ] **Step 6: 기존 테스트를 새 시그니처에 맞춤**

`DetectPriceChangesUseCaseTest.kt`의 `tracked(...)`와 `snapshot(...)` 헬퍼에
`tripType = TripType.ROUND_TRIP`을 추가한다. **단언은 하나도 바꾸지 않는다.**

`com.sypark.flightdeal.domain.model.TripType` import를 추가한다.

- [ ] **Step 7: 전체 테스트**

```bash
./gradlew :domain:test :data:testDebugUnitTest :presentation:testDebugUnitTest :presentation:assembleDebug
```

기대: `:domain` 38(33+5), `:data` 56, `:presentation` 14. BUILD SUCCESSFUL.

`:data`나 `:presentation`이 깨지면 `Airport`를 `copy`하거나 세 필드 동일성에 기대던
곳이 있다는 뜻이다. 어느 파일인지 보고한다.

- [ ] **Step 8: 커밋**

```bash
git add domain
git commit -m "feat: 추적 항목에 여정 종류를 저장하고 공항 동일성을 IATA 기준으로 변경"
```

---

## Task 2: Room 저장소

**Files:**
- Modify: `gradle/libs.versions.toml`, `data/build.gradle.kts`
- Create: `data/src/main/java/com/sypark/flightdeal/data/local/entity/TrackedRouteEntity.kt`
- Create: `data/src/main/java/com/sypark/flightdeal/data/local/entity/PriceSnapshotEntity.kt`
- Create: `data/src/main/java/com/sypark/flightdeal/data/local/TrackedRouteDao.kt`
- Create: `data/src/main/java/com/sypark/flightdeal/data/local/PriceSnapshotDao.kt`
- Create: `data/src/main/java/com/sypark/flightdeal/data/local/FlightDealDatabase.kt`
- Create: `data/src/main/java/com/sypark/flightdeal/data/di/DatabaseModule.kt`
- Test: `data/src/test/java/com/sypark/flightdeal/data/local/DaoTest.kt`

**Interfaces:**
- Consumes: 없음 (엔티티는 도메인 모델과 독립적이다)
- Produces:
  - `TrackedRouteEntity(id, originIata, destinationIata, departDate, returnDate, tripType, targetPrice, createdAt)` — 전부 원시 타입/문자열
  - `PriceSnapshotEntity(id, trackedRouteId, price, tripType, capturedAt)`
  - `TrackedRouteDao` — `observeAll(): Flow<List<TrackedRouteEntity>>`, `getAll()`, `insert(entity): Long`, `update(entity)`, `deleteById(id)`
  - `PriceSnapshotDao` — `insert(entity)`, `observeFor(trackedRouteId, sinceEpochSecond): Flow<List<PriceSnapshotEntity>>`, `latestFor(trackedRouteId)`, `deleteOlderThan(epochSecond)`, `deleteForRoute(trackedRouteId)`
  - `FlightDealDatabase`

**엔티티에 도메인 타입을 넣지 않는다.** `Won`도 `TripType`도 `LocalDate`도 쓰지 않고
`Int`/`String`/`Long`으로 저장한다. 타입 컨버터를 두면 도메인 모델을 바꿀 때마다
스키마가 흔들린다. 변환은 Repository 구현체의 몫이다(Task 3).

- [ ] **Step 1: 의존성 추가**

`gradle/libs.versions.toml`의 `[versions]`:

```toml
room = "2.6.1"
```

`[libraries]`:

```toml
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
robolectric = { module = "org.robolectric:robolectric", version = "4.14.1" }
```

`data/build.gradle.kts`의 `dependencies`:

```kotlin
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
```

`android { }` 블록에 추가한다. Robolectric이 없으면 Room DAO를 JVM 테스트로 돌릴 수 없다.

```kotlin
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
```

- [ ] **Step 2: 엔티티 작성**

`data/src/main/java/com/sypark/flightdeal/data/local/entity/TrackedRouteEntity.kt`:

```kotlin
package com.sypark.flightdeal.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 도메인 타입을 쓰지 않는다. 스키마가 도메인 모델의 변경에 끌려다니면
 * 모델을 손볼 때마다 마이그레이션을 써야 한다.
 *
 * @param departDate ISO-8601 `"2026-10-12"`
 * @param tripType [com.sypark.flightdeal.domain.model.TripType]의 `name`
 * @param createdAt epoch second
 */
@Entity(tableName = "tracked_route")
data class TrackedRouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originIata: String,
    val destinationIata: String,
    val departDate: String,
    val returnDate: String?,
    val tripType: String,
    val targetPrice: Int?,
    val createdAt: Long,
)
```

`data/src/main/java/com/sypark/flightdeal/data/local/entity/PriceSnapshotEntity.kt`:

```kotlin
package com.sypark.flightdeal.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 추적 항목이 사라지면 그 이력도 함께 사라진다. 앱 코드가 지우는 것을 잊어도
 * DB가 보장하도록 외래키에 맡긴다.
 */
@Entity(
    tableName = "price_snapshot",
    foreignKeys = [
        ForeignKey(
            entity = TrackedRouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackedRouteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trackedRouteId")],
)
data class PriceSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackedRouteId: Long,
    val price: Int,
    val tripType: String,
    val capturedAt: Long,
)
```

- [ ] **Step 3: DAO 작성**

`data/src/main/java/com/sypark/flightdeal/data/local/TrackedRouteDao.kt`:

```kotlin
package com.sypark.flightdeal.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.sypark.flightdeal.data.local.entity.TrackedRouteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedRouteDao {

    @Query("SELECT * FROM tracked_route ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TrackedRouteEntity>>

    /** 워커용. Flow가 아니라 한 번만 읽는다. */
    @Query("SELECT * FROM tracked_route")
    suspend fun getAll(): List<TrackedRouteEntity>

    @Insert
    suspend fun insert(entity: TrackedRouteEntity): Long

    @Update
    suspend fun update(entity: TrackedRouteEntity)

    @Query("DELETE FROM tracked_route WHERE id = :id")
    suspend fun deleteById(id: Long)
}
```

`data/src/main/java/com/sypark/flightdeal/data/local/PriceSnapshotDao.kt`:

```kotlin
package com.sypark.flightdeal.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sypark.flightdeal.data.local.entity.PriceSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceSnapshotDao {

    @Insert
    suspend fun insert(entity: PriceSnapshotEntity)

    @Query(
        "SELECT * FROM price_snapshot WHERE trackedRouteId = :trackedRouteId " +
            "AND capturedAt >= :sinceEpochSecond ORDER BY capturedAt ASC"
    )
    fun observeFor(trackedRouteId: Long, sinceEpochSecond: Long): Flow<List<PriceSnapshotEntity>>

    @Query(
        "SELECT * FROM price_snapshot WHERE trackedRouteId = :trackedRouteId " +
            "ORDER BY capturedAt DESC LIMIT 1"
    )
    suspend fun latestFor(trackedRouteId: Long): PriceSnapshotEntity?

    /** 이력은 계속 쌓인다. 워커가 돌 때 함께 치운다. */
    @Query("DELETE FROM price_snapshot WHERE capturedAt < :epochSecond")
    suspend fun deleteOlderThan(epochSecond: Long)
}
```

`deleteForRoute`는 두지 않는다. 외래키의 `CASCADE`가 처리한다.

- [ ] **Step 4: 데이터베이스와 Hilt 모듈 작성**

`data/src/main/java/com/sypark/flightdeal/data/local/FlightDealDatabase.kt`:

```kotlin
package com.sypark.flightdeal.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sypark.flightdeal.data.local.entity.PriceSnapshotEntity
import com.sypark.flightdeal.data.local.entity.TrackedRouteEntity

@Database(
    entities = [TrackedRouteEntity::class, PriceSnapshotEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FlightDealDatabase : RoomDatabase() {
    abstract fun trackedRouteDao(): TrackedRouteDao
    abstract fun priceSnapshotDao(): PriceSnapshotDao
}
```

`data/src/main/java/com/sypark/flightdeal/data/di/DatabaseModule.kt`:

```kotlin
package com.sypark.flightdeal.data.di

import android.content.Context
import androidx.room.Room
import com.sypark.flightdeal.data.local.FlightDealDatabase
import com.sypark.flightdeal.data.local.PriceSnapshotDao
import com.sypark.flightdeal.data.local.TrackedRouteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FlightDealDatabase =
        Room.databaseBuilder(context, FlightDealDatabase::class.java, "flightdeal.db")
            // 외래키 CASCADE가 동작하려면 켜야 한다. 기본값은 꺼짐이다.
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
            .build()

    @Provides
    fun provideTrackedRouteDao(db: FlightDealDatabase): TrackedRouteDao = db.trackedRouteDao()

    @Provides
    fun providePriceSnapshotDao(db: FlightDealDatabase): PriceSnapshotDao = db.priceSnapshotDao()
}
```

- [ ] **Step 5: DAO 테스트 작성**

`data/src/test/java/com/sypark/flightdeal/data/local/DaoTest.kt`:

```kotlin
package com.sypark.flightdeal.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sypark.flightdeal.data.local.entity.PriceSnapshotEntity
import com.sypark.flightdeal.data.local.entity.TrackedRouteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DaoTest {

    private lateinit var db: FlightDealDatabase
    private lateinit var routes: TrackedRouteDao
    private lateinit var snapshots: PriceSnapshotDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlightDealDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        routes = db.trackedRouteDao()
        snapshots = db.priceSnapshotDao()
    }

    @After
    fun tearDown() = db.close()

    private fun route(destination: String = "TYO") = TrackedRouteEntity(
        originIata = "ICN",
        destinationIata = destination,
        departDate = "2026-10-12",
        returnDate = "2026-10-16",
        tripType = "ROUND_TRIP",
        targetPrice = 280_000,
        createdAt = 1_800_000_000L,
    )

    private fun snapshot(routeId: Long, price: Int, at: Long) = PriceSnapshotEntity(
        trackedRouteId = routeId,
        price = price,
        tripType = "ROUND_TRIP",
        capturedAt = at,
    )

    @Test
    fun `추적 노선을 넣고 관찰한다`() = runTest {
        routes.insert(route())

        assertEquals(1, routes.observeAll().first().size)
    }

    @Test
    fun `가장 최근 스냅샷을 돌려준다`() = runTest {
        val id = routes.insert(route())
        snapshots.insert(snapshot(id, 300_000, at = 100))
        snapshots.insert(snapshot(id, 280_000, at = 200))

        assertEquals(280_000, snapshots.latestFor(id)!!.price)
    }

    @Test
    fun `이력이 없으면 최근 스냅샷은 null이다`() = runTest {
        val id = routes.insert(route())

        assertNull(snapshots.latestFor(id))
    }

    @Test
    fun `기간 이후의 이력만 오래된 순으로 돌려준다`() = runTest {
        val id = routes.insert(route())
        snapshots.insert(snapshot(id, 300_000, at = 100))
        snapshots.insert(snapshot(id, 290_000, at = 300))
        snapshots.insert(snapshot(id, 280_000, at = 200))

        val history = snapshots.observeFor(id, sinceEpochSecond = 150).first()

        // 그래프는 시간순이어야 한다.
        assertEquals(listOf(280_000, 290_000), history.map { it.price })
    }

    @Test
    fun `오래된 이력을 지운다`() = runTest {
        val id = routes.insert(route())
        snapshots.insert(snapshot(id, 300_000, at = 100))
        snapshots.insert(snapshot(id, 280_000, at = 500))

        snapshots.deleteOlderThan(epochSecond = 200)

        assertEquals(1, snapshots.observeFor(id, sinceEpochSecond = 0).first().size)
    }

    @Test
    fun `추적을 해제하면 이력도 함께 사라진다`() = runTest {
        val id = routes.insert(route())
        snapshots.insert(snapshot(id, 300_000, at = 100))

        routes.deleteById(id)

        // 외래키 CASCADE에 맡긴다. 앱 코드가 지우는 것을 잊어도 남지 않아야 한다.
        assertEquals(0, snapshots.observeFor(id, sinceEpochSecond = 0).first().size)
    }

    @Test
    fun `다른 노선의 이력은 섞이지 않는다`() = runTest {
        val tokyo = routes.insert(route("TYO"))
        val bangkok = routes.insert(route("BKK"))
        snapshots.insert(snapshot(tokyo, 300_000, at = 100))
        snapshots.insert(snapshot(bangkok, 200_000, at = 100))

        assertEquals(1, snapshots.observeFor(tokyo, sinceEpochSecond = 0).first().size)
    }
}
```

- [ ] **Step 6: 테스트 실행**

```bash
./gradlew :data:testDebugUnitTest --tests "*DaoTest*"
```

기대: PASS (7건).

`추적을 해제하면 이력도 함께 사라진다`가 실패하면 외래키 강제가 꺼진 것이다.
`Room.inMemoryDatabaseBuilder`는 기본으로 켜지 않는다 —
`.setJournalMode(...)` 대신 테스트 빌더에 `.build()` 후
`db.openHelper.writableDatabase.setForeignKeyConstraintsEnabled(true)`가 필요할 수 있다.
**테스트를 지우지 말고 어떤 설정이 필요했는지 보고한다.**

- [ ] **Step 7: 전체 테스트와 커밋**

```bash
./gradlew :domain:test :data:testDebugUnitTest :presentation:testDebugUnitTest :presentation:assembleDebug
```

기대: `:domain` 38, `:data` 63(56+7), `:presentation` 14.

```bash
git add gradle data
git commit -m "feat: 추적 노선과 가격 이력을 저장할 Room 스키마 추가"
```

---

## Task 3: 저장소 인터페이스와 Room 구현체

**Files:**
- Create: `domain/src/main/java/com/sypark/flightdeal/domain/repository/TrackedRouteRepository.kt`
- Create: `domain/src/main/java/com/sypark/flightdeal/domain/repository/PriceHistoryRepository.kt`
- Create: `data/src/main/java/com/sypark/flightdeal/data/local/RoomTrackedRouteRepository.kt`
- Create: `data/src/main/java/com/sypark/flightdeal/data/local/RoomPriceHistoryRepository.kt`
- Modify: `data/src/main/java/com/sypark/flightdeal/data/di/RepositoryModule.kt`
- Test: `data/src/test/java/com/sypark/flightdeal/data/local/RoomTrackedRouteRepositoryTest.kt`

**Interfaces:**
- Consumes: DAO들 (Task 2), `TrackedRoute`/`PriceSnapshot`/`TripType`/`Won`/`Route`/`Airport` (Task 1)
- Produces:
  - `TrackedRouteRepository` — `observeAll(): Flow<List<TrackedRoute>>`, `suspend getAll(): List<TrackedRoute>`, `suspend add(route, departDate, returnDate, tripType, targetPrice): Long`, `suspend remove(id: Long)`
  - `PriceHistoryRepository` — `suspend append(snapshot: PriceSnapshot)`, `suspend latest(trackedRouteId: Long): PriceSnapshot?`, `fun observeHistory(trackedRouteId: Long, days: Int): Flow<List<PriceSnapshot>>`, `suspend pruneOlderThan(days: Int)`

- [ ] **Step 1: 도메인 인터페이스 작성**

`domain/src/main/java/com/sypark/flightdeal/domain/repository/TrackedRouteRepository.kt`:

```kotlin
package com.sypark.flightdeal.domain.repository

import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface TrackedRouteRepository {

    fun observeAll(): Flow<List<TrackedRoute>>

    /** 워커용. 한 번만 읽는다. */
    suspend fun getAll(): List<TrackedRoute>

    /** @return 새로 만들어진 추적 항목의 id */
    suspend fun add(
        route: Route,
        departDate: LocalDate,
        returnDate: LocalDate?,
        tripType: TripType,
        targetPrice: Won?,
    ): Long

    suspend fun remove(id: Long)
}
```

`domain/src/main/java/com/sypark/flightdeal/domain/repository/PriceHistoryRepository.kt`:

```kotlin
package com.sypark.flightdeal.domain.repository

import com.sypark.flightdeal.domain.model.PriceSnapshot
import kotlinx.coroutines.flow.Flow

interface PriceHistoryRepository {

    suspend fun append(snapshot: PriceSnapshot)

    /** 직전 관측값. 변동 판정의 비교 대상이다. */
    suspend fun latest(trackedRouteId: Long): PriceSnapshot?

    fun observeHistory(trackedRouteId: Long, days: Int): Flow<List<PriceSnapshot>>

    /** 이력은 계속 쌓인다. 워커가 돌 때 함께 치운다. */
    suspend fun pruneOlderThan(days: Int)
}
```

- [ ] **Step 2: 구현체 테스트 작성**

`data/src/test/java/com/sypark/flightdeal/data/local/RoomTrackedRouteRepositoryTest.kt`:

```kotlin
package com.sypark.flightdeal.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class RoomTrackedRouteRepositoryTest {

    private lateinit var db: FlightDealDatabase
    private lateinit var tracked: RoomTrackedRouteRepository
    private lateinit var history: RoomPriceHistoryRepository

    private val clock = Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC)
    private val route = Route(Airport.INCHEON, Airport("TYO", "도쿄", "일본"))

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlightDealDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tracked = RoomTrackedRouteRepository(db.trackedRouteDao(), clock)
        history = RoomPriceHistoryRepository(db.priceSnapshotDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun addTokyo(tripType: TripType = TripType.ROUND_TRIP) = tracked.add(
        route = route,
        departDate = LocalDate.of(2026, 10, 12),
        returnDate = LocalDate.of(2026, 10, 16),
        tripType = tripType,
        targetPrice = Won(280_000),
    )

    @Test
    fun `등록한 노선을 도메인 모델로 되돌려준다`() = runTest {
        val id = addTokyo()

        val saved = tracked.observeAll().first().single()

        assertEquals(id, saved.id)
        assertEquals("ICN", saved.route.origin.iata)
        assertEquals("TYO", saved.route.destination.iata)
        assertEquals(LocalDate.of(2026, 10, 12), saved.departDate)
        assertEquals(LocalDate.of(2026, 10, 16), saved.returnDate)
        assertEquals(TripType.ROUND_TRIP, saved.tripType)
        assertEquals(Won(280_000), saved.targetPrice)
    }

    @Test
    fun `도시 이름을 표시할 수 있게 채워 돌려준다`() = runTest {
        addTokyo()

        val saved = tracked.observeAll().first().single()

        // DB에는 IATA만 있다. 화면은 "TYO"가 아니라 "도쿄"를 보여줘야 한다.
        assertEquals("도쿄", saved.route.destination.cityKo)
        assertEquals("서울", saved.route.origin.cityKo)
    }

    @Test
    fun `편도로 등록하면 편도로 저장된다`() = runTest {
        addTokyo(TripType.ONE_WAY)

        assertEquals(TripType.ONE_WAY, tracked.observeAll().first().single().tripType)
    }

    @Test
    fun `목표가를 안 정해도 등록된다`() = runTest {
        tracked.add(route, LocalDate.of(2026, 10, 12), null, TripType.ONE_WAY, targetPrice = null)

        val saved = tracked.observeAll().first().single()
        assertNull(saved.targetPrice)
        assertNull(saved.returnDate)
    }

    @Test
    fun `해제하면 목록에서 사라진다`() = runTest {
        val id = addTokyo()

        tracked.remove(id)

        assertEquals(0, tracked.observeAll().first().size)
    }

    @Test
    fun `스냅샷을 넣고 최근 값을 읽는다`() = runTest {
        val id = addTokyo()
        history.append(PriceSnapshot(id, Won(300_000), TripType.ROUND_TRIP, clock.instant()))
        history.append(
            PriceSnapshot(id, Won(280_000), TripType.ROUND_TRIP, clock.instant().plusSeconds(60))
        )

        val latest = history.latest(id)!!

        assertEquals(Won(280_000), latest.price)
        assertEquals(TripType.ROUND_TRIP, latest.tripType)
    }

    @Test
    fun `스냅샷의 여정 종류가 보존된다`() = runTest {
        val id = addTokyo(TripType.ONE_WAY)
        history.append(PriceSnapshot(id, Won(100_000), TripType.ONE_WAY, clock.instant()))

        // 종류가 섞이면 왕복과 편도를 비교해 가짜 하락 알림이 나간다.
        assertEquals(TripType.ONE_WAY, history.latest(id)!!.tripType)
    }

    @Test
    fun `지정한 일수 밖의 이력은 관찰 대상이 아니다`() = runTest {
        val id = addTokyo()
        val now = clock.instant()
        history.append(PriceSnapshot(id, Won(300_000), TripType.ROUND_TRIP, now.minusSeconds(40 * 86_400)))
        history.append(PriceSnapshot(id, Won(280_000), TripType.ROUND_TRIP, now))

        assertEquals(1, history.observeHistory(id, days = 30).first().size)
    }

    @Test
    fun `오래된 이력을 정리한다`() = runTest {
        val id = addTokyo()
        val now = clock.instant()
        history.append(PriceSnapshot(id, Won(300_000), TripType.ROUND_TRIP, now.minusSeconds(100 * 86_400)))
        history.append(PriceSnapshot(id, Won(280_000), TripType.ROUND_TRIP, now))

        history.pruneOlderThan(days = 90)

        assertEquals(1, history.observeHistory(id, days = 365).first().size)
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

```bash
./gradlew :data:testDebugUnitTest --tests "*RoomTrackedRouteRepositoryTest*"
```

기대: 컴파일 실패. `Unresolved reference: RoomTrackedRouteRepository`

- [ ] **Step 4: 구현체 작성**

`data/src/main/java/com/sypark/flightdeal/data/local/RoomTrackedRouteRepository.kt`:

```kotlin
package com.sypark.flightdeal.data.local

import com.sypark.flightdeal.data.local.entity.TrackedRouteEntity
import com.sypark.flightdeal.data.remote.AirportNames
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

class RoomTrackedRouteRepository(
    private val dao: TrackedRouteDao,
    private val clock: Clock,
) : TrackedRouteRepository {

    override fun observeAll(): Flow<List<TrackedRoute>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAll(): List<TrackedRoute> = dao.getAll().map { it.toDomain() }

    override suspend fun add(
        route: Route,
        departDate: LocalDate,
        returnDate: LocalDate?,
        tripType: TripType,
        targetPrice: Won?,
    ): Long = dao.insert(
        TrackedRouteEntity(
            originIata = route.origin.iata,
            destinationIata = route.destination.iata,
            departDate = departDate.toString(),
            returnDate = returnDate?.toString(),
            tripType = tripType.name,
            targetPrice = targetPrice?.amount,
            createdAt = clock.instant().epochSecond,
        )
    )

    override suspend fun remove(id: Long) = dao.deleteById(id)

    /**
     * DB에는 IATA만 저장한다. 도시 이름은 표시용이므로 읽을 때 채운다 —
     * 이름이 바뀌어도 저장된 데이터를 건드릴 일이 없다.
     */
    private fun TrackedRouteEntity.toDomain() = TrackedRoute(
        id = id,
        route = Route(
            origin = Airport(originIata, AirportNames.cityOf(originIata), ""),
            destination = Airport(destinationIata, AirportNames.cityOf(destinationIata), ""),
        ),
        departDate = LocalDate.parse(departDate),
        returnDate = returnDate?.let(LocalDate::parse),
        tripType = TripType.valueOf(tripType),
        targetPrice = targetPrice?.let(::Won),
        createdAt = Instant.ofEpochSecond(createdAt),
    )
}
```

`data/src/main/java/com/sypark/flightdeal/data/local/RoomPriceHistoryRepository.kt`:

```kotlin
package com.sypark.flightdeal.data.local

import com.sypark.flightdeal.data.local.entity.PriceSnapshotEntity
import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.PriceHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant

class RoomPriceHistoryRepository(
    private val dao: PriceSnapshotDao,
    private val clock: Clock,
) : PriceHistoryRepository {

    override suspend fun append(snapshot: PriceSnapshot) = dao.insert(
        PriceSnapshotEntity(
            trackedRouteId = snapshot.trackedRouteId,
            price = snapshot.price.amount,
            tripType = snapshot.tripType.name,
            capturedAt = snapshot.capturedAt.epochSecond,
        )
    )

    override suspend fun latest(trackedRouteId: Long): PriceSnapshot? =
        dao.latestFor(trackedRouteId)?.toDomain()

    override fun observeHistory(trackedRouteId: Long, days: Int): Flow<List<PriceSnapshot>> =
        dao.observeFor(trackedRouteId, cutoff(days)).map { list -> list.map { it.toDomain() } }

    override suspend fun pruneOlderThan(days: Int) = dao.deleteOlderThan(cutoff(days))

    private fun cutoff(days: Int): Long =
        clock.instant().epochSecond - days.toLong() * SECONDS_PER_DAY

    private fun PriceSnapshotEntity.toDomain() = PriceSnapshot(
        trackedRouteId = trackedRouteId,
        price = Won(price),
        tripType = TripType.valueOf(tripType),
        capturedAt = Instant.ofEpochSecond(capturedAt),
    )

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
    }
}
```

- [ ] **Step 5: Hilt 바인딩 추가**

`data/src/main/java/com/sypark/flightdeal/data/di/RepositoryModule.kt`에 추가한다.
기존 `provideFlightPriceRepository`는 그대로 둔다.

```kotlin
    /**
     * `Clock`을 주입 가능하게 둔다. Task 7의 `CheckTrackedPricesUseCase`도 이걸 받는다 —
     * 바인딩이 없으면 Hilt가 그 UseCase를 만들지 못해 컴파일이 깨진다.
     * 테스트는 `Clock.fixed`를 직접 넘긴다.
     */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideTrackedRouteRepository(dao: TrackedRouteDao, clock: Clock): TrackedRouteRepository =
        RoomTrackedRouteRepository(dao, clock)

    @Provides
    @Singleton
    fun providePriceHistoryRepository(dao: PriceSnapshotDao, clock: Clock): PriceHistoryRepository =
        RoomPriceHistoryRepository(dao, clock)
```

필요한 import를 추가한다.

- [ ] **Step 6: 테스트 통과 확인과 커밋**

```bash
./gradlew :domain:test :data:testDebugUnitTest :presentation:testDebugUnitTest :presentation:assembleDebug
```

기대: `:domain` 38, `:data` 72(63+9), `:presentation` 14.

```bash
git add domain data
git commit -m "feat: 추적 노선과 가격 이력 저장소 구현"
```

---

## Task 4: 추적 등록·해제 UseCase

**Files:**
- Create: `domain/src/main/java/com/sypark/flightdeal/domain/usecase/TrackRouteUseCase.kt`
- Create: `domain/src/main/java/com/sypark/flightdeal/domain/usecase/UntrackRouteUseCase.kt`
- Test: `domain/src/test/java/com/sypark/flightdeal/domain/usecase/TrackRouteUseCaseTest.kt`

**Interfaces:**
- Consumes: `TrackedRouteRepository`, `PriceHistoryRepository` (Task 3)
- Produces:
  - `TrackRouteUseCase.invoke(quote: PriceQuote, tripType: TripType, targetPrice: Won? = null): Long`
  - `UntrackRouteUseCase.invoke(id: Long)`

### 왜 UseCase가 필요한가

등록은 두 가지 일이다 — 추적 항목을 만들고, **그 시점의 가격을 첫 스냅샷으로 남긴다.**
첫 스냅샷을 남기지 않으면 워커가 처음 도는 6시간 뒤까지 비교 대상이 없어 아무것도
감지하지 못한다. 사용자는 등록해두고 반나절을 기다리게 된다.

- [ ] **Step 1: 테스트 작성**

`domain/src/test/java/com/sypark/flightdeal/domain/usecase/TrackRouteUseCaseTest.kt`:

```kotlin
package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.PriceHistoryRepository
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TrackRouteUseCaseTest {

    private val route = Route(Airport.INCHEON, Airport("TYO", "도쿄", "일본"))

    private val quote = PriceQuote(
        route = route,
        departDate = LocalDate.of(2026, 10, 12),
        returnDate = LocalDate.of(2026, 10, 16),
        price = Won(304_619),
        airline = "제주항공",
        foundAt = Instant.parse("2026-08-28T00:00:00Z"),
        deepLink = null,
    )

    private class FakeTrackedRoutes : TrackedRouteRepository {
        val added = mutableListOf<TripType>()
        var removed: Long? = null

        override fun observeAll(): Flow<List<TrackedRoute>> = flowOf(emptyList())
        override suspend fun getAll(): List<TrackedRoute> = emptyList()
        override suspend fun add(
            route: Route,
            departDate: LocalDate,
            returnDate: LocalDate?,
            tripType: TripType,
            targetPrice: Won?,
        ): Long {
            added += tripType
            return 42L
        }
        override suspend fun remove(id: Long) { removed = id }
    }

    private class FakeHistory : PriceHistoryRepository {
        val appended = mutableListOf<PriceSnapshot>()

        override suspend fun append(snapshot: PriceSnapshot) { appended += snapshot }
        override suspend fun latest(trackedRouteId: Long): PriceSnapshot? = appended.lastOrNull()
        override fun observeHistory(trackedRouteId: Long, days: Int): Flow<List<PriceSnapshot>> =
            flowOf(appended)
        override suspend fun pruneOlderThan(days: Int) = Unit
    }

    @Test
    fun `등록하면 추적 항목의 id를 돌려준다`() = runTest {
        val useCase = TrackRouteUseCase(FakeTrackedRoutes(), FakeHistory())

        assertEquals(42L, useCase(quote, TripType.ROUND_TRIP))
    }

    @Test
    fun `등록 즉시 첫 스냅샷을 남긴다`() = runTest {
        val history = FakeHistory()
        val useCase = TrackRouteUseCase(FakeTrackedRoutes(), history)

        useCase(quote, TripType.ROUND_TRIP)

        // 첫 스냅샷이 없으면 6시간 뒤 워커가 돌 때까지 비교 대상이 없다.
        val snapshot = history.appended.single()
        assertEquals(42L, snapshot.trackedRouteId)
        assertEquals(Won(304_619), snapshot.price)
        assertEquals(quote.foundAt, snapshot.capturedAt)
    }

    @Test
    fun `첫 스냅샷도 요청한 여정 종류로 남는다`() = runTest {
        val history = FakeHistory()
        val useCase = TrackRouteUseCase(FakeTrackedRoutes(), history)

        useCase(quote, TripType.ONE_WAY)

        // 종류가 어긋나면 다음 조회에서 가짜 하락으로 읽힌다.
        assertEquals(TripType.ONE_WAY, history.appended.single().tripType)
    }

    @Test
    fun `요청한 여정 종류로 등록한다`() = runTest {
        val routes = FakeTrackedRoutes()
        val useCase = TrackRouteUseCase(routes, FakeHistory())

        useCase(quote, TripType.ONE_WAY)

        assertEquals(listOf(TripType.ONE_WAY), routes.added)
    }

    @Test
    fun `해제하면 저장소에서 지운다`() = runTest {
        val routes = FakeTrackedRoutes()
        val useCase = UntrackRouteUseCase(routes)

        useCase(7L)

        // 이력은 외래키 CASCADE가 함께 지운다.
        assertEquals(7L, routes.removed)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :domain:test --tests "*TrackRouteUseCaseTest*"
```

기대: 컴파일 실패. `Unresolved reference: TrackRouteUseCase`

- [ ] **Step 3: 구현**

`domain/src/main/java/com/sypark/flightdeal/domain/usecase/TrackRouteUseCase.kt`:

```kotlin
package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.PriceHistoryRepository
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import javax.inject.Inject

class TrackRouteUseCase @Inject constructor(
    private val trackedRoutes: TrackedRouteRepository,
    private val history: PriceHistoryRepository,
) {

    /**
     * 추적 항목을 만들고 지금 가격을 첫 스냅샷으로 남긴다.
     *
     * 첫 스냅샷을 남기지 않으면 워커가 처음 도는 6시간 뒤까지 비교 대상이 없어
     * 아무 변동도 감지하지 못한다.
     *
     * @return 새 추적 항목의 id
     */
    suspend operator fun invoke(
        quote: PriceQuote,
        tripType: TripType,
        targetPrice: Won? = null,
    ): Long {
        val id = trackedRoutes.add(
            route = quote.route,
            departDate = quote.departDate,
            returnDate = quote.returnDate,
            tripType = tripType,
            targetPrice = targetPrice,
        )

        history.append(
            PriceSnapshot(
                trackedRouteId = id,
                price = quote.price,
                tripType = tripType,
                capturedAt = quote.foundAt,
            )
        )

        return id
    }
}
```

`domain/src/main/java/com/sypark/flightdeal/domain/usecase/UntrackRouteUseCase.kt`:

```kotlin
package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import javax.inject.Inject

class UntrackRouteUseCase @Inject constructor(
    private val trackedRoutes: TrackedRouteRepository,
) {
    /** 이력은 저장소의 외래키가 함께 지운다. */
    suspend operator fun invoke(id: Long) = trackedRoutes.remove(id)
}
```

- [ ] **Step 4: 테스트 통과 확인과 커밋**

```bash
./gradlew :domain:test
```

기대: `:domain` 43(38+5).

```bash
git add domain
git commit -m "feat: 노선 추적 등록·해제 UseCase 추가"
```

---

## Task 5: 딜 카드에서 추적 등록하기

**Files:**
- Modify: `presentation/src/main/java/com/sypark/flightdeal/feed/DealCard.kt`
- Modify: `presentation/src/main/java/com/sypark/flightdeal/feed/DealFeedScreen.kt`
- Modify: `presentation/src/main/java/com/sypark/flightdeal/feed/DealFeedViewModel.kt`
- Test: `presentation/src/test/java/com/sypark/flightdeal/feed/DealFeedViewModelTest.kt` (추가)

**Interfaces:**
- Consumes: `TrackRouteUseCase` (Task 4), `DealItem`, `TripType`
- Produces: `DealFeedViewModel.track(item: DealItem)`

### 진입점을 딜 카드에 두는 이유

검색 화면이 아직 자리표시자다. 지금 노선과 가격이 함께 있는 유일한 곳이 딜 카드다.
카드 전체를 누르는 동작은 나중에 예약처 딥링크가 가져갈 자리이므로, **카드 안에
별도의 작은 버튼**을 둔다.

- [ ] **Step 1: ViewModel 테스트 작성**

`DealFeedViewModelTest.kt`에 추가한다. 기존 테스트는 건드리지 않는다.

```kotlin
    @Test
    fun `딜을 추적하면 현재 여정 종류로 등록한다`() = runTest {
        val routes = RecordingTrackedRoutes()
        val viewModel = DealFeedViewModel(
            getDealFeed = GetDealFeedUseCase(
                FakeFlightPriceRepository(), CalculateDiscountUseCase()
            ),
            trackRoute = TrackRouteUseCase(routes, NoopHistory()),
        )
        advanceUntilIdle()
        val deal = (viewModel.uiState.value as DealFeedUiState.Success).deals.first()

        viewModel.setTripType(TripType.ONE_WAY)
        advanceUntilIdle()
        viewModel.track(deal)
        advanceUntilIdle()

        // 화면이 편도를 보여주는데 왕복으로 등록되면 이후 비교가 전부 어긋난다.
        assertEquals(TripType.ONE_WAY, routes.lastTripType)
    }
```

`TrackRouteUseCase`는 `final class`라 상속할 수 없다. 대신 진짜 UseCase에 기록용 저장소를
물려서 쓴다. 같은 파일에 둔다.

```kotlin
    private class RecordingTrackedRoutes : TrackedRouteRepository {
        var lastTripType: TripType? = null

        override fun observeAll(): Flow<List<TrackedRoute>> = flowOf(emptyList())
        override suspend fun getAll(): List<TrackedRoute> = emptyList()
        override suspend fun add(
            route: Route,
            departDate: LocalDate,
            returnDate: LocalDate?,
            tripType: TripType,
            targetPrice: Won?,
        ): Long {
            lastTripType = tripType
            return 1L
        }
        override suspend fun remove(id: Long) = Unit
    }

    private class NoopHistory : PriceHistoryRepository {
        override suspend fun append(snapshot: PriceSnapshot) = Unit
        override suspend fun latest(trackedRouteId: Long): PriceSnapshot? = null
        override fun observeHistory(trackedRouteId: Long, days: Int) =
            flowOf(emptyList<PriceSnapshot>())
        override suspend fun pruneOlderThan(days: Int) = Unit
    }
```

**기존 `viewModel(behavior)` 헬퍼도 고쳐야 한다.** `DealFeedViewModel`의 생성자가 인자를
둘 받게 되므로, 헬퍼가 `TrackRouteUseCase(RecordingTrackedRoutes(), NoopHistory())`를
함께 넘기도록 바꾼다. 기존 테스트의 단언은 하나도 바꾸지 않는다.

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :presentation:testDebugUnitTest --tests "*DealFeedViewModelTest*"
```

기대: 컴파일 실패. `DealFeedViewModel`의 생성자가 인자를 하나만 받는다.

- [ ] **Step 3: ViewModel 수정**

`DealFeedViewModel.kt`. **기존 동작을 전부 보존한다** — `loadJob?.cancel()`,
`CancellationException` 재던지기가 `Exception`보다 앞에 오는 순서, `Log` 호출.

```kotlin
@HiltViewModel
class DealFeedViewModel @Inject constructor(
    private val getDealFeed: GetDealFeedUseCase,
    private val trackRoute: TrackRouteUseCase,
) : ViewModel() {
```

`refresh()` 아래에 추가한다.

```kotlin
    /** 지금 화면이 보여주는 여정 종류로 등록한다. 화면과 다른 종류로 저장하면 이후 비교가 어긋난다. */
    fun track(item: DealItem) {
        viewModelScope.launch {
            try {
                trackRoute(item.quote, _tripType.value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "추적 등록 실패", e)
            }
        }
    }
```

- [ ] **Step 4: 카드에 버튼 추가**

`DealCard.kt`의 시그니처에 `onTrack: () -> Unit`을 추가하고, 가격 행 오른쪽 끝에 둔다.
가격 `Row`에 `Modifier.fillMaxWidth()`를 주고 버튼 앞에 `Spacer(Modifier.weight(1f))`를 넣는다.

```kotlin
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "추적",
                color = Indigo,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(IndigoSubtle, RoundedCornerShape(20.dp))
                    .clickable(onClick = onTrack)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
```

`androidx.compose.foundation.layout.Spacer`, `androidx.compose.foundation.layout.fillMaxWidth`
import를 추가한다.

`DealFeedScreen.kt`의 `DealCard` 호출에 `onTrack = { viewModel.track(deal) }`을 넘긴다.

- [ ] **Step 5: 테스트 통과 확인과 커밋**

```bash
./gradlew :domain:test :data:testDebugUnitTest :presentation:testDebugUnitTest :presentation:assembleDebug
```

기대: `:domain` 43, `:data` 72, `:presentation` 15(14+1).

```bash
git add presentation
git commit -m "feat: 딜 카드에서 노선을 추적 등록"
```

---

## Task 6: 추적 목록 화면

**Files:**
- Create: `presentation/src/main/java/com/sypark/flightdeal/tracking/TrackingUiState.kt`
- Create: `presentation/src/main/java/com/sypark/flightdeal/tracking/TrackingViewModel.kt`
- Create: `presentation/src/main/java/com/sypark/flightdeal/tracking/TrackedRouteCard.kt`
- Create: `presentation/src/main/java/com/sypark/flightdeal/tracking/TrackingScreen.kt`
- Modify: `presentation/src/main/java/com/sypark/flightdeal/ui/FlightDealNavHost.kt`
- Test: `presentation/src/test/java/com/sypark/flightdeal/tracking/TrackingViewModelTest.kt`

**Interfaces:**
- Consumes: `TrackedRouteRepository`, `PriceHistoryRepository`, `UntrackRouteUseCase`, `DetectPriceChangesUseCase`
- Produces:
  - `TrackedItem(tracked: TrackedRoute, latest: PriceSnapshot?, previous: PriceSnapshot?)`
  - `TrackingUiState` — `Loading` / `Empty` / `Success(items: List<TrackedItem>)`
  - `TrackingViewModel.uiState: StateFlow<TrackingUiState>`, `untrack(id: Long)`

빈 상태에 오류가 없다. 로컬 DB를 읽는 화면이라 네트워크가 개입하지 않는다.

- [ ] **Step 1: 상태와 모델 작성**

`presentation/src/main/java/com/sypark/flightdeal/tracking/TrackingUiState.kt`:

```kotlin
package com.sypark.flightdeal.tracking

import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.TrackedRoute

/**
 * @param latest 가장 최근 관측값. 등록 직후라면 등록 시점의 가격이다.
 * @param previous 그 직전 관측값. 아직 한 번밖에 없으면 null이고, 화면은 변동을 표시하지 않는다.
 */
data class TrackedItem(
    val tracked: TrackedRoute,
    val latest: PriceSnapshot?,
    val previous: PriceSnapshot?,
)

sealed interface TrackingUiState {
    data object Loading : TrackingUiState
    data object Empty : TrackingUiState
    data class Success(val items: List<TrackedItem>) : TrackingUiState
}
```

- [ ] **Step 2: ViewModel 테스트 작성**

`presentation/src/test/java/com/sypark/flightdeal/tracking/TrackingViewModelTest.kt`:

```kotlin
package com.sypark.flightdeal.tracking

import app.cash.turbine.test
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.PriceHistoryRepository
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import com.sypark.flightdeal.domain.usecase.UntrackRouteUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val route = Route(Airport.INCHEON, Airport("TYO", "도쿄", "일본"))

    private fun tracked(id: Long = 1L) = TrackedRoute(
        id = id,
        route = route,
        departDate = LocalDate.of(2026, 10, 12),
        returnDate = LocalDate.of(2026, 10, 16),
        tripType = TripType.ROUND_TRIP,
        targetPrice = Won(280_000),
        createdAt = Instant.EPOCH,
    )

    private fun snapshot(price: Int, at: Long) =
        PriceSnapshot(1L, Won(price), TripType.ROUND_TRIP, Instant.ofEpochSecond(at))

    private class FakeRoutes(routes: List<TrackedRoute>) : TrackedRouteRepository {
        val state = MutableStateFlow(routes)
        var removed: Long? = null

        override fun observeAll(): Flow<List<TrackedRoute>> = state
        override suspend fun getAll(): List<TrackedRoute> = state.value
        override suspend fun add(
            route: Route, departDate: LocalDate, returnDate: LocalDate?,
            tripType: TripType, targetPrice: Won?,
        ): Long = 1L
        override suspend fun remove(id: Long) {
            removed = id
            state.value = state.value.filterNot { it.id == id }
        }
    }

    private class FakeHistory(private val snapshots: List<PriceSnapshot>) : PriceHistoryRepository {
        override suspend fun append(snapshot: PriceSnapshot) = Unit
        override suspend fun latest(trackedRouteId: Long): PriceSnapshot? = snapshots.lastOrNull()
        override fun observeHistory(trackedRouteId: Long, days: Int): Flow<List<PriceSnapshot>> =
            flowOf(snapshots)
        override suspend fun pruneOlderThan(days: Int) = Unit
    }

    private fun viewModel(
        routes: TrackedRouteRepository,
        history: PriceHistoryRepository,
    ) = TrackingViewModel(routes, history, UntrackRouteUseCase(routes))

    @Test
    fun `추적 항목이 없으면 빈 상태다`() = runTest {
        viewModel(FakeRoutes(emptyList()), FakeHistory(emptyList())).uiState.test {
            assertEquals(TrackingUiState.Loading, awaitItem())
            assertEquals(TrackingUiState.Empty, awaitItem())
        }
    }

    @Test
    fun `최근 두 관측값을 함께 내려준다`() = runTest {
        val history = FakeHistory(listOf(snapshot(300_000, 100), snapshot(280_000, 200)))

        viewModel(FakeRoutes(listOf(tracked())), history).uiState.test {
            awaitItem()
            val item = (awaitItem() as TrackingUiState.Success).items.single()

            assertEquals(Won(280_000), item.latest!!.price)
            assertEquals(Won(300_000), item.previous!!.price)
        }
    }

    @Test
    fun `관측값이 하나뿐이면 직전 값이 없다`() = runTest {
        val history = FakeHistory(listOf(snapshot(300_000, 100)))

        viewModel(FakeRoutes(listOf(tracked())), history).uiState.test {
            awaitItem()
            val item = (awaitItem() as TrackingUiState.Success).items.single()

            // 등록 직후엔 비교할 대상이 없다. 화면은 변동을 표시하지 않아야 한다.
            assertEquals(Won(300_000), item.latest!!.price)
            assertNull(item.previous)
        }
    }

    @Test
    fun `해제하면 목록에서 빠진다`() = runTest {
        val routes = FakeRoutes(listOf(tracked()))
        val vm = viewModel(routes, FakeHistory(listOf(snapshot(300_000, 100))))

        vm.uiState.test {
            awaitItem()
            assertTrue(awaitItem() is TrackingUiState.Success)

            vm.untrack(1L)

            assertEquals(TrackingUiState.Empty, awaitItem())
        }
        assertEquals(1L, routes.removed)
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

```bash
./gradlew :presentation:testDebugUnitTest --tests "*TrackingViewModelTest*"
```

기대: 컴파일 실패. `Unresolved reference: TrackingViewModel`

- [ ] **Step 4: ViewModel 구현**

`presentation/src/main/java/com/sypark/flightdeal/tracking/TrackingViewModel.kt`:

```kotlin
package com.sypark.flightdeal.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.repository.PriceHistoryRepository
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import com.sypark.flightdeal.domain.usecase.UntrackRouteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackingViewModel @Inject constructor(
    trackedRoutes: TrackedRouteRepository,
    private val history: PriceHistoryRepository,
    private val untrackRoute: UntrackRouteUseCase,
) : ViewModel() {

    val uiState: StateFlow<TrackingUiState> = trackedRoutes.observeAll()
        .map { routes ->
            if (routes.isEmpty()) {
                TrackingUiState.Empty
            } else {
                TrackingUiState.Success(routes.map { it.withRecentPrices() })
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackingUiState.Loading)

    fun untrack(id: Long) {
        viewModelScope.launch { untrackRoute(id) }
    }

    /**
     * 마지막 두 관측값을 붙인다. 하나뿐이면 [TrackedItem.previous]가 null이고
     * 화면은 변동을 표시하지 않는다 — 등록 직후엔 비교할 대상이 없다.
     */
    private suspend fun TrackedRoute.withRecentPrices(): TrackedItem {
        val recent = history.observeHistory(id, days = HISTORY_DAYS).first()
        return TrackedItem(
            tracked = this,
            latest = recent.lastOrNull(),
            previous = recent.getOrNull(recent.lastIndex - 1),
        )
    }

    private companion object {
        const val HISTORY_DAYS = 90
    }
}
```

- [ ] **Step 5: 화면 작성**

`presentation/src/main/java/com/sypark/flightdeal/tracking/TrackedRouteCard.kt`:

```kotlin
package com.sypark.flightdeal.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.feed.formatWon
import com.sypark.flightdeal.ui.theme.Outline
import com.sypark.flightdeal.ui.theme.PriceDown
import com.sypark.flightdeal.ui.theme.PriceUp
import com.sypark.flightdeal.ui.theme.TextPrimary
import com.sypark.flightdeal.ui.theme.TextSecondary

@Composable
fun TrackedRouteCard(
    item: TrackedItem,
    onUntrack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Outline, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${item.tracked.route.origin.cityKo} → ${item.tracked.route.destination.cityKo}",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "해제",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onUntrack).padding(4.dp),
            )
        }

        Text(
            text = buildString {
                append(item.tracked.departDate)
                item.tracked.returnDate?.let { append(" – $it") }
                append(if (item.tracked.tripType == TripType.ROUND_TRIP) " · 왕복" else " · 편도")
            },
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 3.dp),
        )

        Row(
            modifier = Modifier.padding(top = 9.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = item.latest?.let { formatWon(it.price) } ?: "가격을 모으는 중이에요",
                color = TextPrimary,
                fontSize = if (item.latest != null) 21.sp else 13.sp,
                fontWeight = FontWeight.Bold,
            )

            val latest = item.latest
            val previous = item.previous
            if (latest != null && previous != null && latest.price != previous.price) {
                val dropped = latest.price < previous.price
                Text(
                    // 색이 정보를 나른다. 하락은 항상 초록, 상승은 항상 빨강.
                    text = if (dropped) "▼ ${formatWon(previous.price)}" else "▲ ${formatWon(previous.price)}",
                    color = if (dropped) PriceDown else PriceUp,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        item.tracked.targetPrice?.let { target ->
            Text(
                text = "목표가 ${formatWon(target)}",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
```

`presentation/src/main/java/com/sypark/flightdeal/tracking/TrackingScreen.kt`:

```kotlin
package com.sypark.flightdeal.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sypark.flightdeal.ui.theme.Background
import com.sypark.flightdeal.ui.theme.TextPrimary
import com.sypark.flightdeal.ui.theme.TextSecondary

@Composable
fun TrackingScreen(
    modifier: Modifier = Modifier,
    viewModel: TrackingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().background(Background)) {
        Text(
            text = "추적 중인 항공권",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
        )

        when (val current = state) {
            TrackingUiState.Loading -> Box(Modifier.fillMaxSize())

            TrackingUiState.Empty -> Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "특가 탭에서 마음에 드는 항공권을 추적해보세요.\n가격이 바뀌면 알려드릴게요.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }

            is TrackingUiState.Success -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items = current.items, key = { it.tracked.id }) { item ->
                    TrackedRouteCard(item = item, onUntrack = { viewModel.untrack(item.tracked.id) })
                }
            }
        }
    }
}
```

- [ ] **Step 6: 내비게이션 연결**

`FlightDealNavHost.kt`에서 `Tab.Tracking`의 목적지를 `PlaceholderScreen()` 대신
`TrackingScreen()`으로 바꾼다. import를 추가한다.

- [ ] **Step 7: 테스트와 커밋**

```bash
./gradlew :domain:test :data:testDebugUnitTest :presentation:testDebugUnitTest :presentation:assembleDebug
```

기대: `:domain` 43, `:data` 72, `:presentation` 19(15+4).

```bash
git add presentation
git commit -m "feat: 추적 목록 화면 추가"
```

---

## Task 7: 워커와 알림

**Files:**
- Modify: `gradle/libs.versions.toml`, `presentation/build.gradle.kts`
- Create: `domain/src/main/java/com/sypark/flightdeal/domain/usecase/CheckTrackedPricesUseCase.kt`
- Create: `presentation/src/main/java/com/sypark/flightdeal/worker/PriceCheckWorker.kt`
- Create: `presentation/src/main/java/com/sypark/flightdeal/worker/PriceChangeNotifier.kt`
- Create: `presentation/src/main/java/com/sypark/flightdeal/worker/WorkScheduler.kt`
- Modify: `presentation/src/main/java/com/sypark/flightdeal/FlightDealApp.kt`
- Modify: `presentation/src/main/AndroidManifest.xml`
- Modify: `presentation/src/main/java/com/sypark/flightdeal/feed/DealFeedViewModel.kt`
- Test: `domain/src/test/java/com/sypark/flightdeal/domain/usecase/CheckTrackedPricesUseCaseTest.kt`

**Interfaces:**
- Consumes: `TrackedRouteRepository`, `PriceHistoryRepository`, `FlightPriceRepository`, `DetectPriceChangesUseCase`
- Produces:
  - `CheckTrackedPricesUseCase.invoke(): List<PriceChange>`
  - `PriceChangeNotifier.notify(changes: List<PriceChange>, routes: List<TrackedRoute>)`
  - `WorkScheduler.ensureScheduled(context: Context)`

### 판정 로직은 도메인에 둔다

워커는 안드로이드 부품이라 테스트하기 번거롭다. **무엇을 조회하고 무엇이 변동인지**는
`CheckTrackedPricesUseCase`가 정하고, 워커는 그걸 부르고 결과를 알림으로 넘기기만 한다.
그러면 판정 로직 전체가 JVM 테스트로 검증된다.

- [ ] **Step 1: UseCase 테스트 작성**

`domain/src/test/java/com/sypark/flightdeal/domain/usecase/CheckTrackedPricesUseCaseTest.kt`:

```kotlin
package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Direction
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.PriceStats
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.FlightPriceRepository
import com.sypark.flightdeal.domain.repository.PriceHistoryRepository
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class CheckTrackedPricesUseCaseTest {

    private val clock = Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC)
    private val route = Route(Airport.INCHEON, Airport("TYO", "도쿄", "일본"))

    private fun tracked(id: Long = 1L, target: Won? = null) = TrackedRoute(
        id = id,
        route = route,
        departDate = LocalDate.of(2026, 10, 12),
        returnDate = LocalDate.of(2026, 10, 16),
        tripType = TripType.ROUND_TRIP,
        targetPrice = target,
        createdAt = Instant.EPOCH,
    )

    private class StubRoutes(private val routes: List<TrackedRoute>) : TrackedRouteRepository {
        override fun observeAll(): Flow<List<TrackedRoute>> = flowOf(routes)
        override suspend fun getAll(): List<TrackedRoute> = routes
        override suspend fun add(
            route: Route, departDate: LocalDate, returnDate: LocalDate?,
            tripType: TripType, targetPrice: Won?,
        ): Long = 1L
        override suspend fun remove(id: Long) = Unit
    }

    private class StubHistory(private val last: PriceSnapshot?) : PriceHistoryRepository {
        val appended = mutableListOf<PriceSnapshot>()
        var pruned = false

        override suspend fun append(snapshot: PriceSnapshot) { appended += snapshot }
        override suspend fun latest(trackedRouteId: Long): PriceSnapshot? = last
        override fun observeHistory(trackedRouteId: Long, days: Int) = flowOf(emptyList<PriceSnapshot>())
        override suspend fun pruneOlderThan(days: Int) { pruned = true }
    }

    private class StubPrices(
        private val result: AppResult<List<PriceQuote>>,
    ) : FlightPriceRepository {
        var seenTripType: TripType? = null

        override suspend fun cheapestDeals(origin: Airport, limit: Int, tripType: TripType) =
            AppResult.Empty
        override suspend fun calendarPrices(route: Route, month: YearMonth, tripType: TripType):
            AppResult<List<PriceQuote>> {
            seenTripType = tripType
            return result
        }
        override suspend fun priceStats(route: Route, month: YearMonth, tripType: TripType):
            AppResult<PriceStats> = AppResult.Empty
    }

    private fun quote(price: Int) = PriceQuote(
        route = route,
        departDate = LocalDate.of(2026, 10, 12),
        returnDate = LocalDate.of(2026, 10, 16),
        price = Won(price),
        airline = "대한항공",
        foundAt = Instant.EPOCH,
        deepLink = null,
    )

    private fun snapshot(price: Int, tripType: TripType = TripType.ROUND_TRIP) =
        PriceSnapshot(1L, Won(price), tripType, Instant.EPOCH)

    private fun useCase(
        routes: TrackedRouteRepository,
        history: PriceHistoryRepository,
        prices: FlightPriceRepository,
    ) = CheckTrackedPricesUseCase(routes, history, prices, DetectPriceChangesUseCase(), clock)

    @Test
    fun `가격이 내리면 변동을 돌려준다`() = runTest {
        val history = StubHistory(snapshot(300_000))
        val changes = useCase(
            StubRoutes(listOf(tracked())),
            history,
            StubPrices(AppResult.Success(listOf(quote(280_000)))),
        ).invoke()

        assertEquals(1, changes.size)
        assertEquals(Direction.DOWN, changes.single().direction)
    }

    @Test
    fun `조회한 가격을 이력에 남긴다`() = runTest {
        val history = StubHistory(snapshot(300_000))

        useCase(
            StubRoutes(listOf(tracked())),
            history,
            StubPrices(AppResult.Success(listOf(quote(280_000)))),
        ).invoke()

        // 다음 실행의 비교 대상이 된다. 남기지 않으면 매번 같은 변동을 다시 알린다.
        assertEquals(Won(280_000), history.appended.single().price)
    }

    @Test
    fun `추적 항목의 여정 종류로 조회한다`() = runTest {
        val prices = StubPrices(AppResult.Success(listOf(quote(280_000))))

        useCase(StubRoutes(listOf(tracked())), StubHistory(snapshot(300_000)), prices).invoke()

        // 왕복 추적을 편도로 조회하면 매번 60% 하락으로 읽힌다.
        assertEquals(TripType.ROUND_TRIP, prices.seenTripType)
    }

    @Test
    fun `이력의 여정 종류도 추적 항목과 같다`() = runTest {
        val history = StubHistory(snapshot(300_000))

        useCase(
            StubRoutes(listOf(tracked())),
            history,
            StubPrices(AppResult.Success(listOf(quote(280_000)))),
        ).invoke()

        assertEquals(TripType.ROUND_TRIP, history.appended.single().tripType)
    }

    @Test
    fun `가격이 그대로면 변동이 없다`() = runTest {
        val changes = useCase(
            StubRoutes(listOf(tracked())),
            StubHistory(snapshot(300_000)),
            StubPrices(AppResult.Success(listOf(quote(300_000)))),
        ).invoke()

        assertTrue(changes.isEmpty())
    }

    @Test
    fun `조회에 실패한 항목은 건너뛴다`() = runTest {
        val changes = useCase(
            StubRoutes(listOf(tracked())),
            StubHistory(snapshot(300_000)),
            StubPrices(AppResult.NetworkError(java.io.IOException("boom"))),
        ).invoke()

        // 한 노선이 실패했다고 나머지를 포기하지 않는다.
        assertTrue(changes.isEmpty())
    }

    @Test
    fun `추적 항목이 없으면 아무 일도 하지 않는다`() = runTest {
        val changes = useCase(
            StubRoutes(emptyList()),
            StubHistory(null),
            StubPrices(AppResult.Empty),
        ).invoke()

        assertTrue(changes.isEmpty())
    }

    @Test
    fun `실행할 때마다 오래된 이력을 치운다`() = runTest {
        val history = StubHistory(snapshot(300_000))

        useCase(
            StubRoutes(listOf(tracked())),
            history,
            StubPrices(AppResult.Success(listOf(quote(280_000)))),
        ).invoke()

        assertTrue(history.pruned)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :domain:test --tests "*CheckTrackedPricesUseCaseTest*"
```

기대: 컴파일 실패. `Unresolved reference: CheckTrackedPricesUseCase`

- [ ] **Step 3: UseCase 구현**

`domain/src/main/java/com/sypark/flightdeal/domain/usecase/CheckTrackedPricesUseCase.kt`:

```kotlin
package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.PriceChange
import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.repository.FlightPriceRepository
import com.sypark.flightdeal.domain.repository.PriceHistoryRepository
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

/**
 * 워커가 부르는 유일한 진입점. 판정 로직 전체가 여기 있어서 JVM 테스트로 검증된다.
 */
class CheckTrackedPricesUseCase @Inject constructor(
    private val trackedRoutes: TrackedRouteRepository,
    private val history: PriceHistoryRepository,
    private val prices: FlightPriceRepository,
    private val detectChanges: DetectPriceChangesUseCase,
    private val clock: Clock,
) {

    suspend operator fun invoke(): List<PriceChange> {
        // 이력은 계속 쌓인다. 조회하러 온 김에 치운다.
        history.pruneOlderThan(HISTORY_DAYS)

        return trackedRoutes.getAll().mapNotNull { tracked -> check(tracked) }
    }

    private suspend fun check(tracked: TrackedRoute): PriceChange? {
        val current = currentPrice(tracked) ?: return null
        val previous = history.latest(tracked.id)

        history.append(
            PriceSnapshot(
                trackedRouteId = tracked.id,
                price = current,
                // 추적 항목의 종류를 그대로 쓴다. 섞이면 매번 가짜 하락이 뜬다.
                tripType = tracked.tripType,
                capturedAt = clock.instant(),
            )
        )

        return detectChanges(tracked, previous, current)
    }

    /**
     * 한 노선이 실패했다고 나머지를 포기하지 않는다.
     *
     * 왕복은 귀국일을 정확히 지정하지 않고 같은 달로 조회한다. API가 출발월·귀국월 단위로만
     * 받기 때문이다. 그래서 등록한 귀국일과 다른 조합의 가격이 잡힐 수 있다 —
     * 같은 기준으로 계속 비교하므로 변동 판정은 성립한다.
     */
    private suspend fun currentPrice(tracked: TrackedRoute) =
        when (
            val result = prices.calendarPrices(
                route = tracked.route,
                month = YearMonth.from(tracked.departDate),
                tripType = tracked.tripType,
            )
        ) {
            is AppResult.Success ->
                result.data.filter { it.departDate == tracked.departDate }.minByOrNull { it.price.amount }?.price
            else -> null
        }

    private companion object {
        const val HISTORY_DAYS = 90
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew :domain:test
```

기대: `:domain` 51(43+8).

**`가격이 내리면 변동을 돌려준다`가 실패하면** `currentPrice`의 날짜 필터를 확인한다.
테스트의 `quote(280_000)`은 `departDate`가 추적 항목과 같으므로 통과해야 한다.
필터를 지우지 말고 무엇이 걸렸는지 보고한다.

- [ ] **Step 5: WorkManager 의존성 추가**

`gradle/libs.versions.toml`의 `[versions]`:

```toml
work = "2.10.0"
hiltWork = "1.2.0"
```

`[libraries]`:

```toml
androidx-work = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
hilt-work = { module = "androidx.hilt:hilt-work", version.ref = "hiltWork" }
hilt-work-compiler = { module = "androidx.hilt:hilt-compiler", version.ref = "hiltWork" }
```

`presentation/build.gradle.kts`의 `dependencies`:

```kotlin
    implementation(libs.androidx.work)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
```

- [ ] **Step 6: 알림 발송기 작성**

`presentation/src/main/java/com/sypark/flightdeal/worker/PriceChangeNotifier.kt`:

```kotlin
package com.sypark.flightdeal.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sypark.flightdeal.domain.model.Direction
import com.sypark.flightdeal.domain.model.PriceChange
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.feed.formatWon
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class PriceChangeNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * 한 번의 워커 실행에서 바뀐 것들을 알림 하나로 묶는다.
     * 노선마다 따로 쏘면 추적을 여러 개 걸어둔 사용자에게 알림 폭탄이 된다.
     */
    fun notify(changes: List<PriceChange>, routes: List<TrackedRoute>) {
        if (changes.isEmpty()) return
        if (!hasPermission()) return

        ensureChannel()

        val byId = routes.associateBy { it.id }
        val lines = changes.mapNotNull { change ->
            val route = byId[change.trackedRouteId] ?: return@mapNotNull null
            val arrow = if (change.direction == Direction.DOWN) "▼" else "▲"
            val target = if (change.reachedTarget) " · 목표가 도달" else ""
            "$arrow ${route.route.destination.cityKo} ${formatWon(change.current)}$target"
        }
        if (lines.isEmpty()) return

        val title = if (changes.size == 1) "항공권 가격이 바뀌었어요" else "항공권 ${changes.size}건의 가격이 바뀌었어요"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(NotificationCompat.InboxStyle().also { style -> lines.forEach(style::addLine) })
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /** 권한이 없으면 조용히 넘어간다. 알림을 못 받을 뿐 앱의 나머지는 정상 동작해야 한다. */
    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "가격 변동", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private companion object {
        const val CHANNEL_ID = "price_change"
        const val NOTIFICATION_ID = 1001
    }
}
```

minSdk 26이므로 `NotificationChannel`은 조건 분기 없이 쓴다.

- [ ] **Step 7: 워커와 스케줄러 작성**

`presentation/src/main/java/com/sypark/flightdeal/worker/PriceCheckWorker.kt`:

```kotlin
package com.sypark.flightdeal.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import com.sypark.flightdeal.domain.usecase.CheckTrackedPricesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class PriceCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val checkPrices: CheckTrackedPricesUseCase,
    private val trackedRoutes: TrackedRouteRepository,
    private val notifier: PriceChangeNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val changes = checkPrices()
        notifier.notify(changes, trackedRoutes.getAll())
        Log.d(TAG, "가격 확인 완료, 변동 ${changes.size}건")
        Result.success()
    } catch (e: Exception) {
        // 다음 정기 주기를 기다리는 것보다 조금 뒤 다시 해보는 편이 낫다.
        Log.w(TAG, "가격 확인 실패", e)
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    private companion object {
        const val TAG = "PriceCheckWorker"
        const val MAX_ATTEMPTS = 3
    }
}
```

`presentation/src/main/java/com/sypark/flightdeal/worker/WorkScheduler.kt`:

```kotlin
package com.sypark.flightdeal.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {

    /**
     * WorkManager의 최소 주기는 15분이지만 항공권 가격은 분 단위로 바뀌지 않는다.
     * 짧게 잡으면 배터리와 API 쿼터만 쓰고 Doze에서 어차피 밀린다.
     */
    private const val INTERVAL_HOURS = 6L
    private const val WORK_NAME = "price-check"

    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<PriceCheckWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            // 이미 예약돼 있으면 그대로 둔다. 앱을 열 때마다 주기가 초기화되면
            // 자주 여는 사용자에게는 워커가 영영 돌지 않는다.
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
```

- [ ] **Step 8: Application과 매니페스트 설정**

`FlightDealApp.kt`를 아래로 바꾼다.

```kotlin
package com.sypark.flightdeal

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.sypark.flightdeal.worker.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FlightDealApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        WorkScheduler.ensureScheduled(this)
    }
}
```

`AndroidManifest.xml`에 권한을 추가하고, WorkManager의 기본 초기화를 끈다.
`Configuration.Provider`를 쓰면 기본 초기화를 제거해야 한다.

```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

`<application>` 안에 추가한다.

```xml
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>
```

`<manifest>`에 `xmlns:tools="http://schemas.android.com/tools"`를 추가한다.

- [ ] **Step 9: 권한 요청을 추적 등록 시점에 붙임**

`DealFeedScreen.kt`에서 추적 버튼을 누를 때 권한을 요청한다.
**앱 시작 시점이 아니라 첫 추적 등록 시점**이다 — 왜 필요한지 모르는 상태에서 물으면 거절당한다.

`DealFeedScreen` 안에 추가한다.

```kotlin
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* 거절해도 추적 자체는 동작한다. 알림만 못 받는다. */ }
```

`onTrack` 람다를 아래로 바꾼다.

```kotlin
                        onTrack = {
                            viewModel.track(deal)
                            if (ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
```

`android.Manifest`, `android.content.pm.PackageManager`,
`androidx.activity.compose.rememberLauncherForActivityResult`,
`androidx.activity.result.contract.ActivityResultContracts`,
`androidx.compose.ui.platform.LocalContext`, `androidx.core.content.ContextCompat` import를 추가한다.

- [ ] **Step 10: 빌드와 전체 테스트**

```bash
./gradlew :domain:test :data:testDebugUnitTest :presentation:testDebugUnitTest :presentation:assembleDebug
```

기대: `:domain` 51, `:data` 72, `:presentation` 19. BUILD SUCCESSFUL.

- [ ] **Step 11: 에뮬레이터에서 확인**

`Pixel_API_33`이 이미 떠 있다. 없으면 부팅한다.

```bash
./gradlew :presentation:installDebug
~/Library/Android/sdk/platform-tools/adb shell am start -n com.sypark.flightdeal/.MainActivity
```

확인할 것:

| 항목 | 기대 |
|---|---|
| 딜 카드 | 오른쪽에 인디고 "추적" 버튼 |
| 추적 버튼 | 누르면 알림 권한을 물음 (첫 등록 시점) |
| 추적 탭 | 등록한 노선이 보이고, 가격과 왕복/편도가 표시됨 |
| 추적 탭 | 등록 직후엔 변동 표시가 없음 (비교 대상이 하나뿐) |
| 해제 | 누르면 목록에서 사라짐 |
| 빈 상태 | 전부 해제하면 "특가 탭에서 …" 안내 |

워커를 즉시 돌려 알림을 확인한다.

```bash
~/Library/Android/sdk/platform-tools/adb shell cmd jobscheduler run -f com.sypark.flightdeal 0
~/Library/Android/sdk/platform-tools/adb logcat -d -s PriceCheckWorker:* | tail -20
```

가격이 그대로면 알림이 안 온다. **그건 정상이다** — 변동이 없으면 알리지 않는 게 설계다.
로그의 "변동 0건"으로 워커가 돌았음을 확인한다.

스크린샷을 `.superpowers/sdd/task-7-tracking.png`로 남긴다.
**찍지 못했으면 그렇다고 보고한다. 보지 않은 화면을 묘사하지 않는다.**

- [ ] **Step 12: 커밋**

```bash
git add gradle domain presentation
git commit -m "feat: 6시간 주기 가격 확인 워커와 변동 알림 추가"
```

---

## 완료 기준

- [ ] 딜 카드에서 노선을 추적 등록할 수 있고, 등록 즉시 첫 스냅샷이 남는다
- [ ] 추적 탭에 목록이 뜨고, 관측값이 둘 이상이면 직전 대비 변동이 초록/빨강으로 표시된다
- [ ] 해제하면 목록과 이력이 함께 사라진다 (외래키 CASCADE)
- [ ] 워커가 6시간 주기로 등록되고, 수동 실행 시 로그가 남는다
- [ ] 변동이 있으면 알림 하나로 묶여 발송된다
- [ ] 알림 권한은 첫 추적 등록 시점에 요청하고, 거절해도 나머지 기능이 동작한다
- [ ] **추적 항목·스냅샷·조회가 모두 같은 여정 종류를 쓴다** — 왕복과 편도를 섞어 비교하지 않는다
- [ ] 공항은 IATA로만 동일성을 판정한다
- [ ] `:domain`에 안드로이드 타입도 Travelpayouts라는 단어도 없다
- [ ] `.java` 파일이 하나도 없다
- [ ] 전체 테스트 통과. `:domain` 51, `:data` 72, `:presentation` 19

## 다음 계획서

- **계획서 4:** 가격 추이 그래프(Compose `Canvas`), 추적 상세 화면, 목표가 설정 UI
- **계획서 5:** 날짜별 최저가 캘린더, 목적지 탐색, Custom Tabs 딥링크

## 미해결로 남긴 것

- **네트워크 오류 시 기존 목록이 사라진다.** spec §8은 캐시 데이터를 유지하고 스낵바로만 알리라고 한다. 현재 상태 모델로는 "콘텐츠 + 일시적 오류"를 표현할 수 없다. 화면이 더 늘기 전에 정할 것
- **OkHttp 디스패처가 앱 전역 4슬롯이다.** 워커가 추적 노선을 여럿 도는 동안 포그라운드 피드가 큐에 밀린다. 워커에 별도 클라이언트를 줄 것
- **캐시에 무효화 API가 없다.** pull-to-refresh와 워커의 강제 갱신에 필요하다
- **다크 팔레트 없음.** 라이트 고정
- **`transfers`/`duration`이 도메인까지 오지 않는다.** 경유편이 직항과 같은 무게로 표시된다
- 마커 미발급 — 딥링크는 열리되 커미션이 안 붙는다
