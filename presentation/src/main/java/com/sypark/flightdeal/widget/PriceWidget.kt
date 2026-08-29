package com.sypark.flightdeal.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sypark.flightdeal.MainActivity
import com.sypark.flightdeal.R
import com.sypark.flightdeal.domain.model.PRICE_HISTORY_RETENTION_DAYS
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.PriceHistoryRepository
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import com.sypark.flightdeal.feed.formatWon
import com.sypark.flightdeal.tracking.TrackedItem
import com.sypark.flightdeal.tracking.previousDifferingSnapshot
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate

/**
 * Glance의 `provideGlance`는 Composable이 아니라 suspend 함수라 Hilt가 생성자 주입을
 * 해주지 않는다. `SingletonComponent`에서 직접 꺼내는 통로가 필요하다.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PriceWidgetEntryPoint {
    fun trackedRoutes(): TrackedRouteRepository
    fun history(): PriceHistoryRepository
    fun clock(): Clock
}

/** 위젯 한 화면에 몇 줄까지 보여줄지. 좁은 위젯에서 다 보여주려 하면 아무것도 안 읽힌다. */
private const val MAX_ROWS = 3

class PriceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context, PriceWidgetEntryPoint::class.java)
        val rows = widgetRows(loadTrackedItems(entryPoint), limit = MAX_ROWS)

        provideContent {
            PriceWidgetContent(context, rows)
        }
    }

    /**
     * 위젯은 갱신 시점에 한 장을 그리는 것이지 계속 구독하는 화면이 아니다.
     * `observeHistory`가 돌려주는 Flow에서 `first()`로 한 번만 값을 읽는다 —
     * `TrackingViewModel`처럼 계속 구독하면 위젯 프로세스가 끝나지 않는 구독을 남긴다.
     *
     * `previous`는 [previousDifferingSnapshot]으로 고른다. 추적 화면(`TrackingViewModel`)과
     * 정확히 같은 함수를 부른다 — 규칙을 두 번 짜면 같은 노선에 대해 두 화면이 다른
     * 화살표를 보여주는 사고가 난다.
     */
    private suspend fun loadTrackedItems(entryPoint: PriceWidgetEntryPoint): List<TrackedItem> {
        val today = LocalDate.now(entryPoint.clock())
        return entryPoint.trackedRoutes().getAll().map { route ->
            val recent = entryPoint.history()
                .observeHistory(route.id, PRICE_HISTORY_RETENTION_DAYS)
                .first()
            val latest = recent.lastOrNull()
            TrackedItem(
                tracked = route,
                latest = latest,
                previous = previousDifferingSnapshot(recent, latest),
                history = recent,
                hasDeparted = route.hasDeparted(today),
            )
        }
    }
}

@Composable
private fun PriceWidgetContent(context: Context, rows: List<WidgetRow>) {
    val openTracking = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_OPEN_TRACKING, true)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(R.color.window_background))
            .appWidgetBackground()
            .padding(12.dp)
            .clickable(actionStartActivity(openTracking)),
    ) {
        Text(
            text = "추적 중인 항공권",
            style = TextStyle(
                color = ColorProvider(R.color.widget_text_primary),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(modifier = GlanceModifier.height(8.dp))

        if (rows.isEmpty()) {
            Text(
                text = "추적 중인 항공권이 없어요",
                style = TextStyle(color = ColorProvider(R.color.widget_text_secondary), fontSize = 13.sp),
            )
        } else {
            rows.forEach { row ->
                WidgetRowLine(row)
                Spacer(modifier = GlanceModifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun WidgetRowLine(row: WidgetRow) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                // 지난 여정은 값이 더 갱신되지 않는다. 흐리게 하고 "· 지난"을 붙여
                // 최신 가격으로 오해하지 않게 한다.
                text = if (row.hasDeparted) "${row.label} · 지난" else row.label,
                style = TextStyle(
                    color = ColorProvider(
                        if (row.hasDeparted) R.color.widget_text_secondary else R.color.widget_text_primary,
                    ),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Text(
                text = row.price?.let { formatWon(it) } ?: "가격을 모으는 중이에요",
                style = TextStyle(
                    color = ColorProvider(R.color.widget_text_primary),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }

        val price = row.price
        val previous = row.previous
        // 지난 여정은 더 조회하지 않으니 변동도 없다. 추적 화면(TrackedRouteCard)과
        // 같은 조건으로 화살표를 가린다.
        if (!row.hasDeparted && price != null && previous != null && price != previous) {
            val dropped = price < previous
            Text(
                text = if (dropped) "▼" else "▲",
                style = TextStyle(
                    // 색이 정보를 나른다. 하락은 항상 초록, 상승은 항상 빨강 — 추적 화면과 같다.
                    color = ColorProvider(if (dropped) R.color.widget_price_down else R.color.widget_price_up),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}
