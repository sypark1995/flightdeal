# 내정보 화면 구현 계획

**Goal:** 마지막 남은 placeholder 탭을 채운다. 이 앱이 무엇을 기기에 저장하고 있는지,
알림이 실제로 켜져 있는지, 가격은 어디서 오는지 보여준다.

**왜.** 계정이 없고 모든 데이터가 단말에만 쌓이는 앱이다. 사용자가 **자기 기기에 무엇이
쌓여 있는지 볼 방법이 없고, 지울 방법도 없다.** 알림도 마찬가지다 — 워커가 6시간마다
돌지만 알림이 실제로 도착하는 상태인지 앱 안에서 알 수 없다. 채널만 꺼도 조용히
아무것도 안 오는데, 그때 사용자는 "가격이 안 변했나 보다"라고 생각한다.

## 설계 원칙 — 상태를 두 벌 만들지 않는다

**알림 on/off를 앱이 따로 저장하지 않는다.** 안드로이드 알림 설정이 이미 유일한 진실이고,
`PriceChangeNotifier`가 그것을 읽어 판단한다. 앱에 별도 스위치를 두면 "앱에서는 켜짐,
시스템에서는 꺼짐"이 생기고 사용자는 왜 알림이 안 오는지 영영 모른다.

그래서 이 화면은 **읽고 보여주고, 바꾸는 것은 시스템 설정으로 넘긴다.**

같은 이유로 **판정 로직을 복사하지 않는다.** `PriceChangeNotifier.notificationsAllowed()`는
지금 `private`이다. 화면이 같은 판정을 따로 구현하면 둘이 어긋나 화면은 "켜짐"이라고
말하는데 알림은 안 오는 상태가 만들어진다. **이 프로젝트가 네 번 겪은 결함이 정확히
그 모양이다.** 하나로 뽑아 양쪽이 부른다.

폴링 주기도 마찬가지다. `WorkScheduler`의 `INTERVAL_HOURS = 6`이 `private`이라
화면에 "6시간"을 따로 쓰면 나중에 주기를 바꿀 때 화면만 거짓말한다.

## Global Constraints

- Java 소스 파일 금지 — `.java` 파일이 0개여야 한다
- `:domain`은 아무것도 의존하지 않는다. 안드로이드 타입 금지. `:data`는 `:domain`만 의존
- 비동기는 Coroutines/Flow. RxJava·LiveData 금지. KSP만, kapt 금지
- **새 의존성 금지** — DataStore를 추가하지 마라. 이 화면은 저장할 상태가 없다
- 색은 `FlightDealTheme.colors`로 읽는다. 최상위 색 상수는 이제 없다
- 주석은 한국어로, **왜**를 적을 것
- 커밋에 Co-Authored-By나 Claude/AI 트레일러 금지

---

## Task 1: 이력 건수와 삭제를 도메인에 연다

**Files:**
- Modify: `domain/.../repository/PriceHistoryRepository.kt`
- Modify: `data/.../local/PriceSnapshotDao.kt`, `RoomPriceHistoryRepository.kt`
- Test: `data/src/test/.../local/` (Robolectric DAO 테스트가 이미 있다)

- [ ] **Step 1: 인터페이스**

```kotlin
    /** 기기에 쌓인 가격 관측 건수. 내정보 화면이 "무엇을 저장하고 있는지" 보여주는 데 쓴다. */
    fun observeCount(): Flow<Int>

    /**
     * 관측 이력만 지운다. 추적 항목과 통보 기준선([TrackedRoute.notifiedPrice])은 남는다 —
     * 추적을 계속하겠다는 사용자의 결정까지 취소하지 않는다. 그래프만 비고 다시 쌓인다.
     */
    suspend fun clearAll()
```

`Flow<Int>`로 두는 이유: 워커가 스냅샷을 쌓으면 화면이 저절로 따라간다.

- [ ] **Step 2: DAO와 구현, 그리고 테스트**

```kotlin
    @Query("SELECT COUNT(*) FROM price_snapshot")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM price_snapshot")
    suspend fun deleteAll()
```

테스트:
```kotlin
    @Test fun `이력을 지워도 추적 항목은 남는다`()
    @Test fun `건수는 쌓을 때마다 늘어난다`()
```

**첫 번째가 중요하다.** 외래키가 걸려 있으므로 방향을 착각하면 반대로 동작한다 —
`tracked_route`를 지우면 `price_snapshot`이 따라 지워지지만, 그 반대는 아니어야 한다.

- [ ] **Step 3: 커밋** — `feat: 가격 이력 건수 조회와 전체 삭제 추가`

---

## Task 2: 공유되는 판정을 하나로 뽑는다

**Files:**
- Modify: `presentation/.../worker/PriceChangeNotifier.kt`, `WorkScheduler.kt`
- Create: `presentation/.../worker/NotificationStatus.kt` (이름은 판단에 맡긴다)

- [ ] **Step 1: 알림 가능 여부를 한 곳에**

`PriceChangeNotifier.notificationsAllowed()`의 내용을 밖으로 뽑아
`PriceChangeNotifier`와 내정보 화면이 **같은 함수**를 부르게 한다.
앱 전체 스위치와 채널 importance를 둘 다 보는 지금 동작을 **그대로** 옮길 것 —
이 판정은 기기에서 음소거/해제 대조로 검증된 것이다. 로직을 바꾸지 마라.

