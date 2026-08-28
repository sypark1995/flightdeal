# 가격 추이 그래프 구현 계획

**Goal:** 추적 중인 여정의 카드를 누르면 그동안 모은 가격 이력이 선 그래프로 펼쳐진다.

**Architecture:** 데이터는 이미 흐르고 있다 — `TrackingViewModel.itemFlow()`가
`observeHistory(id, 90일)`로 전체 이력을 가져와서 마지막 두 개만 쓰고 버린다.
그 목록을 `TrackedItem`에 실어 카드까지 보내고, 카드가 펼쳐질 때 그린다.
**새 화면도, 새 ViewModel도, 새 네비게이션 인자도 필요 없다.**

그래프의 좌표 계산은 Compose를 모르는 순수 함수로 분리한다. Canvas 안에 섞어 넣으면
기기 없이는 검증할 수 없는데, 좌표 계산이야말로 조용히 틀리는 곳이다.

**Tech Stack:** Jetpack Compose Canvas만 쓴다. **차트 라이브러리를 추가하지 않는다.**

## Global Constraints

- Java 소스 파일 금지 — `.java` 파일이 0개여야 한다
- `:domain`은 아무것도 의존하지 않는다. 안드로이드 타입 금지
- 비동기는 Coroutines/Flow. RxJava·LiveData 금지. KSP만, kapt 금지
- 가격은 `Won` value class
- **새 의존성 금지**
- 주석은 한국어로, **왜**를 적을 것
- 커밋에 Co-Authored-By나 Claude/AI 트레일러 금지

---

## Task 1: 좌표 계산 (순수 함수)

**Files:**
- Create: `presentation/src/main/java/com/sypark/flightdeal/tracking/PriceChartGeometry.kt`
- Test: `presentation/src/test/java/com/sypark/flightdeal/tracking/PriceChartGeometryTest.kt`

이 파일에는 **안드로이드 import가 하나도 없어야 한다.** `androidx.compose.ui.geometry.Offset`도
쓰지 마라 — 그걸 쓰는 순간 JVM 테스트에서 못 부른다.

**Produces:** `PriceChartGeometry.of(snapshots, targetPrice)` → `PriceChartGeometry`

```kotlin
package com.sypark.flightdeal.tracking

import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.Won

/** 0..1로 정규화된 점. y는 0이 위(비쌈), 1이 아래(쌈) — Canvas 좌표계와 같은 방향이다. */
data class ChartPoint(val x: Float, val y: Float)

/**
 * @param scaleLow 세로축 아래 끝 가격. 축 눈금으로 그대로 표시한다.
 * @param scaleHigh 세로축 위 끝 가격.
 * @param targetY 목표가 선의 y. 목표가가 없거나 축 범위 밖이면 null이다.
 */
data class PriceChartGeometry(
    val points: List<ChartPoint>,
    val scaleLow: Won,
    val scaleHigh: Won,
    val targetY: Float?,
) {
    companion object {
        fun of(snapshots: List<PriceSnapshot>, targetPrice: Won?): PriceChartGeometry { ... }
    }
}
```

**규칙 — 이 넷이 이 작업의 전부다:**

1. **가로는 인덱스가 아니라 시각에 비례한다.** 워커가 사흘 못 돌면 점 사이가 사흘만큼
   벌어져야 한다. 인덱스로 균등하게 놓으면 그래프가 "언제 바뀌었나"에 대해 거짓말을 한다 —
   이 앱이 답하려는 질문이 바로 그것이다.
   `x = (t - tMin) / (tMax - tMin)`, epoch second로 계산한다.
2. **분모가 0이 되는 경우를 전부 가운데로 보낸다.** 점이 하나뿐이거나 모든 가격이 같으면
   `(max - min)`이 0이다. 나누면 `NaN`이 나오고 Canvas는 아무것도 그리지 않거나 튄다.
   그럴 때 x도 y도 `0.5f`로 둔다 — 수평선이 한가운데 그려진다.
3. **세로축을 목표가에 맞춰 늘리지 않는다.** 30만 원짜리 항공권에 목표가를 5만 원으로
   잡아두면 축이 다섯 배로 늘어나 실제 가격 변동이 납작한 직선이 된다. 목표가가 데이터
   범위 밖이면 `targetY`를 null로 두고 선을 그리지 않는다. 목표가 숫자는 카드에 이미
   글자로 있다.
4. **y는 뒤집는다.** 비싼 값이 위로 가야 한다. `y = (scaleHigh - price) / (scaleHigh - scaleLow)`

