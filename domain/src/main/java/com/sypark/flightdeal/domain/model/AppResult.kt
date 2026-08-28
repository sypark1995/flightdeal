package com.sypark.flightdeal.domain.model

/**
 * [Empty]는 오류가 아니다. 데이터 소스가 캐시 기반이라 한산한 노선은
 * 정상적으로 빈 응답을 준다. 이를 오류로 표시하면 사용자는 앱 고장으로 오해한다.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data object Empty : AppResult<Nothing>
    data class NetworkError(val cause: Throwable) : AppResult<Nothing>
    data class Unknown(val cause: Throwable) : AppResult<Nothing>
}
