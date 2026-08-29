# 알림 기록 구현 계획

**Goal:** 지난 알림을 다시 볼 수 있게 한다.

**왜.** 알림은 `NOTIFICATION_ID`가 고정이라 **새 알림이 못 본 알림을 덮는다.**
지금은 그게 괜찮다고 정리해 뒀는데 — 추적 화면이 현재 상태를 다 갖고 있으니
알림은 그리로 가는 포인터일 뿐이라고 — **사실이 아니다.** 추적 카드는 현재가와
마지막 변동만 보여준다. "어제 도쿄가 34만에서 30만으로 떨어졌다고 알림이 왔던 것 같은데"를
확인할 방법이 앱 어디에도 없다.

**기록할 자리는 정해져 있다.** `ConfirmNotifiedUseCase`는 **실제로 화면에 뜬 변동에
대해서만** 실행된다 — 워커가 `notifier.notify(...)`가 돌려준 `shown`만 넘긴다.
기준선을 옮기는 것과 기록을 남기는 것이 같은 자리에서 일어나야 둘이 어긋나지 않는다.

## Global Constraints

- Java 소스 파일 금지 — `.java` 파일이 0개여야 한다
- `:domain`은 아무것도 의존하지 않는다. 안드로이드 타입 금지, "Travelpayouts" 문자열 금지
- `:data`는 `:domain`만 의존한다
- 비동기는 Coroutines/Flow. RxJava·LiveData 금지. KSP만, kapt 금지
- 가격은 `Won` value class
- **새 의존성 금지** (`room-testing`은 이미 있다)
- 색은 `FlightDealTheme.colors`로 읽는다
- **`java.time`의 새 메서드는 도입 API 레벨을 확인할 것.** `:domain`은 순수 JVM 모듈이라
  린트가 보지 않고 테스트는 JDK에서 돈다 — `LocalDate.ofInstant`(API 34)로 워커가
  안드로이드 13 이하 전 기기에서 죽은 적이 있다. `Clock`을 받는 팩토리를 쓸 것
- 주석은 한국어로, **왜**를 적을 것
- 커밋에 Co-Authored-By나 Claude/AI 트레일러 금지

---

## Task 1: 저장 (Room 2 → 3)

**Files:**
- Create: `domain/.../model/PriceAlert.kt`, `domain/.../repository/PriceAlertRepository.kt`
- Create: `data/.../local/entity/PriceAlertEntity.kt`, `PriceAlertDao.kt`, `RoomPriceAlertRepository.kt`
- Modify: `data/.../local/FlightDealDatabase.kt`, `Migrations.kt`, `data/.../di/DatabaseModule.kt`
- Test: `:data` — DAO 테스트와 **마이그레이션 테스트**

- [ ] **Step 1: 도메인 모델**

```kotlin
/**
 * 사용자에게 **실제로 보여준** 가격 변동 하나.
 *
 * @param notifiedAt 알림을 띄운 시각. 관측 시각이 아니다 — 못 본 알림을 나중에
 *   찾아보는 것이 이 기록의 목적이므로 "언제 알렸나"가 기준이다.
 */
data class PriceAlert(
    val id: Long,
    val trackedRouteId: Long,
    val previous: Won,
    val current: Won,
    val reachedTarget: Boolean,
    val notifiedAt: Instant,
) {
    /** 저장하지 않고 계산한다. 값과 방향이 따로 저장되면 둘이 어긋날 수 있다. */
    val direction: Direction get() = if (current < previous) Direction.DOWN else Direction.UP
}
```

- [ ] **Step 2: Repository**

```kotlin
interface PriceAlertRepository {
    /** 알림을 띄운 직후에만 부른다. */
    suspend fun record(changes: List<PriceChange>, at: Instant)
    fun observeRecent(days: Int): Flow<List<PriceAlert>>
    suspend fun pruneOlderThan(days: Int)
}
```

- [ ] **Step 3: 엔티티와 마이그레이션**

```kotlin
@Entity(
    tableName = "price_alert",
    foreignKeys = [ForeignKey(
        entity = TrackedRouteEntity::class,
        parentColumns = ["id"], childColumns = ["trackedRouteId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("trackedRouteId"), Index("notifiedAt")],
)
```

