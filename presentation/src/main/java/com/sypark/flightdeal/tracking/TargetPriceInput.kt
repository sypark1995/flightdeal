package com.sypark.flightdeal.tracking

import com.sypark.flightdeal.domain.model.Won

/**
 * 사용자가 친 문자열을 목표가로 바꾼다. 쓸 수 없는 입력이면 null.
 *
 * 안드로이드 import가 하나도 없다 — JVM 유닛 테스트에서 그대로 부를 수 있어야 한다.
 */
fun parseTargetPrice(text: String): Won? {
    // 숫자만 남긴다. 쉼표를 치기도 하고 붙여넣기로 "원"이 딸려오기도 한다.
    val digits = text.filter { it.isDigit() }
    if (digits.isEmpty()) return null

    // Won.amount는 Int다. toIntOrNull()만 쓰면 넘칠 때도 조용히 null이 되어
    // "너무 큰 값"과 "숫자가 아닌 입력"을 구별할 수 없다. Long으로 먼저 받아
    // Int 범위인지 판정한 뒤에 변환한다 — 둘 다 결과는 null로 같지만, 여기서
    // 갈라 둬야 나중에 이유를 나눠 보여주고 싶을 때 바로 쓸 수 있다.
    val value = digits.toLongOrNull() ?: return null
    if (value > Int.MAX_VALUE) return null

    // 0원은 목표가로 뜻이 없다.
    if (value == 0L) return null

    return Won(value.toInt())
}
