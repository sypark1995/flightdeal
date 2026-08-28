package com.sypark.flightdeal.feed

import com.sypark.flightdeal.domain.model.Won
import java.text.NumberFormat
import java.util.Locale

/** NumberFormat은 스레드 안전하지 않다. 알림 포맷팅처럼 UI 밖에서 불릴 때를 대비한다. */
private val KRW: ThreadLocal<NumberFormat> =
    ThreadLocal.withInitial { NumberFormat.getIntegerInstance(Locale.KOREA) }

/**
 * value class인 [Won]을 Compose에서는 그냥 함수로 받는다.
 * XML 표현식과 달리 접근자 이름을 해석할 필요가 없다.
 */
fun formatWon(won: Won): String = "${KRW.get()!!.format(won.amount)}원"