**추적을 해제하면 그 노선의 알림 기록도 함께 사라진다(CASCADE).**
해제 확인 다이얼로그가 이미 "지금까지 모은 가격 이력도 함께 지워져요"라고 약속하고
있으므로 그 약속과 어긋나지 않는다. 기록만 남기려면 노선 정보를 알림 행에 복사해야
하는데, 그러면 같은 사실이 두 곳에 저장된다.

`MIGRATION_2_3`은 `CREATE TABLE` + `CREATE INDEX`다. 기존 데이터는 건드리지 않는다.
**`fallbackToDestructiveMigration()`을 쓰지 마라** — 며칠에 걸쳐 모은 가격 이력은
다시 만들 수 없다.

- [ ] **Step 4: 테스트**

```kotlin
    @Test fun `알림을 기록하고 최신순으로 돌려준다`()
    @Test fun `추적을 해제하면 그 노선의 알림 기록도 사라진다`()
    @Test fun `보관 기간이 지난 기록은 정리된다`()
```

그리고 `MigrationTestHelper`로:
```kotlin
    @Test fun `MIGRATION_2_3은 기존 데이터를 보존한다`()
```
v2 스키마로 열어 `tracked_route`와 `price_snapshot`에 행을 넣고, 마이그레이션 뒤
**둘 다 남아 있고 `price_alert`가 비어 있는지** 확인한다. `MIGRATION_1_2` 테스트가
이미 같은 방식으로 있으니 그것을 따를 것.

- [ ] **Step 5: 커밋** — `feat: 알림 기록 저장소 추가`

---

## Task 2: 알림을 띄울 때 기록한다

**Files:**
- Modify: `domain/.../usecase/ConfirmNotifiedUseCase.kt`
- Modify: `domain/.../usecase/CheckTrackedPricesUseCase.kt` (정리(prune)를 함께)
- Test: `:domain`

- [ ] **Step 1: 같은 자리에서 둘 다 한다**

```kotlin
/**
 * 알림이 전달된 것을 확인하고 기준선을 옮기며, 그 사실을 기록으로 남긴다.
 * 알림을 실제로 띄운 뒤에만 부른다 — 먼저 부르면 놓친 변동이 생긴다.
 *
 * 기준선 갱신과 기록은 **같은 자리에서 일어나야 한다.** 따로 두면 알림은 갔는데
 * 기록에 없거나, 기록에는 있는데 기준선이 안 옮겨간 상태가 생긴다.
 */
class ConfirmNotifiedUseCase @Inject constructor(
    private val trackedRoutes: TrackedRouteRepository,
    private val alerts: PriceAlertRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(changes: List<PriceChange>) {
        alerts.record(changes, clock.instant())
        changes.forEach { trackedRoutes.markNotified(it.trackedRouteId, it.current) }
    }
}
```

- [ ] **Step 2: 정리**

`CheckTrackedPricesUseCase`가 이미 `history.pruneOlderThan(PRICE_HISTORY_RETENTION_DAYS)`를
부른다. 알림 기록도 **같은 기간**으로 함께 정리한다. 두 기간이 달라지면 그래프에는
없는데 기록에는 있는 변동이 생긴다.

- [ ] **Step 3: 테스트**

```kotlin
    @Test fun `알림을 확인하면 기록이 남는다`()
    @Test fun `보여주지 않은 변동은 기록하지 않는다`()
```

**두 번째가 중요하다.** 워커는 `notify()`가 돌려준 `shown`만 넘긴다 — 채널이 꺼져
있거나 표시에 실패한 변동이 기록에 남으면, 사용자는 오지도 않은 알림을 기록에서 보게 된다.

- [ ] **Step 4: 커밋** — `feat: 알림을 띄울 때 기록을 남긴다`

---

## Task 3: 화면

**Files:**
- Create: `presentation/.../alerts/AlertHistoryScreen.kt`, `AlertHistoryViewModel.kt`,
  `AlertHistoryUiState.kt`, `RelativeTime.kt`
- Modify: `presentation/.../ui/FlightDealNavHost.kt`, `presentation/.../tracking/TrackingScreen.kt`
- Test: `presentation` — `RelativeTimeTest`, `AlertHistoryViewModelTest`

