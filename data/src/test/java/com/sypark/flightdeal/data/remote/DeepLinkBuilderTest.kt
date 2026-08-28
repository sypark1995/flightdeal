package com.sypark.flightdeal.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkBuilderTest {

    @Test
    fun `상대 경로에 도메인과 마커를 붙인다`() {
        val url = DeepLinkBuilder.build("/search/ICN0610TYO1?t=abc", marker = "123456")!!
        assertTrue(url.startsWith("https://www.aviasales.com/search/ICN0610TYO1?t=abc"))
        assertTrue(url.contains("marker=123456"))
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
