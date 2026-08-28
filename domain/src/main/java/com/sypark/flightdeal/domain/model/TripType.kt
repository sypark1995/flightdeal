package com.sypark.flightdeal.domain.model

/**
 * 왕복이 기본이다. 여행자가 실제로 사는 형태이고, 가격 추적도 왕복 기준이어야 의미가 있다.
 * 같은 화면에 둘을 섞지 않는다 — 10만원(편도)과 30만원(왕복)이 나란히 놓이면 비교가 무의미하다.
 */
enum class TripType { ROUND_TRIP, ONE_WAY }
