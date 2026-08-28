# flightdeal 기반 구축 (0~3단계) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Travelpayouts의 한국 노선 데이터를 검증하고, 3모듈 Clean Architecture 골격 위에 특가 피드 화면을 Fake 데이터로 완성한다.

**Architecture:** `:presentation` → `:domain` ← `:data` 3모듈. `:domain`은 순수 Kotlin JVM 모듈로 안드로이드 프레임워크에 의존하지 않으며, 핵심 계산 로직(할인율, 가격 변동 감지)이 여기 순수 함수로 들어가 JVM 단위 테스트로 검증된다. `:data`는 `FlightPriceRepository` 구현체를 제공하며, 이 계획서 범위에서는 `FakeFlightPriceRepository` 하나만 만든다. 실제 API 연동은 0단계 검증 결과를 확인한 뒤 별도 계획서에서 진행한다.

**Tech Stack:** Kotlin 2.1.0, AGP 8.7.3, Hilt, Coroutines/Flow, XML DataBinding, Navigation Component, Material 3, JUnit4 + Turbine

## Global Constraints

spec `docs/superpowers/specs/2026-08-28-flightdeal-design.md`의 프로젝트 전역 요구사항. **모든 태스크에 적용된다.**

- **패키지 루트:** `com.sypark.flightdeal`
- **compileSdk / targetSdk: 36**, **minSdk: 26**. minSdk 26이므로 `java.time`을 core library desugaring 없이 그대로 쓴다.
- **Java 17**, `jvmTarget = "17"`
- **의존성 방향:** `:domain`은 아무것도 의존하지 않는다. `:data`는 `:domain`만 의존한다. `:presentation`은 `:domain`과 `:data`를 의존한다. 이 방향을 거스르는 코드는 작성하지 않는다.
- **`:domain`에 Travelpayouts라는 단어가 등장해서는 안 된다.** 안드로이드 프레임워크 타입(`Context`, `Bundle`, `LiveData` 등)도 등장하면 안 된다.
- **비동기는 Coroutines/Flow로 통일한다. RxJava를 추가하지 않는다.**
- **어노테이션 처리는 KSP를 쓴다. kapt를 쓰지 않는다.**
- **의존성은 전부 `gradle/libs.versions.toml`(Version Catalog)에 선언한다.** 모듈 build 파일에 버전 문자열을 직접 쓰지 않는다.
- **Paging 3를 추가하지 않는다.** (spec §3.1)
- **차트 라이브러리를 추가하지 않는다.** (spec §3.2)
- **가격은 `Won` value class로 다룬다.** raw `Int`로 가격을 주고받지 않는다.
- **강조색 `#4338E0`, 강조 배경 `#EDEBFF`, 배경 `#FFFFFF`, 서피스 `#F4F5F9`, 경계선 `#EAECF3`, 본문 `#0F1115`.**
- **가격 하락은 항상 초록(`#0E9E6E`), 상승은 항상 빨강(`#D93A3A`).** 브랜드 강조색과 절대 섞지 않는다.
- **커밋 메시지:** `feat/fix/build/chore/ci/docs/style/refactor/test/perf` 접두사 + 한국어 제목. 커밋에 Claude를 참여자로 기록하지 않는다.
- **`local.properties`는 절대 커밋하지 않는다.**

### 버전 고정값

`gradle/libs.versions.toml`에 아래 값을 쓴다. Task 2의 빌드 단계에서 하나라도 해석에 실패하면 그 자리에서 최신 안정 버전을 확인해 올린다.

| 항목 | 버전 |
|---|---|
| AGP | 8.7.3 |
| Kotlin | 2.1.0 |
| KSP | 2.1.0-1.0.29 |
| Hilt | 2.53.1 |
| Coroutines | 1.9.0 |
| Navigation | 2.8.5 |
| Lifecycle | 2.8.7 |
| core-ktx | 1.15.0 |
| appcompat | 1.7.0 |
| material | 1.12.0 |
| constraintlayout | 2.2.0 |
| Glide | 4.16.0 |
| Shimmer | 0.5.0 |
| Turbine | 1.2.0 |
| JUnit | 4.13.2 |

---

## File Structure

이 계획서가 끝났을 때 존재하는 파일들이다.

### 루트

| 파일 | 책임 |
|---|---|
| `settings.gradle.kts` | 모듈 3개 등록, 저장소 선언 |
| `build.gradle.kts` | 플러그인 alias 선언 (apply false) |
| `gradle/libs.versions.toml` | 모든 의존성 버전의 단일 출처 |
| `gradle.properties` | JVM 힙, AndroidX, 빌드 피처 플래그 |
| `.gitignore` | 빌드 산출물, `local.properties`, 에디터/도구 디렉터리 |
| `docs/research/2026-08-28-travelpayouts-icn-검증.md` | 0단계 검증 결과 기록 |
| `docs/research/fixtures/` | 실제 API 응답 원본. 나중에 파싱 테스트의 픽스처가 된다 |

### `:domain` (순수 Kotlin JVM)

| 파일 | 책임 |
|---|---|
| `model/Won.kt` | 금액 value class. 덧셈·비교·퍼센트 계산 |
| `model/Airport.kt` | 공항, 노선 |
| `model/PriceQuote.kt` | 가격 하나. 노선 + 날짜 + 금액 + 딥링크 |
| `model/PriceStats.kt` | 가격 분포 요약 (중앙값/최소/최대/표본수) |
| `model/TrackedRoute.kt` | 추적 대상 노선 |
| `model/PriceSnapshot.kt` | 시점별 가격 기록 |
| `model/AppResult.kt` | 성공 / 빈 데이터 / 네트워크 오류 / 알 수 없음 |
| `repository/FlightPriceRepository.kt` | 가격 조회 인터페이스 |
| `usecase/CalculateDiscountUseCase.kt` | 순수 함수. 중앙값 대비 할인율 |
| `usecase/DetectPriceChangesUseCase.kt` | 순수 함수. 스냅샷 비교 |
| `usecase/GetDealFeedUseCase.kt` | 피드 조회 + 할인율 결합 |

`model/`을 파일 하나로 뭉치지 않는 이유: 각 모델이 자기 동작(예: `Won`의 퍼센트 계산)을 갖고 있고, 앞으로 추적/이력 쪽 모델만 따로 커질 것이기 때문이다.

### `:data`

| 파일 | 책임 |
|---|---|
| `fake/FakeFlightPriceRepository.kt` | 개발·테스트용 인메모리 구현체 |
| `fake/FakeDealFixtures.kt` | Fake가 반환할 고정 데이터 |
| `di/RepositoryModule.kt` | Hilt 바인딩 |

### `:presentation`

| 파일 | 책임 |
|---|---|
| `FlightDealApp.kt` | `@HiltAndroidApp` |
| `MainActivity.kt` | 단일 액티비티. BottomNav + NavHost |
| `feed/DealFeedFragment.kt` | 특가 피드 화면 |
| `feed/DealFeedViewModel.kt` | 피드 상태 관리 |
| `feed/DealFeedUiState.kt` | 로딩/성공/빈/오류 4상태 |
| `feed/DealAdapter.kt` | RecyclerView 어댑터 + DiffUtil |
| `feed/DealBindingAdapters.kt` | DataBinding 어댑터 (가격 포맷, 할인 배지) |
| `res/values/colors.xml` | 인디고 팔레트 |
| `res/values/themes.xml` | 앱 테마 |
| `res/layout/activity_main.xml` | BottomNav + NavHostFragment |
| `res/layout/fragment_deal_feed.xml` | 피드 화면 |
| `res/layout/item_deal.xml` | 딜 카드 |
| `res/navigation/nav_graph.xml` | 4탭 그래프 |
| `res/menu/bottom_nav_menu.xml` | 하단 탭 |

---

## Task 1: Travelpayouts 실응답 검증

**이 태스크는 사람의 개입이 필요하다.** Travelpayouts 가입은 대신 해줄 수 없다.
검증 결과가 나쁘면 이후 계획이 바뀌므로, 여기서 멈추고 결과를 보고할 것.

**Files:**
- Create: `docs/research/2026-08-28-travelpayouts-icn-검증.md`
- Create: `docs/research/fixtures/calendar-ICN-TYO.json`
- Create: `docs/research/fixtures/calendar-ICN-BKK.json`
- Create: `docs/research/fixtures/calendar-ICN-DAD.json`
- Create: `local.properties` (커밋하지 않음)

**Interfaces:**
- Consumes: 없음
- Produces: 실제 응답 JSON 픽스처 3건. 이후 계획서의 파싱 테스트가 이 파일들을 그대로 쓴다. `TRAVELPAYOUTS_TOKEN`, `TRAVELPAYOUTS_MARKER`.

- [ ] **Step 1: Travelpayouts 가입 및 토큰 발급 (사용자 작업)**

https://www.travelpayouts.com 에서 가입한 뒤 대시보드에서 다음 두 값을 확보한다.

- API 토큰 (`token` 파라미터에 들어감)
- 마커 / 파트너 ID (`marker` 파라미터. 딥링크 커미션 추적용)

- [ ] **Step 2: 토큰을 `local.properties`에 기록**

```properties
TRAVELPAYOUTS_TOKEN=발급받은_토큰
TRAVELPAYOUTS_MARKER=발급받은_마커
```

- [ ] **Step 3: 현재 엔드포인트 확인**

브라우저로 https://support.travelpayouts.com/hc/en-us/articles/203956163-Aviasales-Data-API 를 열어
**날짜별 최저가를 반환하는 엔드포인트의 현재 경로와 파라미터 이름**을 확인한다.

확인해야 할 것:
- 경로 (`/v1/prices/calendar` 인지, `/aviasales/v3/prices_for_dates` 인지)
- 출발/도착 파라미터 이름과 형식 (IATA 공항 코드인지 도시 코드인지 — 서울은 `SEL`, 인천은 `ICN`)
- 월 지정 파라미터 이름과 형식 (`depart_date=2026-10` 인지 `departure_at=2026-10` 인지)
- 통화 파라미터로 `krw`가 되는지

**확인한 경로를 Step 4의 curl에 반영한다.** 문서와 다르면 문서를 따른다.

- [ ] **Step 4: 세 노선의 실제 응답 저장**

`$TOKEN`을 실제 토큰으로 치환하고, Step 3에서 확인한 경로/파라미터로 조정해서 실행한다.

```bash
mkdir -p docs/research/fixtures
TOKEN=발급받은_토큰
MONTH=$(date -v+2m +%Y-%m)   # 2개월 뒤. Linux면 date -d '+2 months' +%Y-%m

for pair in TYO:도쿄 BKK:방콕 DAD:다낭; do
  DEST=${pair%%:*}
  curl -sS "https://api.travelpayouts.com/v1/prices/calendar?origin=ICN&destination=${DEST}&depart_date=${MONTH}&currency=krw&token=${TOKEN}" \
    | python3 -m json.tool > "docs/research/fixtures/calendar-ICN-${DEST}.json"
  echo "=== ICN-${DEST} ==="
  python3 -c "
import json,sys
d=json.load(open('docs/research/fixtures/calendar-ICN-${DEST}.json'))
data=d.get('data') or {}
print('success:', d.get('success'))
print('날짜 개수:', len(data))
for k in list(data)[:3]:
    print(' ', k, data[k])
"
done
```

- [ ] **Step 5: 판정**

각 노선에 대해 아래를 확인한다.

