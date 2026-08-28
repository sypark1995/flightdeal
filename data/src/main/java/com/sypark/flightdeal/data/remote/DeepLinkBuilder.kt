package com.sypark.flightdeal.data.remote

/**
 * API의 `link`는 `/search/ICN0610TYO1?t=...` 형태의 상대 경로다.
 * 도메인을 붙여야 열리고, marker를 붙여야 커미션이 계정에 잡힌다.
 */
object DeepLinkBuilder {

    private const val BASE = "https://www.aviasales.com"

    fun build(relativeLink: String?, marker: String): String? {
        if (relativeLink.isNullOrBlank()) return null

        val absolute = if (relativeLink.startsWith("http")) relativeLink else BASE + relativeLink
        if (marker.isBlank()) return absolute

        val separator = if (absolute.contains('?')) '&' else '?'
        return "$absolute${separator}marker=$marker"
    }
}
