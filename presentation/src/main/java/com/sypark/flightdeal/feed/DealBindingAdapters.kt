package com.sypark.flightdeal.feed

import android.graphics.Paint
import android.view.View
import android.widget.TextView
import com.sypark.flightdeal.R
import com.sypark.flightdeal.domain.model.Won
import java.text.NumberFormat
import java.util.Locale

private val KRW: NumberFormat = NumberFormat.getIntegerInstance(Locale.KOREA)

/**
 * [Won]은 Kotlin 인라인 값 클래스라 JVM 바이트코드에서 getter 이름이 맹글링된다
 * (예: `getPrice-XXXXXXX`). 데이터 바인딩의 XML 표현식(`@{item.quote.price}`)은
 * 리플렉션으로 `getPrice()`를 찾기 때문에 접근자를 찾지 못해 컴파일이 실패한다.
 * 그래서 XML에 `app:wonPrice="@{...}"`로 선언하지 않고, [DealAdapter]에서
 * 이 확장 함수를 직접 호출한다.
 */
fun TextView.setWonPrice(won: Won?) {
    text = won?.let { context.getString(R.string.price_won, KRW.format(it.amount)) }.orEmpty()
}

/** 취소선 기준가. 값이 없으면 뷰 자체를 감춘다. */
fun TextView.setStrikethroughPrice(won: Won?) {
    if (won == null) {
        visibility = View.GONE
        return
    }
    visibility = View.VISIBLE
    paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
    text = context.getString(R.string.price_won, KRW.format(won.amount))
}