| 확인 항목 | 통과 기준 |
|---|---|
| `success` | `true` |
| 날짜 개수 | 노선당 **10일 이상** |
| 가격 필드 | KRW로 보이는 값 (수만~수십만 단위) |
| 딥링크 정보 | 예약처로 연결할 수 있는 필드 또는 `link` 존재 |

**세 노선 모두 통과** → 계획대로 진행.
**일부만 통과** → 통과한 노선만 MVP 대상으로 좁히고 진행. 검증 문서에 명시.
**전부 실패하거나 날짜가 10일 미만** → **여기서 멈춘다.** 데이터 소스를 다시 고르는 결정이 필요하므로 사용자에게 결과를 보고할 것. Duffel 샌드박스가 대안이며, `:domain`과 `:presentation`은 영향받지 않는다.

- [ ] **Step 6: 검증 결과 문서 작성**

`docs/research/2026-08-28-travelpayouts-icn-검증.md`에 아래 내용을 채워 넣는다. 실제 관측값만 쓴다.

```markdown
# Travelpayouts 한국 노선 검증 (2026-08-28)

## 사용한 엔드포인트

GET (Step 3에서 확인한 실제 경로)

파라미터: (실제 사용한 파라미터 이름과 값)

## 결과

| 노선 | success | 날짜 개수 | 최저가 | 최고가 | 판정 |
|---|---|---|---|---|---|
| ICN → TYO | | | | | |
| ICN → BKK | | | | | |
| ICN → DAD | | | | | |

## 응답 스키마

(실제 응답에서 관측한 필드명과 타입. 추측 금지)

## 결론

(계획대로 진행 / 노선 축소 / 소스 교체)
```

- [ ] **Step 7: 커밋**

```bash
git add docs/research
git commit -m "docs: Travelpayouts 한국 노선 데이터 검증 결과 기록"
```

`local.properties`가 스테이징되지 않았는지 `git status`로 확인한다. (`.gitignore`는 Task 2에서 추가되므로 이 시점에는 아직 없다.)

---

## Task 2: 프로젝트 골격

**Files:**
- Create: `.gitignore`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- Create: `domain/build.gradle.kts`, `data/build.gradle.kts`, `presentation/build.gradle.kts`
- Create: `data/src/main/AndroidManifest.xml`, `presentation/src/main/AndroidManifest.xml`
- Create: `presentation/src/main/java/com/sypark/flightdeal/FlightDealApp.kt`
- Test: `domain/src/test/java/com/sypark/flightdeal/domain/SanityTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces: `:domain`, `:data`, `:presentation` 모듈. Hilt가 켜진 `FlightDealApp`. Version Catalog alias 전체.

- [ ] **Step 1: Gradle Wrapper 생성**

기존 프로젝트에서 복사한다. `hey_ticket`은 구버전이므로 `finnhub` 쪽이 더 최신일 가능성이 높다.

```bash
cd ~/AndroidStudioProjects/flightdeal
cp -r ~/AndroidStudioProjects/finnhub/gradle/wrapper gradle/ 2>/dev/null || \
  cp -r ~/AndroidStudioProjects/hey_ticket/gradle/wrapper gradle/
cp ~/AndroidStudioProjects/finnhub/gradlew . 2>/dev/null || cp ~/AndroidStudioProjects/hey_ticket/gradlew .
cp ~/AndroidStudioProjects/finnhub/gradlew.bat . 2>/dev/null || cp ~/AndroidStudioProjects/hey_ticket/gradlew.bat .
chmod +x gradlew
cat gradle/wrapper/gradle-wrapper.properties
```

AGP 8.7.3은 Gradle 8.9 이상을 요구한다. `distributionUrl`이 8.9 미만이면 아래로 교체한다.

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
```

- [ ] **Step 2: `.gitignore` 작성**

```gitignore
*.iml
.gradle/
/local.properties
/.idea/
.DS_Store
/build
*/build/
/captures
.externalNativeBuild
.cxx
local.properties
/.kotlin

# 도구 디렉터리 — 커밋하지 않는다
.claude/
.superpowers/
```

- [ ] **Step 3: `gradle/libs.versions.toml` 작성**

```toml
[versions]
agp = "8.7.3"
kotlin = "2.1.0"
ksp = "2.1.0-1.0.29"
hilt = "2.53.1"
coroutines = "1.9.0"
navigation = "2.8.5"
lifecycle = "2.8.7"
coreKtx = "1.15.0"
appcompat = "1.7.0"
material = "1.12.0"
constraintlayout = "2.2.0"
glide = "4.16.0"
shimmer = "0.5.0"
turbine = "1.2.0"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-appcompat = { module = "androidx.appcompat:appcompat", version.ref = "appcompat" }
androidx-constraintlayout = { module = "androidx.constraintlayout:constraintlayout", version.ref = "constraintlayout" }
android-material = { module = "com.google.android.material:material", version.ref = "material" }

androidx-fragment-ktx = { module = "androidx.fragment:fragment-ktx", version = "1.8.5" }
androidx-activity-ktx = { module = "androidx.activity:activity-ktx", version = "1.9.3" }
androidx-lifecycle-viewmodel = { module = "androidx.lifecycle:lifecycle-viewmodel-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }

androidx-navigation-fragment = { module = "androidx.navigation:navigation-fragment-ktx", version.ref = "navigation" }
androidx-navigation-ui = { module = "androidx.navigation:navigation-ui-ktx", version.ref = "navigation" }

hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }

kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }

glide = { module = "com.github.bumptech.glide:glide", version.ref = "glide" }
glide-ksp = { module = "com.github.bumptech.glide:ksp", version.ref = "glide" }
shimmer = { module = "com.facebook.shimmer:shimmer", version.ref = "shimmer" }

junit = { module = "junit:junit", version.ref = "junit" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
navigation-safeargs = { id = "androidx.navigation.safeargs.kotlin", version.ref = "navigation" }
```

- [ ] **Step 4: `settings.gradle.kts` 작성**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "flightdeal"
include(":presentation")
include(":data")
include(":domain")
```

- [ ] **Step 5: 루트 `build.gradle.kts`와 `gradle.properties` 작성**

`build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.navigation.safeargs) apply false
}
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

- [ ] **Step 6: `:domain` 모듈 — 순수 Kotlin JVM**

`domain/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation("javax.inject:javax.inject:1")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

안드로이드 라이브러리가 아니라 JVM 모듈로 만드는 이유: 안드로이드 타입을 **실수로도** 쓸 수 없게 컴파일러가 막아준다. 테스트도 에뮬레이터 없이 즉시 돈다.

- [ ] **Step 7: `:data` 모듈**

`data/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.sypark.flightdeal.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

`data/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

빈 `data/consumer-rules.pro` 파일도 만든다.

- [ ] **Step 8: `:presentation` 모듈**

`presentation/build.gradle.kts`:

```kotlin
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.navigation.safeargs)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.sypark.flightdeal"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sypark.flightdeal"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TRAVELPAYOUTS_TOKEN", "\"${localProps.getProperty("TRAVELPAYOUTS_TOKEN", "")}\"")
        buildConfigField("String", "TRAVELPAYOUTS_MARKER", "\"${localProps.getProperty("TRAVELPAYOUTS_MARKER", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        dataBinding = true
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.android.material)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.glide)
    ksp(libs.glide.ksp)
    implementation(libs.shimmer)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
```

빈 `presentation/proguard-rules.pro` 파일도 만든다.

- [ ] **Step 9: Application 클래스와 매니페스트**

`presentation/src/main/java/com/sypark/flightdeal/FlightDealApp.kt`:

```kotlin
package com.sypark.flightdeal

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FlightDealApp : Application()
```

`presentation/src/main/AndroidManifest.xml`. `Theme.FlightDeal`은 Task 8에서 만들므로
지금은 Material3 기본 테마를 쓴다. 런처 아이콘도 아직 없으므로 `android:icon`을 넣지 않는다.
Task 8 Step 7에서 둘 다 되돌린다.

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".FlightDealApp"
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar">
    </application>
</manifest>
```

`presentation/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">flightdeal</string>
</resources>
```

- [ ] **Step 10: sanity 테스트 작성**

`domain/src/test/java/com/sypark/flightdeal/domain/SanityTest.kt`:

```kotlin
package com.sypark.flightdeal.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SanityTest {
    @Test
    fun `테스트 인프라가 동작한다`() {
        assertEquals(4, 2 + 2)
    }
}
```

- [ ] **Step 11: 빌드와 테스트 실행**

```bash
./gradlew :domain:test :presentation:assembleDebug
```

기대: `BUILD SUCCESSFUL`.

의존성 해석 실패가 나면 해당 라이브러리의 최신 안정 버전을 확인해
`libs.versions.toml`을 고친다. 버전을 모듈 build 파일에 직접 쓰지 않는다.

`compileSdk 36`을 찾을 수 없다는 오류가 나면 Android SDK Manager에서 API 36을 설치한다.
설치가 불가능하면 `:data`와 `:presentation`의 `compileSdk`/`targetSdk`를 35로 낮추고
**그 사실을 커밋 메시지에 남긴다.**

- [ ] **Step 12: 커밋**

```bash
git add .
git status --short
```

`local.properties`가 목록에 없는지 확인한 뒤:

```bash
git commit -m "build: 3모듈 Clean Architecture 골격 및 Version Catalog 구성"
```

---

## Task 3: 도메인 모델과 할인율 계산

**Files:**
- Create: `domain/src/main/java/com/sypark/flightdeal/domain/model/Won.kt`
- Create: `domain/src/main/java/com/sypark/flightdeal/domain/model/Airport.kt`
- Create: `domain/src/main/java/com/sypark/flightdeal/domain/model/PriceQuote.kt`
- Create: `domain/src/main/java/com/sypark/flightdeal/domain/model/PriceStats.kt`
- Create: `domain/src/main/java/com/sypark/flightdeal/domain/model/AppResult.kt`
- Create: `domain/src/main/java/com/sypark/flightdeal/domain/usecase/CalculateDiscountUseCase.kt`
- Test: `domain/src/test/java/com/sypark/flightdeal/domain/model/WonTest.kt`
- Test: `domain/src/test/java/com/sypark/flightdeal/domain/model/PriceStatsTest.kt`
- Test: `domain/src/test/java/com/sypark/flightdeal/domain/usecase/CalculateDiscountUseCaseTest.kt`

**Interfaces:**
- Consumes: Task 2의 `:domain` 모듈
- Produces:
  - `Won(val amount: Int)` — `operator fun compareTo(other: Won): Int`, `fun percentOf(base: Won): Int`
  - `Airport(iata: String, cityKo: String, countryKo: String)`
  - `Route(origin: Airport, destination: Airport)`
  - `PriceQuote(route, departDate: LocalDate, returnDate: LocalDate?, price: Won, airline: String?, foundAt: Instant, deepLink: String?)`
  - `PriceStats(median: Won, min: Won, max: Won, sampleCount: Int)` + `PriceStats.from(prices: List<Won>): PriceStats?`
  - `AppResult<T>` — `Success<T>(data)`, `Empty`, `NetworkError(cause)`, `Unknown(cause)`
  - `CalculateDiscountUseCase.invoke(price: Won, stats: PriceStats): Int?` — 중앙값 대비 할인 퍼센트. 할인이 아니면 `null`

- [ ] **Step 1: `Won` 테스트 작성**

