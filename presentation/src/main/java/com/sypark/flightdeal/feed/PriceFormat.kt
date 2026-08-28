package com.sypark.flightdeal.feed

import com.sypark.flightdeal.domain.model.Won
import java.text.NumberFormat
import java.util.Locale

private val KRW: NumberFormat = NumberFormat.getIntegerInstance(Locale.KOREA)

/**
 * value class인 [Won]을 Compose에서는 그냥 함수로 받는다.
 * XML 표현식과 달리 접근자 이름을 해석할 필요가 없다.
 */
fun formatWon(won: Won): String = "${KRW.format(won.amount)}원"