- [ ] **Step 1: 상대 시각 (순수 함수)**

`RelativeTime.kt`에 **안드로이드 import 없이:**

```kotlin
/** 예: "방금", "3시간 전", "어제", "10월 2일". */
fun relativeTimeKo(then: Instant, now: Instant, zone: ZoneId): String
```

- 1분 미만 → `"방금"`
- 1시간 미만 → `"N분 전"`
- 24시간 미만 → `"N시간 전"`
- 어제 → `"어제"`
- 그 밖 → `"M월 D일"`

**"어제"는 경과 시간이 아니라 날짜로 판정한다.** 23시간 전이어도 날짜가 바뀌었으면
어제다. `LocalDate`로 비교할 것 — `Instant.atZone(zone).toLocalDate()`를 쓴다
(`LocalDate.ofInstant`는 API 34다).

테스트:
```kotlin
    @Test fun `1분 미만은 방금이다`()
    @Test fun `23시간 전이어도 날짜가 다르면 어제다`()
    @Test fun `이틀 넘으면 날짜를 적는다`()
    @Test fun `같은 날 23시간 전은 시간으로 적는다`()
```

- [ ] **Step 2: ViewModel**

`observeRecent(PRICE_HISTORY_RETENTION_DAYS)`와 `trackedRoutes.observeAll()`을 묶어
노선 이름을 채운다. **노선을 못 찾는 기록은 버린다** — CASCADE라 정상적으로는
생기지 않지만, 그 하나 때문에 화면 전체가 못 열려서는 안 된다
(`RoomTrackedRouteRepository.toDomain`이 같은 이유로 그렇게 한다).

상태는 `Loading` / `Empty` / `Success`. **`Empty`에 재시도 버튼을 두지 마라** —
로컬 DB라 다시 눌러도 결과가 같다.

- [ ] **Step 3: 화면**

- 제목 `알림 기록`
- 각 줄: `서울 → 도쿄` / `2026-10-10 – 10-14 · 왕복` / `▼ 340,000원 → 304,619원` / `3시간 전`
  - 하락은 `priceDown`, 상승은 `priceUp` — 앱의 다른 곳과 같은 규칙
  - `reachedTarget`이면 `· 목표가 도달`을 덧붙인다
- 빈 상태: `"아직 알림 기록이 없어요"` / `"가격이 바뀌면 여기에 남겨드릴게요."`
- 뒤로 가기로 추적 화면으로 돌아온다

- [ ] **Step 4: 진입점**

`FlightDealNavHost`에 `alerts` 목적지를 더한다. **하단 탭을 늘리지 마라** — 네 개면 충분하고,
알림 기록은 추적에 딸린 화면이다. `TrackingScreen` 제목 줄 오른쪽에 `알림 기록` 텍스트
버튼을 두고 거기서 이동한다. 48dp 터치 영역.

- [ ] **Step 5: 빌드·테스트**

- [ ] **Step 6: 기기 확인 — logcat까지**

`emulator-5554`에서:
1. 추적 화면에서 `알림 기록`으로 이동, 빈 상태가 뜨는지
2. **실제 알림을 하나 발생시킨다** — sqlite로 `tracked_route.notifiedPrice`를 다른 값으로
   바꾸고 워커를 강제 실행한다. WorkManager가 주기 작업의 즉시 실행을 거부하므로
   앱을 force-stop하고 `no_backup/androidx.work.workdb*`를 지운 뒤 다시 열면 즉시 돈다
3. 알림이 뜬 뒤 알림 기록에 그 변동이 남았는지, 시각이 `방금`으로 보이는지
4. **추적을 해제하면 그 노선의 기록이 사라지는지**
5. 라이트·다크 스크린샷

`adb -s emulator-5554 shell logcat -c` 후 위를 다 하고
`logcat -d | grep -iE "FATAL|AndroidRuntime|NoSuchMethod|Exception"`을 `com.sypark.flightdeal`로
걸러 확인한다. **깨끗해도 무엇을 봤는지 보고할 것.**

- [ ] **Step 7: 커밋** — `feat: 알림 기록 화면 추가`
