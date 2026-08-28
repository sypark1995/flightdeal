# Travelpayouts 한국 노선 검증 (2026-08-28)

계획서 Task 1. 실제 토큰으로 인천 출발 세 노선을 조회해 데이터 밀도와 응답 스키마를 확인했다.

## 결론

**통과. 계획대로 진행한다.** 세 노선 모두 판정 기준(노선당 날짜 10일 이상)을 넘겼고,
가격은 KRW로 오며 딥링크가 100% 채워져 있다.

다만 그대로 쓸 수 없는 것이 세 가지 있다 — 편도 기본값, 예약처 구성, 상대 경로 링크.
아래에 적는다.

## 사용한 엔드포인트

```
GET https://api.travelpayouts.com/aviasales/v3/prices_for_dates
  ?origin=ICN&destination=TYO&departure_at=2026-10
  &currency=krw&sorting=price&limit=1000&token=...
```

**`v1/prices/calendar`는 쓰지 않는다.** 계획서가 1순위로 적어둔 엔드포인트인데,
`depart_date=2026-10`을 줘도 무시하고 오늘부터 1년치를 돌려준다(152개 항목,
2026-08-28 ~ 2027-08-01, 그중 2026-10은 27개). 달을 지정할 수 없으니 캘린더 화면에
쓰기 어렵고, 응답에 딥링크도 없다.

`v3/prices_for_dates`는 요청한 달만 정확히 돌려주고 `link`를 포함한다.

## 결과

| 노선 | success | 건수 / 고유 날짜 | 최저가 | 중앙값 | 최고가 | 딥링크 | 판정 |
|---|---|---|---|---|---|---|---|
| ICN → TYO (도쿄) | true | 31 / 31 | 100,354원 | 145,657원 | 207,026원 | 31/31 | 통과 |
| ICN → BKK (방콕) | true | 25 / 25 | 135,352원 | 186,277원 | 227,331원 | 25/25 | 통과 |
| ICN → DAD (다낭) | true | 20 / 20 | 97,645원 | 103,574원 | 179,325원 | 20/20 | 통과 |

날짜 범위는 세 노선 모두 2026-10-01 ~ 10-30/31. **하루에 하나씩, 그날의 최저가**가 온다.
한산한 노선일수록 건수가 줄어드는 경향이 보인다(다낭 20건 = 10일치 결측).

## 응답 스키마 (v3/prices_for_dates)

```
airline              "ZE"                 IATA 항공사 코드
flight_number        "613"
origin / destination "SEL" / "TYO"        도시 코드
origin_airport       "ICN"                공항 코드
destination_airport  "NRT"
departure_at         "2026-10-06T15:15:00+09:00"   ISO 8601, KST 오프셋 포함
return_at            (편도 조회 시 없음)
price                100354               정수, currency 파라미터 단위
gate                 "Trip.com"           예약처
transfers            1                    경유 횟수
return_transfers     0
duration             380                  분
duration_to / duration_back
link                 "/search/ICN0610TYO1?t=..."   상대 경로
```

`PriceQuote`에 그대로 대응된다. `foundAt`은 응답에 없으므로 조회 시각을 채운다.

## 그대로 쓸 수 없는 것

### 1. 기본값이 편도다

`return_at` 필드 자체가 오지 않는다(0/31). 100,354원은 편도 가격이다.
왕복을 받으려면 `one_way=false`와 `return_at`을 함께 준다.

```
&departure_at=2026-10&return_at=2026-10&one_way=false
```

ICN→TYO 왕복은 44건, 301,430 ~ 413,101원으로 현실적인 값이 나온다.

**결정: 왕복을 기본으로 하고 편도 토글을 둔다.** 여행자가 실제로 사는 형태가 왕복이고,
가격 추적도 왕복 기준이어야 의미가 있다. 편도는 피드 상단 토글로 전환한다.
같은 화면에 둘을 섞지는 않는다 — 10만원과 30만원이 나란히 놓이면 비교가 무의미해진다.

도메인 모델의 `PriceQuote.returnDate`가 nullable이라 둘 다 담을 수 있다. 토글 상태는
`cheapestDeals` 호출의 파라미터가 되고, 추적 항목은 등록 시점의 형태를 기억한다.

### 2. 예약처의 절반 이상이 한국에서 쓸 수 없다

| 노선 | Trip.com | Kiwi.com | 나머지 (CIS 시장) |
|---|---|---|---|
| TYO | 13/31 (42%) | 1 | Biletix 7, Kupi.com 4, Aviakassa 3, Т-Банк 1, Clickavia 1, Авиасейлс 1 |
| BKK | 11/25 (44%) | 1 | Авиасейлс 6, Biletix 3, Clickavia 2, Kupi.com 1, City.Travel 1 |
| DAD | 9/20 (45%) | 1 | Авиасейлс 6, Kupi.com 2, Т-Банк 1, Tickets.kz 1 |

Aviakassa, Kupi.com, Biletix, Clickavia, Авиасейлс, Т-Банк, Tickets.kz는 러시아·CIS
시장 OTA다. 한국 사용자가 결제까지 가기 어렵거나 불가능하다. Aviasales가 러시아에서
출발한 서비스라 예약처 구성이 그쪽에 치우쳐 있다.

실제로 쓸 만한 것은 **Trip.com + Kiwi.com, 노선당 약 45~48%** 다.

**결정: 거르되 부족하면 보충한다.** 화이트리스트(Trip.com, Kiwi.com)를 우선 채우고,
결과가 임계 건수에 못 미치면 나머지로 채운다. 대부분의 카드는 실제 예약 가능한 곳으로
가면서 한산한 노선에서 화면이 비는 일도 막는다.

이 필터링은 `:data`의 매퍼에서 한다. `:domain`은 예약처가 무엇인지 알 필요가 없다.
화이트리스트는 상수로 두되, 나중에 한국에서 쓸 수 있는 예약처가 더 확인되면 늘린다.

### 3. `link`는 상대 경로다

`/search/ICN0610TYO1?t=...` 형태로 온다. 도메인을 붙여야 하고, 커미션을 받으려면
`marker`를 붙여야 한다. 마커는 아직 발급받지 않았다(대시보드 좌측 하단 Partner ID).

딥링크 조립은 `:data`의 매퍼 책임이다.

## 계획서에 반영할 것

- 엔드포인트를 `v3/prices_for_dates`로 확정. `v1/prices/calendar`는 후보에서 제외.
- `PriceQuote.foundAt`은 응답에 없으므로 조회 시각으로 채운다.
- gate 화이트리스트를 `:data`에 둔다.
- 딥링크 조립(도메인 + marker)을 `:data` 매퍼에 둔다.
- 왕복이 기본이므로 `one_way=false` + `return_at`을 기본 파라미터로 쓴다. 편도 토글은 이를 뒤집는다.
- gate 화이트리스트는 우선순위 정렬이지 완전 배제가 아니다. 부족분은 나머지로 채운다.

## 픽스처

파싱 테스트에 그대로 쓴다. 토큰은 응답에 포함되지 않아 커밋해도 안전하다(확인함).

- `fixtures/v3-ICN-TYO.json` — 편도 31건
- `fixtures/v3-ICN-BKK.json` — 편도 25건
- `fixtures/v3-ICN-DAD.json` — 편도 20건
- `fixtures/v3-ICN-TYO-roundtrip.json` — 왕복 44건
- `fixtures/v1-calendar-ICN-TYO.json` — 채택하지 않은 엔드포인트. 달 지정이 무시되는 근거로 남긴다.