채널 ID 상수도 함께 옮긴다. 화면이 시스템 설정을 열 때 그 ID가 필요하다.

- [ ] **Step 2: 폴링 주기를 공개한다**

`WorkScheduler.INTERVAL_HOURS`를 `internal` 또는 `const val`로 열어 화면이 읽게 한다.
화면에 `"6"`을 직접 쓰지 마라.

- [ ] **Step 3: 커밋** — `refactor: 알림 가능 여부 판정을 화면과 워커가 공유하게 한다`

---

## Task 3: 내정보 화면

**Files:**
- Create: `presentation/.../profile/ProfileScreen.kt`, `ProfileViewModel.kt`
- Modify: `presentation/.../ui/FlightDealNavHost.kt`
- Delete: `presentation/.../ui/PlaceholderScreen.kt` (다른 사용처가 없으면)
- Test: `presentation/src/test/.../profile/ProfileViewModelTest.kt`

- [ ] **Step 1: 화면 내용**

제목 `내정보`. 아래 항목들을 구분선으로 나눠 세로로.

**알림**
- 현재 상태: `켜짐` / `꺼짐`. 꺼짐이면 `TextSecondary`가 아니라 눈에 띄게
- 꺼져 있을 때만 설명을 붙인다: "알림이 꺼져 있어 가격이 바뀌어도 알려드릴 수 없어요"
- `알림 설정 열기` 버튼 → 시스템 설정
  ```kotlin
  Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
      .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
      .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
  ```
  **API 26 미만은 이 액션이 없다.** minSdk가 26이라 괜찮지만, 채널이 아직 안 만들어진
  상태(알림을 한 번도 안 보낸 새 설치)에서는 이 인텐트가 실패할 수 있다.
  `ACTION_APP_NOTIFICATION_SETTINGS`로 물러서고, 그것도 실패하면 앱 상세 설정으로.
  **어느 경로로도 못 열면 조용히 아무 일도 없으면 안 된다** — 스낵바로 알린다
- **화면이 다시 보일 때마다 상태를 새로 읽어야 한다.** 사용자가 설정에서 켜고
  돌아왔는데 화면이 "꺼짐" 그대로면 고쳐지지 않은 줄 안다.
  `Lifecycle.Event.ON_RESUME`에서 다시 읽는다

**가격 확인**
- "`WorkScheduler.INTERVAL_HOURS`시간마다 확인해요"
- "기기가 절전 상태면 조금 늦을 수 있어요" — WorkManager는 정확한 시각을 보장하지 않는다.
  약속하지 않은 것을 약속하지 않는다

**저장된 데이터**
- "가격 이력 N건" (`observeCount()`)
- "모든 데이터는 이 기기에만 저장돼요. 계정도, 서버도 없어요."
- `이력 지우기` — **확인 다이얼로그 필수.** 추적 항목은 남는다는 것을 문구에 명시할 것
- 0건이면 지우기 버튼을 숨긴다

**가격 정보**
- "Travelpayouts (Aviasales)" — 누르면 Custom Tabs로 `https://www.aviasales.com`
- "표시 가격은 참고가예요"

**앱**
- 버전: `BuildConfig.VERSION_NAME` (`:presentation`의 BuildConfig)

- [ ] **Step 2: ViewModel 테스트**

```kotlin
    @Test fun `이력 건수를 그대로 흘려보낸다`()
    @Test fun `이력을 지우면 저장소의 삭제를 부른다`()
```

알림 상태는 안드로이드 API를 직접 읽으므로 단위 테스트 대상이 아니다.
**억지로 테스트하려고 추상화를 만들지 마라** — 기기에서 확인한다.

- [ ] **Step 3: 탭 연결**

`composable(Tab.Profile.route) { ProfileScreen() }`.

- [ ] **Step 4: 빌드·테스트**

- [ ] **Step 5: 기기 확인**

`emulator-5554`에서:
1. 내정보 탭을 열어 이력 건수가 실제 DB와 맞는지 대조한다 (sqlite로 `SELECT COUNT(*)`)
2. `알림 설정 열기`를 눌러 시스템 설정이 열리는지
3. **거기서 "가격 변동" 채널을 끄고 앱으로 돌아와 상태가 `꺼짐`으로 바뀌는지** —
   ON_RESUME 재조회가 실제로 되는지 보는 것이다. 다시 켜서 되돌린다
4. `이력 지우기` → 확인 → 건수가 0이 되고, **추적 탭의 카드는 그대로 남아 있는지**
   확인한다. 카드가 사라지면 반대로 지운 것이다
5. 라이트·다크 두 모드 스크린샷

`.../profile-light.png`, `.../profile-dark.png`, `.../profile-notif-off.png`에 저장.
끝나면 `cmd uimode night no`로 되돌린다.

**주의: 4번은 이 에뮬레이터에 며칠치 실제 이력이 쌓여 있는 것을 지운다.**
지우기 전에 `SELECT COUNT(*)`를 기록해 두고, 지운 뒤 값이 0이 된 것과
추적 항목이 남은 것을 함께 보고할 것.

- [ ] **Step 6: 커밋** — `feat: 내정보 화면 추가`
