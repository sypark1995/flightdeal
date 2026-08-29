package com.sypark.flightdeal.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * 위젯을 최신 상태로 다시 그리라는 신호.
 *
 * 워커와 ViewModel이 Glance API를 직접 알 필요는 없다 — 인터페이스로 가르면
 * JVM 테스트에서 이 신호를 구현이 없는 채로(=아무 일도 안 하는 채로) 넘길 수 있다.
 */
interface WidgetUpdater {
    suspend fun refresh()
}

/** 위젯 구현이 필요 없는 경로(단위 테스트)의 기본값. */
object NoopWidgetUpdater : WidgetUpdater {
    override suspend fun refresh() = Unit
}

class GlancePriceWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetUpdater {
    override suspend fun refresh() {
        PriceWidget().updateAll(context)
    }
}