- [ ] **Step 1: 실패하는 테스트를 먼저 쓴다**

```kotlin
    private fun snapshot(price: Int, atEpochSecond: Long) = PriceSnapshot(
        trackedRouteId = 1,
        price = Won(price),
        tripType = TripType.ROUND_TRIP,
        capturedAt = Instant.ofEpochSecond(atEpochSecond),
    )

    @Test
    fun `가로 위치는 인덱스가 아니라 시각에 비례한다`() {
        // 첫 점에서 1시간 뒤, 그리고 25시간 뒤. 인덱스로 놓으면 0, 0.5, 1이 된다.
        val geometry = PriceChartGeometry.of(
            listOf(
                snapshot(300_000, 0),
                snapshot(280_000, 3_600),
                snapshot(260_000, 90_000),
            ),
            targetPrice = null,
        )

        assertEquals(0f, geometry.points[0].x, 0.001f)
        assertEquals(0.04f, geometry.points[1].x, 0.005f)
        assertEquals(1f, geometry.points[2].x, 0.001f)
    }

    @Test
    fun `가격이 모두 같아도 NaN이 되지 않는다`() {
        val geometry = PriceChartGeometry.of(
            listOf(snapshot(300_000, 0), snapshot(300_000, 3_600)),
            targetPrice = null,
        )

        // 나눗셈의 분모가 0이다. 가운데 수평선으로 그린다.
        geometry.points.forEach { assertEquals(0.5f, it.y, 0.001f) }
        assertTrue(geometry.points.none { it.y.isNaN() })
    }

    @Test
    fun `점이 하나면 한가운데에 놓는다`() {
        val geometry = PriceChartGeometry.of(listOf(snapshot(300_000, 0)), targetPrice = null)

        assertEquals(1, geometry.points.size)
        assertEquals(0.5f, geometry.points.single().x, 0.001f)
        assertEquals(0.5f, geometry.points.single().y, 0.001f)
    }

    @Test
    fun `비싼 값이 위로 간다`() {
        val geometry = PriceChartGeometry.of(
            listOf(snapshot(300_000, 0), snapshot(200_000, 3_600)),
            targetPrice = null,
        )

        assertEquals(0f, geometry.points[0].y, 0.001f)
        assertEquals(1f, geometry.points[1].y, 0.001f)
    }

    @Test
    fun `목표가가 범위 밖이면 선을 그리지 않는다`() {
        // 축을 목표가까지 늘리면 실제 변동이 납작해진다.
        val geometry = PriceChartGeometry.of(
            listOf(snapshot(300_000, 0), snapshot(280_000, 3_600)),
            targetPrice = Won(50_000),
        )

        assertNull(geometry.targetY)
        assertEquals(Won(280_000), geometry.scaleLow)
        assertEquals(Won(300_000), geometry.scaleHigh)
    }

    @Test
    fun `목표가가 범위 안이면 비율대로 놓는다`() {
        val geometry = PriceChartGeometry.of(
            listOf(snapshot(300_000, 0), snapshot(200_000, 3_600)),
            targetPrice = Won(250_000),
        )

        assertEquals(0.5f, geometry.targetY!!, 0.001f)
    }

    @Test
    fun `이력이 없으면 점도 없다`() {
        val geometry = PriceChartGeometry.of(emptyList(), targetPrice = null)

        assertTrue(geometry.points.isEmpty())
    }
```

- [ ] **Step 2: 실패를 확인한다**

`./gradlew :presentation:testDebugUnitTest --tests "*PriceChartGeometryTest*"`
컴파일 실패(클래스 없음)를 확인한다.

- [ ] **Step 3: 구현한다** — 위 네 규칙대로.

- [ ] **Step 4: 통과를 확인한다.** 특히 `가격이 모두 같아도 NaN이 되지 않는다`가
      통과하는지 볼 것. 여기가 이 작업에서 실제로 깨지는 지점이다.

- [ ] **Step 5: 커밋** — `feat: 가격 추이 그래프 좌표 계산 추가`

---

## Task 2: 카드를 펼쳐 그래프를 그린다

**Files:**
- Create: `presentation/src/main/java/com/sypark/flightdeal/tracking/PriceChart.kt`
- Modify: `presentation/.../tracking/TrackingUiState.kt` — `TrackedItem`에 `history` 추가
- Modify: `presentation/.../tracking/TrackingViewModel.kt` — 이미 가져온 목록을 실어 보낸다
- Modify: `presentation/.../tracking/TrackedRouteCard.kt` — 펼침 상태와 그래프
- Test: `presentation/src/test/java/com/sypark/flightdeal/tracking/TrackingViewModelTest.kt`

