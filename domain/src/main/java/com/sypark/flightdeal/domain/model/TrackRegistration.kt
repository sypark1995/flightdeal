package com.sypark.flightdeal.domain.model

/**
 * 추적 등록 결과. 이미 추적 중인 노선을 다시 등록하면 새 행을 만들지 않고
 * 기존 id를 돌려주는데(멱등한 등록), 그것만으로는 호출한 쪽이 새로 만들어진
 * 것인지 알 수 없다. 안내 문구가 "추적을 시작했어요"와 "이미 추적 중이에요"로
 * 갈리므로 구분해서 돌려준다.
 *
 * @param isNew 새로 만들어졌으면 true, 이미 추적 중이던 것이면 false.
 */
data class TrackRegistration(val id: Long, val isNew: Boolean)