`domain/src/test/java/com/sypark/flightdeal/domain/model/WonTest.kt`:

```kotlin
package com.sypark.flightdeal.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WonTest {

    @Test
    fun `금액을 비교할 수 있다`() {
        assertTrue(Won(189_000) < Won(305_000))
        assertTrue(Won(305_000) > Won(189_000))
        assertEquals(0, Won(189_000).compareTo(Won(189_000)))
    }

    @Test
    fun `기준가 대비 퍼센트를 계산한다`() {
        assertEquals(62, Won(189_000).percentOf(Won(305_000)))
    }

    @Test
    fun `기준가와 같으면 100퍼센트다`() {
        assertEquals(100, Won(189_000).percentOf(Won(189_000)))
    }

    @Test
    fun `기준가가 0이면 100퍼센트로 처리한다`() {
        assertEquals(100, Won(189_000).percentOf(Won(0)))
    }
}
```

`기준가가 0이면` 케이스를 넣는 이유: API가 0원을 돌려주는 일이 실제로 있고,
나눗셈에서 그대로 터진다. 0으로 나누는 경로를 테스트가 먼저 막는다.

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :domain:test --tests "*WonTest*"
```

기대: 컴파일 실패. `Unresolved reference: Won`

- [ ] **Step 3: `Won` 구현**

`domain/src/main/java/com/sypark/flightdeal/domain/model/Won.kt`:

```kotlin
package com.sypark.flightdeal.domain.model

/**
 * 원화 금액. raw Int와 섞이지 않도록 감싼다.
 */