**Consumes:** Task 1의 `PriceChartGeometry.of(snapshots, targetPrice)`

- [ ] **Step 1: `TrackedItem`에 이력을 싣는다**

```kotlin
/**
 * @param history 보관 기간 안의 전체 관측 이력, 시간 오름차순. 그래프의 재료다.
 */
data class TrackedItem(
    val tracked: TrackedRoute,
    val latest: PriceSnapshot?,
    val previous: PriceSnapshot?,
    val history: List<PriceSnapshot>,
)
```

`TrackingViewModel.itemFlow()`는 이미 `recent`를 전부 들고 있다. `history = recent`를 더한다.
**조회를 새로 추가하지 마라** — 같은 데이터를 두 번 읽게 된다.

- [ ] **Step 2: ViewModel 테스트를 더한다**

```kotlin
    @Test
    fun `카드에 전체 이력이 실린다`() = runTest {
        // 그래프는 마지막 두 점이 아니라 그동안 모은 전부를 그린다.
        ...
        assertEquals(listOf(Won(300_000), Won(250_000), Won(250_000)), item.history.map { it.price })
    }
```

기존 단언은 하나도 바꾸지 마라.

- [ ] **Step 3: `PriceChart` Composable**

```kotlin
@Composable
fun PriceChart(
    snapshots: List<PriceSnapshot>,
    targetPrice: Won?,
    modifier: Modifier = Modifier,
)
```

- 높이 120.dp, `fillMaxWidth`
- `Canvas` 안에서 `PriceChartGeometry.of(...)`의 0..1 좌표를 실제 크기로 곱한다.
  선 굵기가 잘리지 않도록 위아래로 4.dp씩 여백을 준다 — 여백은 여기서 주고,
  기하 계산은 순수하게 둔다
- 선: `Indigo`, `Path` + `drawPath`, `Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)`
- 마지막 점에 `Indigo` 원 (반지름 3.dp)
- `targetY`가 있으면 `TextSecondary`로 점선 가로선 (`PathEffect.dashPathEffect`)
- 점이 **2개 미만이면 선을 그리지 않고** "가격을 두 번 이상 확인하면 추이를 보여드릴게요"를
  `TextSecondary` 12.sp로 보여준다. 점 하나로 선을 그으면 아무것도 안 그려지거나
  빈 상자만 남는다
- 축 눈금은 좌상단에 `scaleHigh`, 좌하단에 `scaleLow`를 `formatWon`으로 11.sp `TextSecondary`.
  **눈금 없이 선만 그리면 축이 0에서 시작하지 않는다는 사실이 숨겨져 과장돼 보인다**

- [ ] **Step 4: 카드를 펼친다**

`TrackedRouteCard`에 `var expanded by remember { mutableStateOf(false) }`를 두고,
`Column`에 `.clickable { expanded = !expanded }`와 `.animateContentSize()`를 건다.
펼쳤을 때만 `PriceChart`를 그린다.

**해제 텍스트의 클릭이 카드 펼침으로 새어나가면 안 된다.** 해제는 이미 자기
`clickable`을 갖고 있어 이벤트를 소비하지만, 실제로 눌러 확인할 것 — 해제를 눌렀을 때
카드가 함께 펼쳐지면 안 된다.

펼침 여부를 알 수 있게 날짜 줄 오른쪽에 `▾`/`▴`를 `TextSecondary` 11.sp로 둔다.

- [ ] **Step 5: 빌드와 테스트**

```bash
./gradlew :domain:test :data:testDebugUnitTest :presentation:testDebugUnitTest :presentation:assembleDebug
```

- [ ] **Step 6: 기기에서 확인한다**

`emulator-5554`에는 실제 추적 항목과 이력이 있다. 설치하고 카드를 눌러 그래프가
그려지는지 본다. 점이 2개뿐이라 선이 짧다면, 스냅샷을 sqlite로 몇 개 더 넣어
(시각을 며칠씩 벌려서) **시간 간격이 그래프에 반영되는지** 확인한다 — Task 1의
핵심 규칙이 화면에서도 지켜지는지 보는 것이다. 스크린샷을 남긴다.

- [ ] **Step 7: 커밋** — `feat: 추적 카드를 펼치면 가격 추이 그래프를 보여준다`
