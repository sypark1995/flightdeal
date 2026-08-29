package com.sypark.flightdeal.tracking

import com.sypark.flightdeal.domain.model.Won
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TargetPriceInputTest {

    @Test fun `쉼표와 원을 걷어낸다`() {
        assertEquals(Won(304_619), parseTargetPrice("304,619원"))
    }

    @Test fun `비었으면 null이다`() {
        assertNull(parseTargetPrice(""))
        assertNull(parseTargetPrice("   "))
    }

    @Test fun `0은 목표가가 될 수 없다`() {
        assertNull(parseTargetPrice("0"))
        assertNull(parseTargetPrice("0원"))
    }

    @Test fun `Int 범위를 넘으면 null이다`() {
        // Won.amount가 Int다. 자릿수를 계속 치면 넘친다.
        assertNull(parseTargetPrice("99999999999"))
    }

    @Test fun `숫자가 하나도 없으면 null이다`() {
        assertNull(parseTargetPrice("원"))
        assertNull(parseTargetPrice("abc"))
    }
}
