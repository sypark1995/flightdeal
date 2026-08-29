package com.sypark.flightdeal.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * `AppWidgetProvider` 대신 Glance가 요구하는 `GlanceAppWidgetReceiver`를 쓴다.
 * 시스템이 브로드캐스트를 받으면 이 클래스를 거쳐 [PriceWidget.provideGlance]로 넘어간다.
 */
class PriceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PriceWidget()
}