@JvmInline
value class Won(val amount: Int) : Comparable<Won> {

    override fun compareTo(other: Won): Int = amount.compareTo(other.amount)

    /**
     * [base] 대비 이 금액이 몇 퍼센트인지. [base]가 0이면 비교 자체가 무의미하므로 100을 돌려준다.
     *
     * 버림이 아니라 반올림한다. 189,000 / 305,000은 61.97%인데 버리면 61%가 되고,
     * 할인율이 39%로 계산돼 실제 38%와 어긋난다.
     */
    fun percentOf(base: Won): Int {
        if (base.amount == 0) return 100
        val scaled = amount.toLong() * 100 + base.amount / 2
        return (scaled / base.amount).toInt()
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew :domain:test --tests "*WonTest*"
```

기대: PASS (4건)

- [ ] **Step 5: `PriceStats` 테스트 작성**

`domain/src/test/java/com/sypark/flightdeal/domain/model/PriceStatsTest.kt`:

```kotlin
package com.sypark.flightdeal.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceStatsTest {

    @Test
    fun `홀수 개 가격의 중앙값을 구한다`() {
        val stats = PriceStats.from(listOf(Won(100), Won(300), Won(200)))!!
        assertEquals(Won(200), stats.median)
        assertEquals(Won(100), stats.min)
        assertEquals(Won(300), stats.max)
        assertEquals(3, stats.sampleCount)
    }

    @Test
    fun `짝수 개 가격은 가운데 두 값의 평균을 중앙값으로 삼는다`() {
        val stats = PriceStats.from(listOf(Won(100), Won(200), Won(300), Won(500)))!!
        assertEquals(Won(250), stats.median)
    }

    @Test
    fun `가격이 하나면 그 값이 중앙값이자 최소이자 최대다`() {
        val stats = PriceStats.from(listOf(Won(189_000)))!!
        assertEquals(Won(189_000), stats.median)
        assertEquals(Won(189_000), stats.min)
        assertEquals(Won(189_000), stats.max)
    }

    @Test
    fun `빈 목록이면 null이다`() {
        assertNull(PriceStats.from(emptyList()))
    }
}
```

빈 목록에서 `null`을 돌려주게 하는 이유: 한산한 노선은 가격 배열이 비어 온다.
`PriceStats`가 빈 상태로 존재할 수 있으면 이후 계산이 전부 0으로 오염된다.

- [ ] **Step 6: 테스트 실패 확인**

```bash
./gradlew :domain:test --tests "*PriceStatsTest*"
```

기대: 컴파일 실패. `Unresolved reference: PriceStats`

- [ ] **Step 7: `PriceStats` 구현**

`domain/src/main/java/com/sypark/flightdeal/domain/model/PriceStats.kt`:

```kotlin
package com.sypark.flightdeal.domain.model

/**
 * 한 노선·한 달치 가격의 분포 요약. 할인율 배지의 기준이 된다.
 */
data class PriceStats(
    val median: Won,
    val min: Won,
    val max: Won,
    val sampleCount: Int,
) {
    companion object {
        /**
         * @return 가격이 하나도 없으면 null. 빈 분포는 만들지 않는다.
         */
        fun from(prices: List<Won>): PriceStats? {
            if (prices.isEmpty()) return null
            val sorted = prices.sortedBy { it.amount }
            val mid = sorted.size / 2
            val median = if (sorted.size % 2 == 1) {
                sorted[mid]
            } else {
                Won((sorted[mid - 1].amount + sorted[mid].amount) / 2)
            }
            return PriceStats(
                median = median,
                min = sorted.first(),
                max = sorted.last(),
                sampleCount = sorted.size,
            )
        }
    }
}
```

- [ ] **Step 8: 테스트 통과 확인**

```bash
./gradlew :domain:test --tests "*PriceStatsTest*"
```

기대: PASS (4건)

- [ ] **Step 9: 나머지 모델 작성**

이 파일들은 데이터를 담기만 하므로 별도 테스트를 두지 않는다.
동작이 생기면 그때 테스트를 붙인다.

`domain/src/main/java/com/sypark/flightdeal/domain/model/Airport.kt`:

```kotlin
package com.sypark.flightdeal.domain.model

data class Airport(
    val iata: String,
    val cityKo: String,
    val countryKo: String,
)

data class Route(
    val origin: Airport,
    val destination: Airport,
)
```

`domain/src/main/java/com/sypark/flightdeal/domain/model/PriceQuote.kt`:

```kotlin
package com.sypark.flightdeal.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * 특정 노선·날짜의 가격 하나.
 *
 * @param foundAt 이 가격이 관측된 시각. 소스가 캐시 기반이라 조회 시각과 다를 수 있다.
 * @param deepLink 예약처로 연결할 URL. 없을 수 있다.
 */
data class PriceQuote(
    val route: Route,
    val departDate: LocalDate,
    val returnDate: LocalDate?,
    val price: Won,
    val airline: String?,
    val foundAt: Instant,
    val deepLink: String?,
)
```

`domain/src/main/java/com/sypark/flightdeal/domain/model/AppResult.kt`:

```kotlin
package com.sypark.flightdeal.domain.model

/**
 * [Empty]는 오류가 아니다. 데이터 소스가 캐시 기반이라 한산한 노선은
 * 정상적으로 빈 응답을 준다. 이를 오류로 표시하면 사용자는 앱 고장으로 오해한다.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data object Empty : AppResult<Nothing>
    data class NetworkError(val cause: Throwable) : AppResult<Nothing>
    data class Unknown(val cause: Throwable) : AppResult<Nothing>
}
```

- [ ] **Step 10: `CalculateDiscountUseCase` 테스트 작성**

`domain/src/test/java/com/sypark/flightdeal/domain/usecase/CalculateDiscountUseCaseTest.kt`:

```kotlin
package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.PriceStats
import com.sypark.flightdeal.domain.model.Won
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculateDiscountUseCaseTest {

    private val useCase = CalculateDiscountUseCase()

    private fun stats(median: Int) = PriceStats(
        median = Won(median), min = Won(median), max = Won(median), sampleCount = 10,
    )

    @Test
    fun `중앙값보다 싸면 할인율을 돌려준다`() {
        assertEquals(38, useCase(Won(189_000), stats(305_000)))
    }

    @Test
    fun `중앙값과 같으면 null이다`() {
        assertNull(useCase(Won(305_000), stats(305_000)))
    }

    @Test
    fun `중앙값보다 비싸면 null이다`() {
        assertNull(useCase(Won(400_000), stats(305_000)))
    }

    @Test
    fun `표본이 3개 미만이면 신뢰할 수 없으므로 null이다`() {
        val thinStats = PriceStats(Won(305_000), Won(305_000), Won(305_000), sampleCount = 2)
        assertNull(useCase(Won(189_000), thinStats))
    }

    @Test
    fun `할인율이 5퍼센트 미만이면 배지를 달지 않는다`() {
        assertEquals(null, useCase(Won(295_000), stats(305_000)))
    }
}
```

마지막 두 테스트가 이 UseCase의 존재 이유다. 표본 2개짜리 "평균가 대비 −38%"는
사용자를 속이는 숫자이고, "−3%"는 배지를 달 가치가 없다. 둘 다 막는다.

- [ ] **Step 11: 테스트 실패 확인**

```bash
./gradlew :domain:test --tests "*CalculateDiscountUseCaseTest*"
```

기대: 컴파일 실패. `Unresolved reference: CalculateDiscountUseCase`

- [ ] **Step 12: `CalculateDiscountUseCase` 구현**

`domain/src/main/java/com/sypark/flightdeal/domain/usecase/CalculateDiscountUseCase.kt`:

```kotlin
package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.PriceStats
import com.sypark.flightdeal.domain.model.Won
import javax.inject.Inject

/**
 * 중앙값 대비 할인율을 계산한다. 배지로 보여줄 가치가 없으면 null.
 */
class CalculateDiscountUseCase @Inject constructor() {

    operator fun invoke(price: Won, stats: PriceStats): Int? {
        if (stats.sampleCount < MIN_SAMPLE_COUNT) return null
        if (price >= stats.median) return null

        val discount = 100 - price.percentOf(stats.median)
        return if (discount >= MIN_DISCOUNT_PERCENT) discount else null
    }

    private companion object {
        /** 표본이 이보다 적으면 중앙값을 신뢰하지 않는다. */
        const val MIN_SAMPLE_COUNT = 3
        /** 이보다 작은 할인은 배지를 달지 않는다. */
        const val MIN_DISCOUNT_PERCENT = 5
    }
}
```

- [ ] **Step 13: 전체 도메인 테스트 실행**

```bash
./gradlew :domain:test
```

기대: PASS (SanityTest 1 + WonTest 4 + PriceStatsTest 4 + CalculateDiscountUseCaseTest 5 = 14건)

- [ ] **Step 14: 커밋**

```bash
git add domain
git commit -m "feat: 도메인 모델과 할인율 계산 로직 추가"
```

---

## Task 4: 가격 변동 감지

**Files:**
- Create: `domain/src/main/java/com/sypark/flightdeal/domain/model/TrackedRoute.kt`
- Create: `domain/src/main/java/com/sypark/flightdeal/domain/model/PriceSnapshot.kt`
- Create: `domain/src/main/java/com/sypark/flightdeal/domain/usecase/DetectPriceChangesUseCase.kt`
- Test: `domain/src/test/java/com/sypark/flightdeal/domain/usecase/DetectPriceChangesUseCaseTest.kt`

**Interfaces:**
- Consumes: `Won`, `Route` (Task 3)
- Produces:
  - `TrackedRoute(id: Long, route: Route, departDate: LocalDate, returnDate: LocalDate?, targetPrice: Won?, createdAt: Instant)`
  - `PriceSnapshot(trackedRouteId: Long, price: Won, capturedAt: Instant)`
  - `PriceChange(trackedRouteId: Long, previous: Won, current: Won, direction: Direction, reachedTarget: Boolean)`
  - `Direction` — `DOWN`, `UP`
  - `DetectPriceChangesUseCase.invoke(tracked: TrackedRoute, previous: PriceSnapshot?, current: Won): PriceChange?`

이 UseCase가 워커의 알림 판정 로직 전부다. 워커는 이걸 호출하기만 한다.

- [ ] **Step 1: 모델 작성**

`domain/src/main/java/com/sypark/flightdeal/domain/model/TrackedRoute.kt`:

```kotlin
package com.sypark.flightdeal.domain.model

import java.time.Instant
import java.time.LocalDate

data class TrackedRoute(
    val id: Long,
    val route: Route,
    val departDate: LocalDate,
    val returnDate: LocalDate?,
    val targetPrice: Won?,
    val createdAt: Instant,
)
```

`domain/src/main/java/com/sypark/flightdeal/domain/model/PriceSnapshot.kt`:

```kotlin
package com.sypark.flightdeal.domain.model

import java.time.Instant

data class PriceSnapshot(
    val trackedRouteId: Long,
    val price: Won,
    val capturedAt: Instant,
)
```

- [ ] **Step 2: 테스트 작성**

`domain/src/test/java/com/sypark/flightdeal/domain/usecase/DetectPriceChangesUseCaseTest.kt`:

```kotlin
package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.Direction
import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.model.Won
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DetectPriceChangesUseCaseTest {

    private val useCase = DetectPriceChangesUseCase()

    private val route = Route(
        origin = Airport("ICN", "서울", "대한민국"),
        destination = Airport("TYO", "도쿄", "일본"),
    )

    private fun tracked(targetPrice: Won? = null) = TrackedRoute(
        id = 1L,
        route = route,
        departDate = LocalDate.of(2026, 10, 12),
        returnDate = LocalDate.of(2026, 10, 16),
        targetPrice = targetPrice,
        createdAt = Instant.EPOCH,
    )

    private fun snapshot(price: Int) = PriceSnapshot(1L, Won(price), Instant.EPOCH)

    @Test
    fun `가격이 내리면 DOWN 변동을 돌려준다`() {
        val change = useCase(tracked(), snapshot(215_000), Won(189_000))!!
        assertEquals(Direction.DOWN, change.direction)
        assertEquals(Won(215_000), change.previous)
        assertEquals(Won(189_000), change.current)
    }

    @Test
    fun `가격이 오르면 UP 변동을 돌려준다`() {
        val change = useCase(tracked(), snapshot(189_000), Won(215_000))!!
        assertEquals(Direction.UP, change.direction)
    }

    @Test
    fun `가격이 그대로면 null이다`() {
        assertNull(useCase(tracked(), snapshot(189_000), Won(189_000)))
    }

    @Test
    fun `직전 스냅샷이 없으면 알릴 변동이 없으므로 null이다`() {
        assertNull(useCase(tracked(), previous = null, current = Won(189_000)))
    }

    @Test
    fun `목표가 이하로 내려오면 reachedTarget이 true다`() {
        val change = useCase(tracked(targetPrice = Won(200_000)), snapshot(215_000), Won(189_000))!!
        assertTrue(change.reachedTarget)
    }

    @Test
    fun `목표가에 못 미치면 reachedTarget이 false다`() {
        val change = useCase(tracked(targetPrice = Won(150_000)), snapshot(215_000), Won(189_000))!!
        assertFalse(change.reachedTarget)
    }

    @Test
    fun `목표가와 정확히 같으면 도달로 본다`() {
        val change = useCase(tracked(targetPrice = Won(189_000)), snapshot(215_000), Won(189_000))!!
        assertTrue(change.reachedTarget)
    }

    @Test
    fun `목표가를 설정하지 않았으면 reachedTarget은 false다`() {
        val change = useCase(tracked(targetPrice = null), snapshot(215_000), Won(189_000))!!
        assertFalse(change.reachedTarget)
    }

    @Test
    fun `가격이 올랐어도 목표가 이하면 도달로 본다`() {
        val change = useCase(tracked(targetPrice = Won(200_000)), snapshot(150_000), Won(189_000))!!
        assertEquals(Direction.UP, change.direction)
        assertTrue(change.reachedTarget)
    }
}
```

마지막 테스트가 미묘한 지점이다. 가격이 올랐더라도 여전히 목표가 이하라면
사용자에게는 "아직 살 만하다"는 정보다. 방향과 목표 도달은 독립적으로 판정한다.

- [ ] **Step 3: 테스트 실패 확인**

```bash
./gradlew :domain:test --tests "*DetectPriceChangesUseCaseTest*"
```

기대: 컴파일 실패. `Unresolved reference: DetectPriceChangesUseCase`

- [ ] **Step 4: 구현**

`domain/src/main/java/com/sypark/flightdeal/domain/usecase/DetectPriceChangesUseCase.kt`:

```kotlin
package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.PriceChange
import com.sypark.flightdeal.domain.model.Direction
import com.sypark.flightdeal.domain.model.PriceSnapshot
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.model.Won
import javax.inject.Inject

/**
 * 직전 스냅샷과 새 가격을 비교해 알릴 변동을 판정한다.
 * 워커의 알림 로직 전부가 여기 있다.
 *
 * @return 알릴 변동이 없으면 null.
 */
class DetectPriceChangesUseCase @Inject constructor() {

    operator fun invoke(
        tracked: TrackedRoute,
        previous: PriceSnapshot?,
        current: Won,
    ): PriceChange? {
        // 비교 대상이 없으면 "변동"이라는 개념 자체가 성립하지 않는다.
        if (previous == null) return null
        if (previous.price == current) return null

        return PriceChange(
            trackedRouteId = tracked.id,
            previous = previous.price,
            current = current,
            direction = if (current < previous.price) Direction.DOWN else Direction.UP,
            // 방향과 무관하게 판정한다. 올랐어도 목표가 이하면 살 만한 가격이다.
            reachedTarget = tracked.targetPrice?.let { current <= it } ?: false,
        )
    }
}
```

`PriceChange`와 `Direction`은 `PriceSnapshot.kt`에 함께 둔다. 같이 변한다.

`domain/src/main/java/com/sypark/flightdeal/domain/model/PriceSnapshot.kt`에 추가:

```kotlin
enum class Direction { DOWN, UP }

data class PriceChange(
    val trackedRouteId: Long,
    val previous: Won,
    val current: Won,
    val direction: Direction,
    val reachedTarget: Boolean,
)
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
./gradlew :domain:test
```

기대: PASS (총 23건)

- [ ] **Step 6: 커밋**

```bash
git add domain
git commit -m "feat: 가격 변동 감지 로직 추가"
```

---

## Task 5: Repository 인터페이스와 Fake 구현체

**Files:**
- Create: `domain/src/main/java/com/sypark/flightdeal/domain/repository/FlightPriceRepository.kt`
- Create: `data/src/main/java/com/sypark/flightdeal/data/fake/FakeDealFixtures.kt`
- Create: `data/src/main/java/com/sypark/flightdeal/data/fake/FakeFlightPriceRepository.kt`
- Create: `data/src/main/java/com/sypark/flightdeal/data/di/RepositoryModule.kt`
- Test: `data/src/test/java/com/sypark/flightdeal/data/fake/FakeFlightPriceRepositoryTest.kt`

**Interfaces:**
- Consumes: `AppResult`, `PriceQuote`, `PriceStats`, `Airport`, `Route` (Task 3)
- Produces:
  - `FlightPriceRepository` — `suspend cheapestDeals(origin: Airport, limit: Int): AppResult<List<PriceQuote>>`, `suspend calendarPrices(route: Route, month: YearMonth): AppResult<List<PriceQuote>>`, `suspend priceStats(route: Route, month: YearMonth): AppResult<PriceStats>`
  - `FakeFlightPriceRepository(private val behavior: Behavior = Behavior.Normal)` — `Behavior`는 `Normal`, `EmptyData`, `Failing`
  - `FakeDealFixtures.INCHEON`, `FakeDealFixtures.deals(): List<PriceQuote>`

**spec에서 벗어난 부분 — 의도적이다.** spec §6.1은 Fake가 "번들된 로컬 JSON"을 읽는다고
썼지만, 여기서는 Kotlin 코드에 인메모리로 둔다. Fake의 JSON은 Travelpayouts 스키마도
아니어서 파싱 경로를 검증해주지 못하는데, 대신 `Context`와 assets I/O를 끌어들여
테스트에 Robolectric이 필요해진다. 얻는 것 없이 비용만 든다. 실제 스키마 검증은
Task 1이 저장한 픽스처로 다음 계획서에서 한다.

- [ ] **Step 1: Repository 인터페이스 작성**

`domain/src/main/java/com/sypark/flightdeal/domain/repository/FlightPriceRepository.kt`:

```kotlin
package com.sypark.flightdeal.domain.repository

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.PriceStats
import com.sypark.flightdeal.domain.model.Route
import java.time.YearMonth

/**
 * 가격 조회. 구현체가 어디서 데이터를 가져오는지 도메인은 알지 않는다.
 */
interface FlightPriceRepository {

    /** 출발지 기준 특가 목록. 홈 피드용. */
    suspend fun cheapestDeals(origin: Airport, limit: Int): AppResult<List<PriceQuote>>

    /** 한 노선·한 달의 날짜별 가격. */
    suspend fun calendarPrices(route: Route, month: YearMonth): AppResult<List<PriceQuote>>

    /** 한 노선·한 달의 가격 분포. 할인율 배지의 기준. */
    suspend fun priceStats(route: Route, month: YearMonth): AppResult<PriceStats>
}
```

- [ ] **Step 2: Fake 테스트 작성**

`data/src/test/java/com/sypark/flightdeal/data/fake/FakeFlightPriceRepositoryTest.kt`:

```kotlin
package com.sypark.flightdeal.data.fake

import com.sypark.flightdeal.domain.model.AppResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeFlightPriceRepositoryTest {

    @Test
    fun `기본 동작은 특가 목록을 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository()

        val result = repo.cheapestDeals(FakeDealFixtures.INCHEON, limit = 10)

        assertTrue(result is AppResult.Success)
        assertTrue((result as AppResult.Success).data.isNotEmpty())
    }

    @Test
    fun `limit보다 많이 돌려주지 않는다`() = runTest {
        val repo = FakeFlightPriceRepository()

        val result = repo.cheapestDeals(FakeDealFixtures.INCHEON, limit = 2)

        assertEquals(2, (result as AppResult.Success).data.size)
    }

    @Test
    fun `EmptyData 모드는 Empty를 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository(FakeFlightPriceRepository.Behavior.EmptyData)

        val result = repo.cheapestDeals(FakeDealFixtures.INCHEON, limit = 10)

        assertEquals(AppResult.Empty, result)
    }

    @Test
    fun `Failing 모드는 NetworkError를 돌려준다`() = runTest {
        val repo = FakeFlightPriceRepository(FakeFlightPriceRepository.Behavior.Failing)

        val result = repo.cheapestDeals(FakeDealFixtures.INCHEON, limit = 10)

        assertTrue(result is AppResult.NetworkError)
    }
}
```

`Behavior`를 두는 이유: Task 7의 ViewModel 테스트에서 빈 상태와 오류 상태를
결정론적으로 재현해야 한다. 이게 없으면 그 상태들은 손으로 확인할 수밖에 없다.

- [ ] **Step 3: 테스트 실패 확인**

```bash
./gradlew :data:test
```

기대: 컴파일 실패. `Unresolved reference: FakeFlightPriceRepository`

- [ ] **Step 4: 픽스처 작성**

`data/src/main/java/com/sypark/flightdeal/data/fake/FakeDealFixtures.kt`:

```kotlin
package com.sypark.flightdeal.data.fake

import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.Won
import java.time.Instant
import java.time.LocalDate

/**
 * 개발·테스트용 고정 데이터. 실제 API 연동 전까지 화면을 채운다.
 */
object FakeDealFixtures {

    val INCHEON = Airport("ICN", "서울", "대한민국")

    private val DESTINATIONS = listOf(
        Airport("TYO", "도쿄", "일본") to 189_000,
        Airport("BKK", "방콕", "태국") to 241_000,
        Airport("DAD", "다낭", "베트남") to 265_000,
        Airport("TPE", "타이베이", "대만") to 278_000,
        Airport("HKG", "홍콩", "중국") to 312_000,
        Airport("SIN", "싱가포르", "싱가포르") to 398_000,
    )

    private val AIRLINES = listOf("대한항공", "아시아나항공", "티웨이항공", "제주항공")

    fun deals(): List<PriceQuote> = DESTINATIONS.mapIndexed { index, (destination, price) ->
        PriceQuote(
            route = Route(INCHEON, destination),
            departDate = LocalDate.of(2026, 10, 12).plusDays(index.toLong()),
            returnDate = LocalDate.of(2026, 10, 16).plusDays(index.toLong()),
            price = Won(price),
            airline = AIRLINES[index % AIRLINES.size],
            foundAt = Instant.parse("2026-08-28T00:00:00Z"),
            deepLink = "https://example.com/booking/${destination.iata}",
        )
    }

    /** 한 노선의 한 달치 가격. 중앙값이 특가보다 확실히 높도록 구성한다. */
    fun monthlyPrices(route: Route): List<PriceQuote> {
        val base = DESTINATIONS.firstOrNull { it.first.iata == route.destination.iata }?.second
            ?: return emptyList()
        return (1..28).map { day ->
            PriceQuote(
                route = route,
                departDate = LocalDate.of(2026, 10, day),
                returnDate = LocalDate.of(2026, 10, day).plusDays(4),
                // 특가(base)의 1.2배 ~ 1.9배 사이에서 흔들리게 만든다.
                price = Won(base * (120 + (day * 27) % 70) / 100),
                airline = AIRLINES[day % AIRLINES.size],
                foundAt = Instant.parse("2026-08-28T00:00:00Z"),
                deepLink = "https://example.com/booking/${route.destination.iata}/$day",
            )
        }
    }
}
```

- [ ] **Step 5: Fake 구현체 작성**

`data/src/main/java/com/sypark/flightdeal/data/fake/FakeFlightPriceRepository.kt`:

```kotlin
package com.sypark.flightdeal.data.fake

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.PriceStats
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.repository.FlightPriceRepository
import kotlinx.coroutines.delay
import java.io.IOException
import java.time.YearMonth

/**
 * 개발·테스트용 구현체. 네트워크 없이 결정론적으로 동작한다.
 *
 * @param behavior 빈 상태와 오류 상태를 재현하기 위한 스위치.
 */
class FakeFlightPriceRepository(
    private val behavior: Behavior = Behavior.Normal,
) : FlightPriceRepository {

    enum class Behavior { Normal, EmptyData, Failing }

    override suspend fun cheapestDeals(origin: Airport, limit: Int): AppResult<List<PriceQuote>> =
        respond { FakeDealFixtures.deals().take(limit) }

    override suspend fun calendarPrices(route: Route, month: YearMonth): AppResult<List<PriceQuote>> =
        respond { FakeDealFixtures.monthlyPrices(route) }

    override suspend fun priceStats(route: Route, month: YearMonth): AppResult<PriceStats> {
        val prices = FakeDealFixtures.monthlyPrices(route).map { it.price }
        return when (behavior) {
            Behavior.Failing -> AppResult.NetworkError(IOException("fake network failure"))
            Behavior.EmptyData -> AppResult.Empty
            Behavior.Normal -> PriceStats.from(prices)
                ?.let { AppResult.Success(it) }
                ?: AppResult.Empty
        }
    }

    private suspend fun <T> respond(block: () -> List<T>): AppResult<List<T>> {
        delay(NETWORK_DELAY_MS)
        return when (behavior) {
            Behavior.Failing -> AppResult.NetworkError(IOException("fake network failure"))
            Behavior.EmptyData -> AppResult.Empty
            Behavior.Normal -> block().let { if (it.isEmpty()) AppResult.Empty else AppResult.Success(it) }
        }
    }

    private companion object {
        /** 로딩 상태가 실제로 보이도록 약간의 지연을 준다. runTest에서는 즉시 건너뛴다. */
        const val NETWORK_DELAY_MS = 400L
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

```bash
./gradlew :data:test
```

기대: PASS (4건)

- [ ] **Step 7: Hilt 바인딩 작성**

`data/src/main/java/com/sypark/flightdeal/data/di/RepositoryModule.kt`:

```kotlin
package com.sypark.flightdeal.data.di

import com.sypark.flightdeal.data.fake.FakeFlightPriceRepository
import com.sypark.flightdeal.domain.repository.FlightPriceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 지금은 Fake만 제공한다. Travelpayouts 구현체가 생기면
 * 이 함수의 반환값만 교체한다. 다른 어떤 파일도 손대지 않는다.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideFlightPriceRepository(): FlightPriceRepository =
        FakeFlightPriceRepository()
}
```

- [ ] **Step 8: 빌드 확인**

```bash
./gradlew :domain:test :data:test :presentation:assembleDebug
```

기대: `BUILD SUCCESSFUL`

- [ ] **Step 9: 커밋**

```bash
git add domain data
git commit -m "feat: 가격 조회 Repository 인터페이스와 Fake 구현체 추가"
```

---

## Task 6: 특가 피드 UseCase

**Files:**
- Create: `domain/src/main/java/com/sypark/flightdeal/domain/model/DealItem.kt`
- Create: `domain/src/main/java/com/sypark/flightdeal/domain/usecase/GetDealFeedUseCase.kt`
- Test: `domain/src/test/java/com/sypark/flightdeal/domain/usecase/GetDealFeedUseCaseTest.kt`

**Interfaces:**
- Consumes: `FlightPriceRepository` (Task 5), `CalculateDiscountUseCase` (Task 3)
- Produces:
  - `DealItem(quote: PriceQuote, discountPercent: Int?, originalPrice: Won?)`
  - `GetDealFeedUseCase.invoke(origin: Airport, limit: Int = 20): AppResult<List<DealItem>>`

`DealItem`이 화면이 실제로 그리는 단위다. `PriceQuote`는 가격만 알지 "이게 싼 건지"를
모른다. 그 판단을 붙여주는 게 이 UseCase의 일이다.

- [ ] **Step 1: 테스트 작성**

`domain/src/test/java/com/sypark/flightdeal/domain/usecase/GetDealFeedUseCaseTest.kt`:

```kotlin
package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.PriceStats
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.FlightPriceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class GetDealFeedUseCaseTest {

    private val incheon = Airport("ICN", "서울", "대한민국")
    private val tokyo = Airport("TYO", "도쿄", "일본")
    private val route = Route(incheon, tokyo)

    private fun quote(price: Int) = PriceQuote(
        route = route,
        departDate = LocalDate.of(2026, 10, 12),
        returnDate = LocalDate.of(2026, 10, 16),
        price = Won(price),
        airline = "대한항공",
        foundAt = Instant.EPOCH,
        deepLink = null,
    )

    /** 테스트마다 응답을 지정할 수 있는 최소 스텁. */
    private class StubRepository(
        val deals: AppResult<List<PriceQuote>>,
        val stats: AppResult<PriceStats>,
    ) : FlightPriceRepository {
        override suspend fun cheapestDeals(origin: Airport, limit: Int) = deals
        override suspend fun calendarPrices(route: Route, month: YearMonth) =
            AppResult.Success(emptyList<PriceQuote>())
        override suspend fun priceStats(route: Route, month: YearMonth) = stats
    }

    @Test
    fun `할인율을 계산해 붙인다`() = runTest {
        val repo = StubRepository(
            deals = AppResult.Success(listOf(quote(189_000))),
            stats = AppResult.Success(PriceStats(Won(305_000), Won(180_000), Won(400_000), 20)),
        )
        val useCase = GetDealFeedUseCase(repo, CalculateDiscountUseCase())

        val result = useCase(incheon) as AppResult.Success

        assertEquals(1, result.data.size)
        assertEquals(38, result.data.first().discountPercent)
        assertEquals(Won(305_000), result.data.first().originalPrice)
    }

    @Test
    fun `분포를 못 구하면 할인율 없이 가격만 보여준다`() = runTest {
        val repo = StubRepository(
            deals = AppResult.Success(listOf(quote(189_000))),
            stats = AppResult.Empty,
        )
        val useCase = GetDealFeedUseCase(repo, CalculateDiscountUseCase())

        val result = useCase(incheon) as AppResult.Success

        assertEquals(1, result.data.size)
        assertNull(result.data.first().discountPercent)
        assertNull(result.data.first().originalPrice)
    }

    @Test
    fun `빈 응답은 Empty로 전달한다`() = runTest {
        val repo = StubRepository(deals = AppResult.Empty, stats = AppResult.Empty)
        val useCase = GetDealFeedUseCase(repo, CalculateDiscountUseCase())

        assertEquals(AppResult.Empty, useCase(incheon))
    }

    @Test
    fun `네트워크 오류는 그대로 전달한다`() = runTest {
        val repo = StubRepository(
            deals = AppResult.NetworkError(IOException("boom")),
            stats = AppResult.Empty,
        )
        val useCase = GetDealFeedUseCase(repo, CalculateDiscountUseCase())

        assertTrue(useCase(incheon) is AppResult.NetworkError)
    }
}
```

두 번째 테스트가 중요하다. 분포 조회가 실패해도 **피드는 그대로 보여야 한다.**
배지 하나 없다고 화면 전체를 오류로 만들면 안 된다.

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :domain:test --tests "*GetDealFeedUseCaseTest*"
```

기대: 컴파일 실패. `Unresolved reference: GetDealFeedUseCase`

- [ ] **Step 3: `DealItem` 작성**

`domain/src/main/java/com/sypark/flightdeal/domain/model/DealItem.kt`:

```kotlin
package com.sypark.flightdeal.domain.model

/**
 * 화면이 그리는 딜 하나. 가격에 "이게 싼 건지"의 판단이 붙어 있다.
 *
 * @param discountPercent 배지에 표시할 할인율. 배지를 달 가치가 없으면 null.
 * @param originalPrice 취소선으로 보여줄 기준가(중앙값). [discountPercent]가 null이면 함께 null.
 */
data class DealItem(
    val quote: PriceQuote,
    val discountPercent: Int?,
    val originalPrice: Won?,
)
```

- [ ] **Step 4: `GetDealFeedUseCase` 구현**

`domain/src/main/java/com/sypark/flightdeal/domain/usecase/GetDealFeedUseCase.kt`:

```kotlin
package com.sypark.flightdeal.domain.usecase

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.DealItem
import com.sypark.flightdeal.domain.repository.FlightPriceRepository
import java.time.YearMonth
import javax.inject.Inject

class GetDealFeedUseCase @Inject constructor(
    private val repository: FlightPriceRepository,
    private val calculateDiscount: CalculateDiscountUseCase,
) {

    suspend operator fun invoke(origin: Airport, limit: Int = DEFAULT_LIMIT): AppResult<List<DealItem>> {
        return when (val deals = repository.cheapestDeals(origin, limit)) {
            is AppResult.Success -> AppResult.Success(deals.data.map { quote ->
                // 분포 조회가 실패해도 딜 자체는 보여준다. 배지만 빠진다.
                val stats = (repository.priceStats(quote.route, YearMonth.from(quote.departDate))
                    as? AppResult.Success)?.data
                val discount = stats?.let { calculateDiscount(quote.price, it) }

                DealItem(
                    quote = quote,
                    discountPercent = discount,
                    originalPrice = if (discount != null) stats.median else null,
                )
            })
            AppResult.Empty -> AppResult.Empty
            is AppResult.NetworkError -> deals
            is AppResult.Unknown -> deals
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 20
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
./gradlew :domain:test
```

기대: PASS (총 27건)

- [ ] **Step 6: 커밋**

```bash
git add domain
git commit -m "feat: 특가 피드 조회 UseCase 추가"
```

---

## Task 7: 특가 피드 ViewModel

**Files:**
- Create: `presentation/src/main/java/com/sypark/flightdeal/feed/DealFeedUiState.kt`
- Create: `presentation/src/main/java/com/sypark/flightdeal/feed/DealFeedViewModel.kt`
- Test: `presentation/src/test/java/com/sypark/flightdeal/feed/DealFeedViewModelTest.kt`

**Interfaces:**
- Consumes: `GetDealFeedUseCase` (Task 6), `FakeDealFixtures.INCHEON` (Task 5)
- Produces:
  - `DealFeedUiState` — `Loading`, `Success(deals: List<DealItem>)`, `Empty`, `Error(retryable: Boolean)`
  - `DealFeedViewModel.uiState: StateFlow<DealFeedUiState>`, `DealFeedViewModel.refresh()`

- [ ] **Step 1: 테스트 작성**

`presentation/src/test/java/com/sypark/flightdeal/feed/DealFeedViewModelTest.kt`:

```kotlin
package com.sypark.flightdeal.feed

import app.cash.turbine.test
import com.sypark.flightdeal.data.fake.FakeFlightPriceRepository
import com.sypark.flightdeal.domain.usecase.CalculateDiscountUseCase
import com.sypark.flightdeal.domain.usecase.GetDealFeedUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DealFeedViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(behavior: FakeFlightPriceRepository.Behavior) =
        DealFeedViewModel(
            GetDealFeedUseCase(FakeFlightPriceRepository(behavior), CalculateDiscountUseCase())
        )

    @Test
    fun `로딩으로 시작해 성공으로 끝난다`() = runTest {
        viewModel(FakeFlightPriceRepository.Behavior.Normal).uiState.test {
            assertEquals(DealFeedUiState.Loading, awaitItem())

            val loaded = awaitItem()
            assertTrue(loaded is DealFeedUiState.Success)
            assertTrue((loaded as DealFeedUiState.Success).deals.isNotEmpty())
        }
    }

    @Test
    fun `빈 데이터는 Empty 상태가 된다`() = runTest {
        viewModel(FakeFlightPriceRepository.Behavior.EmptyData).uiState.test {
            assertEquals(DealFeedUiState.Loading, awaitItem())
            assertEquals(DealFeedUiState.Empty, awaitItem())
        }
    }

    @Test
    fun `네트워크 오류는 재시도 가능한 Error 상태가 된다`() = runTest {
        viewModel(FakeFlightPriceRepository.Behavior.Failing).uiState.test {
            assertEquals(DealFeedUiState.Loading, awaitItem())

            val error = awaitItem()
            assertTrue(error is DealFeedUiState.Error)
            assertTrue((error as DealFeedUiState.Error).retryable)
        }
    }

    @Test
    fun `할인 배지가 붙은 딜이 하나 이상 있다`() = runTest {
        viewModel(FakeFlightPriceRepository.Behavior.Normal).uiState.test {
            awaitItem() // Loading
            val loaded = awaitItem() as DealFeedUiState.Success

            assertTrue(loaded.deals.any { it.discountPercent != null })
        }
    }
}
```

마지막 테스트가 `FakeDealFixtures.monthlyPrices`의 배수 설계(특가의 1.2~1.9배)가
실제로 할인 배지를 만들어내는지 검증한다. 픽스처를 잘못 만들면 화면에 배지가
하나도 안 뜨는데, 그걸 손으로 확인하다 놓치기 쉽다.

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :presentation:testDebugUnitTest --tests "*DealFeedViewModelTest*"
```

기대: 컴파일 실패. `Unresolved reference: DealFeedViewModel`

- [ ] **Step 3: `DealFeedUiState` 작성**

`presentation/src/main/java/com/sypark/flightdeal/feed/DealFeedUiState.kt`:

```kotlin
package com.sypark.flightdeal.feed

import com.sypark.flightdeal.domain.model.DealItem

/**
 * 네 가지 상태를 명시적으로 구분한다. [Empty]는 오류가 아니다.
 */
sealed interface DealFeedUiState {
    data object Loading : DealFeedUiState
    data class Success(val deals: List<DealItem>) : DealFeedUiState
    data object Empty : DealFeedUiState
    data class Error(val retryable: Boolean) : DealFeedUiState
}
```

- [ ] **Step 4: `DealFeedViewModel` 작성**

`presentation/src/main/java/com/sypark/flightdeal/feed/DealFeedViewModel.kt`:

```kotlin
package com.sypark.flightdeal.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sypark.flightdeal.data.fake.FakeDealFixtures
import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.usecase.GetDealFeedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DealFeedViewModel @Inject constructor(
    private val getDealFeed: GetDealFeedUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DealFeedUiState>(DealFeedUiState.Loading)
    val uiState: StateFlow<DealFeedUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = DealFeedUiState.Loading

            // 기본 출발지는 인천 고정. 설정 화면이 생기면 DataStore에서 읽는다.
            _uiState.value = when (val result = getDealFeed(FakeDealFixtures.INCHEON)) {
                is AppResult.Success -> DealFeedUiState.Success(result.data)
                AppResult.Empty -> DealFeedUiState.Empty
                is AppResult.NetworkError -> DealFeedUiState.Error(retryable = true)
                is AppResult.Unknown -> DealFeedUiState.Error(retryable = false)
            }
        }
    }
}
```

`FakeDealFixtures.INCHEON`을 `:presentation`이 직접 참조하는 것은 임시다.
설정 화면이 생기면 DataStore에서 읽어오고, 이 import는 사라진다.

- [ ] **Step 5: 테스트 통과 확인**

```bash
./gradlew :presentation:testDebugUnitTest
```

기대: PASS (4건)

- [ ] **Step 6: 커밋**

```bash
git add presentation
git commit -m "feat: 특가 피드 ViewModel과 화면 상태 정의 추가"
```

---

## Task 8: 인디고 테마와 특가 피드 화면

**Files:**
- Create: `presentation/src/main/res/values/colors.xml`
- Create: `presentation/src/main/res/values/themes.xml`
- Create: `presentation/src/main/res/values/dimens.xml`
- Create: `presentation/src/main/res/drawable/bg_discount_badge.xml`
- Create: `presentation/src/main/res/drawable/bg_card.xml`
- Create: `presentation/src/main/res/menu/bottom_nav_menu.xml`
- Create: `presentation/src/main/res/navigation/nav_graph.xml`
- Create: `presentation/src/main/res/layout/activity_main.xml`
- Create: `presentation/src/main/res/layout/fragment_deal_feed.xml`
- Create: `presentation/src/main/res/layout/item_deal.xml`
- Create: `presentation/src/main/java/com/sypark/flightdeal/MainActivity.kt`
- Create: `presentation/src/main/java/com/sypark/flightdeal/feed/DealFeedFragment.kt`
- Create: `presentation/src/main/java/com/sypark/flightdeal/feed/DealAdapter.kt`
- Create: `presentation/src/main/java/com/sypark/flightdeal/feed/DealBindingAdapters.kt`
- Create: 나머지 3개 탭의 자리표시 Fragment
- Modify: `presentation/src/main/AndroidManifest.xml`
- Modify: `presentation/src/main/res/values/strings.xml`
- Test: `presentation/src/test/java/com/sypark/flightdeal/feed/DealDiffCallbackTest.kt`

**Interfaces:**
- Consumes: `DealFeedViewModel`, `DealFeedUiState` (Task 7), `DealItem` (Task 6)
- Produces: 실행 가능한 앱. 4탭 BottomNav, 특가 피드 화면.

- [ ] **Step 1: 색상 리소스 작성**

`presentation/src/main/res/values/colors.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- 브랜드 -->
    <color name="indigo">#4338E0</color>
    <color name="indigo_subtle">#EDEBFF</color>

    <!-- 표면 -->
    <color name="background">#FFFFFF</color>
    <color name="surface">#F4F5F9</color>
    <color name="outline">#EAECF3</color>

    <!-- 텍스트 -->
    <color name="text_primary">#0F1115</color>
    <color name="text_secondary">#8A8FA3</color>

    <!-- 가격 방향. 브랜드 색과 절대 섞지 않는다. -->
    <color name="price_down">#0E9E6E</color>
    <color name="price_up">#D93A3A</color>

    <color name="white">#FFFFFF</color>
</resources>
```

- [ ] **Step 2: 테마와 치수 작성**

`presentation/src/main/res/values/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.FlightDeal" parent="Theme.Material3.Light.NoActionBar">
        <item name="colorPrimary">@color/indigo</item>
        <item name="colorOnPrimary">@color/white</item>
        <item name="colorSurface">@color/background</item>
        <item name="android:colorBackground">@color/background</item>
        <item name="android:statusBarColor">@color/background</item>
        <item name="android:windowLightStatusBar" tools:targetApi="m">true</item>
    </style>
</resources>
```

`presentation/src/main/res/values/dimens.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <dimen name="screen_margin">16dp</dimen>
    <dimen name="card_radius">16dp</dimen>
    <dimen name="card_gap">10dp</dimen>
</resources>
```

- [ ] **Step 3: drawable 작성**

`presentation/src/main/res/drawable/bg_discount_badge.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/indigo_subtle" />
    <corners android:radius="6dp" />
</shape>
```

`presentation/src/main/res/drawable/bg_card.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/white" />
    <stroke android:width="1dp" android:color="@color/outline" />
    <corners android:radius="@dimen/card_radius" />
</shape>
```

- [ ] **Step 4: 문자열 리소스 보강**

`presentation/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">flightdeal</string>

    <string name="tab_deals">특가</string>
    <string name="tab_tracking">추적</string>
    <string name="tab_search">검색</string>
    <string name="tab_profile">내정보</string>

    <string name="feed_title">오늘의 특가</string>
    <string name="feed_search_hint">어디로 떠나세요?</string>

    <string name="feed_empty_title">아직 특가가 없어요</string>
    <string name="feed_empty_body">가격 데이터가 모이면 여기에 보여드릴게요.</string>

    <string name="feed_error_title">가격을 불러오지 못했어요</string>
    <string name="feed_error_body">네트워크를 확인하고 다시 시도해주세요.</string>
    <string name="retry">다시 시도</string>

    <string name="discount_badge">평균가 %d%%</string>
    <string name="price_won">%s원</string>
    <string name="coming_soon">준비 중입니다</string>
</resources>
```

`discount_badge`는 `평균가 −38%`처럼 보여야 하므로, 바인딩 어댑터에서 음수 부호를
직접 붙인다(Step 8).

- [ ] **Step 5: 자리표시 Fragment 3개 작성**

`presentation/src/main/res/layout/fragment_placeholder.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:text="@string/coming_soon"
        android:textColor="@color/text_secondary"
        android:textSize="14sp" />
</FrameLayout>
```

`presentation/src/main/java/com/sypark/flightdeal/placeholder/PlaceholderFragments.kt`:

```kotlin
package com.sypark.flightdeal.placeholder

import androidx.fragment.app.Fragment
import com.sypark.flightdeal.R

/**
 * 하단 탭 4개를 먼저 세워두기 위한 자리표시. 각 화면이 구현되면 교체한다.
 */
class TrackingFragment : Fragment(R.layout.fragment_placeholder)
class SearchFragment : Fragment(R.layout.fragment_placeholder)
class ProfileFragment : Fragment(R.layout.fragment_placeholder)
```

- [ ] **Step 6: 하단 탭과 내비게이션 그래프 작성**

`presentation/src/main/res/menu/bottom_nav_menu.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@+id/dealFeedFragment" android:title="@string/tab_deals" />
    <item android:id="@+id/trackingFragment" android:title="@string/tab_tracking" />
    <item android:id="@+id/searchFragment" android:title="@string/tab_search" />
    <item android:id="@+id/profileFragment" android:title="@string/tab_profile" />
</menu>
```

아이콘은 아직 없다. Material3 BottomNavigationView는 아이콘 없이도 라벨만으로 렌더링된다.
아이콘 추가는 이 계획서 범위 밖이다.

`presentation/src/main/res/navigation/nav_graph.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_graph"
    app:startDestination="@id/dealFeedFragment">

    <fragment
        android:id="@+id/dealFeedFragment"
        android:name="com.sypark.flightdeal.feed.DealFeedFragment"
        android:label="@string/tab_deals" />

    <fragment
        android:id="@+id/trackingFragment"
        android:name="com.sypark.flightdeal.placeholder.TrackingFragment"
        android:label="@string/tab_tracking" />

    <fragment
        android:id="@+id/searchFragment"
        android:name="com.sypark.flightdeal.placeholder.SearchFragment"
        android:label="@string/tab_search" />

    <fragment
        android:id="@+id/profileFragment"
        android:name="com.sypark.flightdeal.placeholder.ProfileFragment"
        android:label="@string/tab_profile" />
</navigation>
```

- [ ] **Step 7: MainActivity와 레이아웃 작성**

`presentation/src/main/res/layout/activity_main.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background">

    <androidx.fragment.app.FragmentContainerView
        android:id="@+id/navHost"
        android:name="androidx.navigation.fragment.NavHostFragment"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:defaultNavHost="true"
        app:navGraph="@navigation/nav_graph"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toTopOf="@id/bottomNav"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottomNav"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:background="@color/white"
        app:labelVisibilityMode="labeled"
        app:itemTextColor="@color/indigo"
        app:menu="@menu/bottom_nav_menu"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

`presentation/src/main/java/com/sypark/flightdeal/MainActivity.kt`:

```kotlin
package com.sypark.flightdeal

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.sypark.flightdeal.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navController = (supportFragmentManager
            .findFragmentById(R.id.navHost) as NavHostFragment).navController
        binding.bottomNav.setupWithNavController(navController)
    }
}
```

`AndroidManifest.xml`을 수정해 `MainActivity`를 등록하고, Task 2 Step 9에서
임시로 넣었던 테마를 되돌린다.

```xml
    <application
        android:name=".FlightDealApp"
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.FlightDeal">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.FlightDeal">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
```

- [ ] **Step 8: 바인딩 어댑터 작성**

`presentation/src/main/java/com/sypark/flightdeal/feed/DealBindingAdapters.kt`:

```kotlin
package com.sypark.flightdeal.feed

import android.graphics.Paint
import android.view.View
import android.widget.TextView
import androidx.databinding.BindingAdapter
import com.sypark.flightdeal.R
import com.sypark.flightdeal.domain.model.Won
import java.text.NumberFormat
import java.util.Locale

private val KRW: NumberFormat = NumberFormat.getIntegerInstance(Locale.KOREA)

@BindingAdapter("wonPrice")
fun TextView.setWonPrice(won: Won?) {
    text = won?.let { context.getString(R.string.price_won, KRW.format(it.amount)) }.orEmpty()
}

/** 취소선 기준가. 값이 없으면 뷰 자체를 감춘다. */
@BindingAdapter("strikethroughPrice")
fun TextView.setStrikethroughPrice(won: Won?) {
    if (won == null) {
        visibility = View.GONE
        return
    }
    visibility = View.VISIBLE
    paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
    text = context.getString(R.string.price_won, KRW.format(won.amount))
}

/** 할인 배지. 배지를 달 가치가 없으면 감춘다. */
@BindingAdapter("discountPercent")
fun TextView.setDiscountPercent(percent: Int?) {
    if (percent == null) {
        visibility = View.GONE
        return
    }
    visibility = View.VISIBLE
    // 화면에는 "평균가 −38%"로 보인다. 음수 부호는 U+2212.
    text = context.getString(R.string.discount_badge, -percent)
}

@BindingAdapter("isVisible")
fun View.setIsVisible(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}
```

- [ ] **Step 9: 딜 카드 레이아웃 작성**

`presentation/src/main/res/layout/item_deal.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<layout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools">

    <data>
        <variable name="item" type="com.sypark.flightdeal.domain.model.DealItem" />
    </data>

    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginBottom="@dimen/card_gap"
        android:background="@drawable/bg_card"
        android:paddingHorizontal="14dp"
        android:paddingVertical="13dp">

        <TextView
            android:id="@+id/city"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:text="@{item.quote.route.destination.cityKo}"
            android:textColor="@color/text_primary"
            android:textSize="15sp"
            android:textStyle="bold"
            app:layout_constraintTop_toTopOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toEndOf="parent"
            tools:text="도쿄" />

        <TextView
            android:id="@+id/badge"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:background="@drawable/bg_discount_badge"
            android:paddingHorizontal="7dp"
            android:paddingVertical="3dp"
            android:textColor="@color/indigo"
            android:textSize="11sp"
            android:textStyle="bold"
            app:discountPercent="@{item.discountPercent}"
            app:layout_constraintTop_toBottomOf="@id/city"
            app:layout_constraintStart_toStartOf="parent"
            tools:text="평균가 −38%" />

        <TextView
            android:id="@+id/airline"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="7dp"
            android:text="@{item.quote.airline}"
            android:textColor="@color/text_secondary"
            android:textSize="11sp"
            app:layout_constraintTop_toTopOf="@id/badge"
            app:layout_constraintBottom_toBottomOf="@id/badge"
            app:layout_constraintStart_toEndOf="@id/badge"
            tools:text="대한항공" />

        <TextView
            android:id="@+id/price"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="9dp"
            android:textColor="@color/text_primary"
            android:textSize="21sp"
            android:textStyle="bold"
            app:wonPrice="@{item.quote.price}"
            app:layout_constraintTop_toBottomOf="@id/badge"
            app:layout_constraintStart_toStartOf="parent"
            tools:text="189,000원" />

        <TextView
            android:id="@+id/originalPrice"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            android:textColor="@color/text_secondary"
            android:textSize="12sp"
            app:strikethroughPrice="@{item.originalPrice}"
            app:layout_constraintBottom_toBottomOf="@id/price"
            app:layout_constraintStart_toEndOf="@id/price"
            tools:text="305,000원" />

    </androidx.constraintlayout.widget.ConstraintLayout>
</layout>
```

- [ ] **Step 10: 어댑터 작성과 DiffUtil 테스트**

`presentation/src/test/java/com/sypark/flightdeal/feed/DealDiffCallbackTest.kt`:

```kotlin
package com.sypark.flightdeal.feed

import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.DealItem
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.Won
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DealDiffCallbackTest {

    private fun item(destIata: String, price: Int) = DealItem(
        quote = PriceQuote(
            route = Route(Airport("ICN", "서울", "대한민국"), Airport(destIata, "도쿄", "일본")),
            departDate = LocalDate.of(2026, 10, 12),
            returnDate = null,
            price = Won(price),
            airline = "대한항공",
            foundAt = Instant.EPOCH,
            deepLink = null,
        ),
        discountPercent = null,
        originalPrice = null,
    )

    @Test
    fun `노선과 출발일이 같으면 같은 항목이다`() {
        assertTrue(DealDiffCallback.areItemsTheSame(item("TYO", 189_000), item("TYO", 215_000)))
    }

    @Test
    fun `목적지가 다르면 다른 항목이다`() {
        assertFalse(DealDiffCallback.areItemsTheSame(item("TYO", 189_000), item("BKK", 189_000)))
    }

    @Test
    fun `가격이 바뀌면 내용이 다르다`() {
        assertFalse(DealDiffCallback.areContentsTheSame(item("TYO", 189_000), item("TYO", 215_000)))
    }
}
```

`presentation/src/main/java/com/sypark/flightdeal/feed/DealAdapter.kt`:

```kotlin
package com.sypark.flightdeal.feed

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sypark.flightdeal.databinding.ItemDealBinding
import com.sypark.flightdeal.domain.model.DealItem

/**
 * 테스트에서 직접 호출할 수 있도록 object로 분리한다.
 */
object DealDiffCallback : DiffUtil.ItemCallback<DealItem>() {

    override fun areItemsTheSame(oldItem: DealItem, newItem: DealItem): Boolean =
        oldItem.quote.route == newItem.quote.route &&
            oldItem.quote.departDate == newItem.quote.departDate

    override fun areContentsTheSame(oldItem: DealItem, newItem: DealItem): Boolean =
        oldItem == newItem
}

class DealAdapter(
    private val onClick: (DealItem) -> Unit,
) : ListAdapter<DealItem, DealAdapter.ViewHolder>(DealDiffCallback) {

    class ViewHolder(val binding: ItemDealBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemDealBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.item = item
        holder.binding.root.setOnClickListener { onClick(item) }
        holder.binding.executePendingBindings()
    }
}
```

- [ ] **Step 11: 피드 화면 레이아웃 작성**

`presentation/src/main/res/layout/fragment_deal_feed.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<layout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools">

    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@color/background">

        <TextView
            android:id="@+id/title"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_margin="@dimen/screen_margin"
            android:text="@string/feed_title"
            android:textColor="@color/text_primary"
            android:textSize="20sp"
            android:textStyle="bold"
            app:layout_constraintTop_toTopOf="parent"
            app:layout_constraintStart_toStartOf="parent" />

        <TextView
            android:id="@+id/searchBar"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginHorizontal="@dimen/screen_margin"
            android:background="@drawable/bg_card"
            android:paddingHorizontal="14dp"
            android:paddingVertical="13dp"
            android:text="@string/feed_search_hint"
            android:textColor="@color/text_secondary"
            android:textSize="13sp"
            app:layout_constraintTop_toBottomOf="@id/title"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toEndOf="parent" />

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/dealList"
            android:layout_width="0dp"
            android:layout_height="0dp"
            android:layout_marginTop="14dp"
            android:clipToPadding="false"
            android:paddingHorizontal="@dimen/screen_margin"
            android:paddingBottom="@dimen/screen_margin"
            app:layout_constraintTop_toBottomOf="@id/searchBar"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toEndOf="parent"
            tools:listitem="@layout/item_deal" />

    </androidx.constraintlayout.widget.ConstraintLayout>
</layout>
```


- [ ] **Step 12: Fragment 작성**

`presentation/src/main/java/com/sypark/flightdeal/feed/DealFeedFragment.kt`:

```kotlin
package com.sypark.flightdeal.feed

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sypark.flightdeal.R
import com.sypark.flightdeal.databinding.FragmentDealFeedBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DealFeedFragment : Fragment(R.layout.fragment_deal_feed) {

    private val viewModel: DealFeedViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentDealFeedBinding.bind(view)

        val adapter = DealAdapter { /* 딥링크 연결은 이후 계획서에서 */ }
        binding.dealList.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state is DealFeedUiState.Success) adapter.submitList(state.deals)
                }
            }
        }
    }
}
```

로딩·빈·오류 상태 UI는 Task 9에서 붙인다. 여기서는 성공 경로만 그린다.

- [ ] **Step 13: 빌드와 테스트**

```bash
./gradlew :presentation:testDebugUnitTest :presentation:assembleDebug
```

기대: PASS (7건) + `BUILD SUCCESSFUL`

- [ ] **Step 14: 실제 기기/에뮬레이터에서 확인**

```bash
./gradlew :presentation:installDebug
adb shell am start -n com.sypark.flightdeal/.MainActivity
```

눈으로 확인할 것:

| 항목 | 기대 |
|---|---|
| 하단 탭 | 특가 / 추적 / 검색 / 내정보 4개, 선택된 탭이 인디고 |
| 상단 | "오늘의 특가" 굵은 제목, 아래 "어디로 떠나세요?" 검색바 |
| 카드 | 도쿄 189,000원 / 방콕 241,000원 등 6장 |
| 배지 | 연한 인디고 배경에 인디고 글씨로 `평균가 −xx%` |
| 취소선 | 배지가 있는 카드에만 회색 취소선 기준가 |
| 가격 | 화면에서 가장 큰 텍스트 |
| 탭 이동 | 나머지 3탭에서 "준비 중입니다" |

배지가 **한 장도 안 보이면** `FakeDealFixtures.monthlyPrices`의 배수가 잘못된 것이다.
Task 7의 `할인 배지가 붙은 딜이 하나 이상 있다` 테스트가 통과했는지 먼저 확인한다.

- [ ] **Step 15: 커밋**

```bash
git add presentation
git commit -m "feat: 인디고 테마와 특가 피드 화면 구현"
```

---

## Task 9: 로딩·빈 데이터·오류 상태

**Files:**
- Create: `presentation/src/main/res/layout/view_deal_skeleton.xml`
- Create: `presentation/src/main/res/layout/view_feed_message.xml`
- Modify: `presentation/src/main/res/layout/fragment_deal_feed.xml`
- Modify: `presentation/src/main/java/com/sypark/flightdeal/feed/DealFeedFragment.kt`

**Interfaces:**
- Consumes: `DealFeedUiState` 네 가지 상태 (Task 7)
- Produces: 네 상태가 모두 화면에 표현되는 완성된 피드 화면

- [ ] **Step 1: Shimmer 스켈레톤 레이아웃 작성**

`presentation/src/main/res/layout/view_deal_skeleton.xml`:

**`<layout>` 태그로 감싸는 것이 필수다.** 감싸지 않으면 DataBinding이 이 레이아웃의
바인딩 클래스를 만들지 않고, `<include>`한 쪽에서 `binding.skeleton.shimmer`로
접근할 수 없다. `binding.skeleton`이 바인딩 객체가 아니라 그냥 `View`가 되기 때문이다.

```xml
<?xml version="1.0" encoding="utf-8"?>
<layout xmlns:android="http://schemas.android.com/apk/res/android">

    <com.facebook.shimmer.ShimmerFrameLayout
        android:id="@+id/shimmer"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:paddingHorizontal="@dimen/screen_margin">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <View
                android:layout_width="match_parent"
                android:layout_height="92dp"
                android:layout_marginBottom="@dimen/card_gap"
                android:background="@drawable/bg_card" />

            <View
                android:layout_width="match_parent"
                android:layout_height="92dp"
                android:layout_marginBottom="@dimen/card_gap"
                android:background="@drawable/bg_card" />

            <View
                android:layout_width="match_parent"
                android:layout_height="92dp"
                android:background="@drawable/bg_card" />
        </LinearLayout>
    </com.facebook.shimmer.ShimmerFrameLayout>
