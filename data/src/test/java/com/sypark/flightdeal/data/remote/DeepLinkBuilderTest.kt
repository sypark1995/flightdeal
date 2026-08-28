package com.sypark.flightdeal.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkBuilderTest {

    @Test
    fun `쿼리가 있는 경로에는 앰퍼샌드로 마커를 잇는다`() {
        // startsWith + contains로 나눠 검사하면 물음표를 두 번 붙이는 구현도 통과한다.
        // 전체 문자열을 그대로 비교한다.
        assertEquals(
            "https://www.aviasales.com/search/ICN0610TYO1?t=abc&marker=123456",
            DeepLinkBuilder.build("/search/ICN0610TYO1?t=abc", marker = "123456"),
        )
    }

    @Test
    fun `쿼리가 없는 경로에도 마커를 붙인다`() {
        val url = DeepLinkBuilder.build("/search/ICN0610TYO1", marker = "123456")!!
        assertEquals("https://www.aviasales.com/search/ICN0610TYO1?marker=123456", url)
    }

    @Test
    fun `마커가 비어 있으면 붙이지 않는다`() {
        // 마커 미발급 상태에서도 링크 자체는 동작해야 한다. 커미션만 안 붙는다.
        val url = DeepLinkBuilder.build("/search/ICN0610TYO1?t=abc", marker = "")!!
        assertEquals("https://www.aviasales.com/search/ICN0610TYO1?t=abc", url)
    }

    @Test
    fun `링크가 없으면 null이다`() {
        assertNull(DeepLinkBuilder.build(null, marker = "123456"))
    }

    @Test
    fun `이미 절대 URL이면 도메인을 덧붙이지 않는다`() {
        val url = DeepLinkBuilder.build("https://www.aviasales.com/search/x", marker = "1")!!
        assertEquals("https://www.aviasales.com/search/x?marker=1", url)
    }
}
