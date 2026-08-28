package com.sypark.flightdeal.data.di

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NetworkModuleTest {

    @Test
    fun `로그용 URL에서 토큰을 지운다`() {
        val url = "https://api.travelpayouts.com/x?origin=ICN&token=secret123&currency=krw".toHttpUrl()

        val safe = url.withoutToken()

        assertFalse(safe.toString().contains("secret123"))
        assertEquals(null, safe.queryParameter("token"))
    }

    @Test
    fun `다른 쿼리 파라미터는 그대로 둔다`() {
        val url = "https://api.travelpayouts.com/x?origin=ICN&token=secret123&currency=krw".toHttpUrl()

        val safe = url.withoutToken()

        assertEquals("ICN", safe.queryParameter("origin"))
        assertEquals("krw", safe.queryParameter("currency"))
    }

    @Test
    fun `토큰이 없어도 그대로 동작한다`() {
        val url = "https://api.travelpayouts.com/x?origin=ICN".toHttpUrl()

        assertEquals("ICN", url.withoutToken().queryParameter("origin"))
    }
}