</layout>
```

- [ ] **Step 2: 메시지 뷰 레이아웃 작성**

빈 상태와 오류 상태가 같은 레이아웃을 공유한다. 문구와 버튼 노출만 다르다.

`presentation/src/main/res/layout/view_feed_message.xml`:

스켈레톤과 같은 이유로 `<layout>` 태그가 필요하다.

```xml
<?xml version="1.0" encoding="utf-8"?>
<layout xmlns:android="http://schemas.android.com/apk/res/android">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:gravity="center"
        android:orientation="vertical"
        android:paddingHorizontal="32dp">

        <TextView
            android:id="@+id/messageTitle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/text_primary"
            android:textSize="16sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/messageBody"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="6dp"
            android:gravity="center"
            android:textColor="@color/text_secondary"
            android:textSize="13sp" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/retryButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:backgroundTint="@color/indigo"
            android:text="@string/retry"
            android:textColor="@color/white"
            android:visibility="gone" />
    </LinearLayout>
</layout>
```

- [ ] **Step 3: 피드 레이아웃에 두 뷰 포함**

`fragment_deal_feed.xml`의 `RecyclerView` **바로 다음**에 아래를 추가한다.
제약은 `RecyclerView`와 동일하게 준다.

```xml
        <include
            android:id="@+id/skeleton"
            layout="@layout/view_deal_skeleton"
            android:layout_width="0dp"
            android:layout_height="0dp"
            android:visibility="gone"
            app:layout_constraintTop_toBottomOf="@id/searchBar"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toEndOf="parent" />

        <include
            android:id="@+id/message"
            layout="@layout/view_feed_message"
            android:layout_width="0dp"
            android:layout_height="0dp"
            android:visibility="gone"
            app:layout_constraintTop_toBottomOf="@id/searchBar"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toEndOf="parent" />
```

- [ ] **Step 4: Fragment에서 네 상태를 모두 처리**

`DealFeedFragment.kt`의 `onViewCreated`를 아래로 교체한다.

```kotlin
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentDealFeedBinding.bind(view)

        val adapter = DealAdapter { /* 딥링크 연결은 이후 계획서에서 */ }
        binding.dealList.adapter = adapter
        binding.message.retryButton.setOnClickListener { viewModel.refresh() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(binding, adapter, state) }
            }
        }
    }

    private fun render(
        binding: FragmentDealFeedBinding,
        adapter: DealAdapter,
        state: DealFeedUiState,
    ) {
        binding.dealList.isVisible = state is DealFeedUiState.Success
        binding.skeleton.root.isVisible = state is DealFeedUiState.Loading
        binding.message.root.isVisible =
            state is DealFeedUiState.Empty || state is DealFeedUiState.Error

        when (state) {
            DealFeedUiState.Loading -> binding.skeleton.shimmer.startShimmer()

            is DealFeedUiState.Success -> {
                binding.skeleton.shimmer.stopShimmer()
                adapter.submitList(state.deals)
            }

            DealFeedUiState.Empty -> {
                binding.skeleton.shimmer.stopShimmer()
                binding.message.messageTitle.setText(R.string.feed_empty_title)
                binding.message.messageBody.setText(R.string.feed_empty_body)
                // 빈 데이터는 오류가 아니다. 재시도해도 결과가 같으므로 버튼을 감춘다.
                binding.message.retryButton.isVisible = false
            }

            is DealFeedUiState.Error -> {
                binding.skeleton.shimmer.stopShimmer()
                binding.message.messageTitle.setText(R.string.feed_error_title)
                binding.message.messageBody.setText(R.string.feed_error_body)
                binding.message.retryButton.isVisible = state.retryable
            }
        }
    }
```

`androidx.core.view.isVisible` import를 추가한다.

- [ ] **Step 5: 세 상태를 눈으로 확인**

`RepositoryModule`의 `provideFlightPriceRepository()`를 잠시 바꿔가며 확인한다.

```kotlin
// 1) 빈 상태
FakeFlightPriceRepository(FakeFlightPriceRepository.Behavior.EmptyData)
// 2) 오류 상태
FakeFlightPriceRepository(FakeFlightPriceRepository.Behavior.Failing)
```

각각 `./gradlew :presentation:installDebug` 후 확인할 것:

| 모드 | 기대 |
|---|---|
| `Normal` | 앱 시작 직후 회색 카드 3장이 반짝이다가(Shimmer) 딜 목록으로 바뀜 |
| `EmptyData` | "아직 특가가 없어요" + 설명. **재시도 버튼 없음** |
| `Failing` | "가격을 불러오지 못했어요" + 인디고 재시도 버튼. 눌러도 다시 실패 |

- [ ] **Step 6: `Normal`로 되돌리고 최종 확인**

`RepositoryModule`을 `FakeFlightPriceRepository()`로 되돌린다.

```bash
./gradlew :domain:test :data:test :presentation:testDebugUnitTest :presentation:assembleDebug
```

기대: 전체 PASS + `BUILD SUCCESSFUL`

- [ ] **Step 7: 커밋**

```bash
git add presentation
git commit -m "feat: 특가 피드 로딩·빈 데이터·오류 상태 화면 추가"
```

---

## 완료 기준

이 계획서가 끝났을 때:

- [ ] Travelpayouts의 한국 노선 데이터 밀도가 문서로 기록되어 있고, 실제 응답 JSON 3건이 픽스처로 저장돼 있다
- [ ] `./gradlew :domain:test :data:test :presentation:testDebugUnitTest`가 전부 통과한다
- [ ] 앱이 실행되고 4탭 하단 내비게이션이 동작한다
- [ ] 특가 피드가 인디고 팔레트로 렌더링되고 할인 배지와 취소선 기준가가 보인다
- [ ] 로딩·성공·빈 데이터·오류 네 상태가 모두 화면에 표현된다
- [ ] `:domain`에 안드로이드 타입도, Travelpayouts라는 단어도 없다
- [ ] `local.properties`가 커밋되지 않았다

## 다음 계획서

Task 1의 검증 결과를 손에 쥔 뒤 작성한다.

- **계획서 2 (spec 4단계):** Travelpayouts 실연동. Retrofit 인터페이스, DTO, 매퍼,
  Task 1이 저장한 픽스처로 파싱 테스트. `RepositoryModule` 한 줄 교체로 전환.
- **계획서 3 (spec 5~6단계):** Room 가격 이력, 추적 목록/상세 화면, 스파크라인 커스텀 뷰,
  `PriceCheckWorker`, 알림, `POST_NOTIFICATIONS` 권한.
- **계획서 4 (spec 7단계):** 날짜별 최저가 캘린더, 목적지 탐색, Custom Tabs 딥링크.
