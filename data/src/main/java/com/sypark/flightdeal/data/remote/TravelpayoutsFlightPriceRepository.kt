package com.sypark.flightdeal.data.remote

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.PriceStats
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.FlightPriceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

class TravelpayoutsFlightPriceRepository(
    private val api: TravelpayoutsApi,
    private val marker: String,
    private val clock: Clock,
    private val destinations: List<String> = Airport.DESTINATIONS.map { it.iata },
) : FlightPriceRepository {

    /** 예약처는 특가 목록을 고를 때만 쓴다. 도메인 밖으로 새 나가지 않는다. */
    private data class GatedQuote(val gate: String?, val quote: PriceQuote)

    private data class CacheKey(
        val origin: String,
        val destination: String,
        val month: YearMonth,
        val tripType: TripType,
    )

    private class CacheEntry(val storedAt: Instant, val quotes: List<GatedQuote>)

    private val cache = ConcurrentHashMap<CacheKey, CacheEntry>()

    override suspend fun cheapestDeals(
        origin: Airport,
        limit: Int,
        tripType: TripType,
    ): AppResult<List<PriceQuote>> = call {
        val month = YearMonth.now(clock).plusMonths(LEAD_MONTHS)

        coroutineScope {
            val outcomes = destinations.map { destination ->
                async {
                    try {
                        Result.success(fetch(origin.iata, destination, month, tripType))
                    } catch (e: CancellationException) {
                        // 취소는 실패가 아니다. 삼키면 취소가 빈 결과로 둔갑한다.
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            }.awaitAll()

            val succeeded = outcomes.mapNotNull { it.getOrNull() }

            // 전부 실패했을 때만 오류다. 하나라도 건졌으면 그것만 보여준다 —
            // 목적지 하나가 429를 맞았다고 나머지 다섯을 버릴 이유가 없다.
            if (succeeded.isEmpty()) {
                outcomes.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { throw it }
            }

            succeeded
                .mapNotNull { quotes ->
                    // 목적지마다 한 건만 고른다. 한국에서 예약 가능한 예약처를 우선하되,
                    // 그런 곳이 없으면 그 노선의 최저가라도 보여준다.
                    // minCount=1이어야 정책이 실제로 고르는 일을 한다.
                    GatePolicy.prioritize(quotes, { it.gate }, minCount = 1).firstOrNull()
                }
                .take(limit)
                .map { it.quote }
        }
    }

    override suspend fun calendarPrices(
        route: Route,
        month: YearMonth,
        tripType: TripType,
    ): AppResult<List<PriceQuote>> = call {
        // 캘린더는 그날의 최저가를 보여주는 화면이다. 예약처로 거르지 않는다.
        fetch(route.origin.iata, route.destination.iata, month, tripType)
            .map { it.quote }
    }

    override suspend fun calendarDeals(
        route: Route,
        month: YearMonth,
        tripType: TripType,
    ): AppResult<List<PriceQuote>> = call {
        fetch(route.origin.iata, route.destination.iata, month, tripType)
            .groupBy { it.quote.departDate }
            .mapNotNull { (_, quotes) ->
                // 딜 피드·추적과 같은 규칙이다. 여기서 갈리면 화면마다 값이 달라진다.
                GatePolicy.prioritize(quotes, { it.gate }, minCount = 1).firstOrNull()?.quote
            }
            .sortedBy { it.departDate }
    }

    override suspend fun priceStats(
        route: Route,
        month: YearMonth,
        tripType: TripType,
    ): AppResult<PriceStats> {
        return when (val prices = calendarPrices(route, month, tripType)) {
            is AppResult.Success ->
                PriceStats.from(prices.data.map { it.price })
                    ?.let { AppResult.Success(it) }
                    ?: AppResult.Empty
            AppResult.Empty -> AppResult.Empty
            is AppResult.NetworkError -> prices
            is AppResult.Unknown -> prices
        }
    }

    override suspend fun trackedPrice(
        route: Route,
        departDate: LocalDate,
        returnDate: LocalDate?,
        tripType: TripType,
    ): AppResult<Won> = callSingle {
        val quotes = fetch(
            originIata = route.origin.iata,
            destinationIata = route.destination.iata,
            month = YearMonth.from(departDate),
            tripType = tripType,
        ).filter { it.quote.departDate == departDate && it.quote.returnDate == returnDate }

        // 딜 피드가 고른 것과 같은 규칙이다. 여기서 규칙이 갈리면 가짜 변동이 생긴다.
        GatePolicy.prioritize(quotes, { it.gate }, minCount = 1).firstOrNull()?.quote?.price
    }

    /**
     * 예약처를 quote에 붙여서 돌려준다. DTO와 도메인 객체를 따로 들고 다니다
     * 인덱스로 짝지으면, 변환에 실패한 항목이 하나만 있어도 전부 어긋난다.
     */
    private suspend fun fetch(
        originIata: String,
        destinationIata: String,
        month: YearMonth,
        tripType: TripType,
    ): List<GatedQuote> {
        val key = CacheKey(originIata, destinationIata, month, tripType)
        val now = clock.instant()

        cache[key]?.let { cached ->
            if (Duration.between(cached.storedAt, now) < CACHE_TTL) return cached.quotes
        }

        val monthText = month.format(MONTH_FORMAT)
        val roundTrip = tripType == TripType.ROUND_TRIP

        val response = api.pricesForDates(
            origin = originIata,
            destination = destinationIata,
            departureAt = monthText,
            returnAt = if (roundTrip) monthText else null,
            oneWay = !roundTrip,
        )
        if (!response.success) throw IllegalStateException("API returned success=false")

        val quotes = response.data.orEmpty().mapNotNull { dto ->
            PriceQuoteMapper.toDomain(dto, now, marker)?.let { GatedQuote(dto.gate, it) }
        }

        // 만료된 항목은 쓸 때 함께 치운다. 별도 청소 작업을 두지 않는다.
        cache.entries.removeIf { Duration.between(it.value.storedAt, now) >= CACHE_TTL }
        cache[key] = CacheEntry(now, quotes)
        return quotes
    }

    /**
     * 예외를 [AppResult]로 옮긴다.
     *
     * 연결 실패와 429·5xx·408은 [AppResult.NetworkError]다 — 재시도할 가치가 있다.
     * 401(토큰 오류)이나 400(잘못된 노선)은 재시도해도 결과가 같으므로 [AppResult.Unknown]이다.
     */
    private suspend fun <T> call(block: suspend () -> List<T>): AppResult<List<T>> =
        withContext(Dispatchers.IO) {
            try {
                block().let { if (it.isEmpty()) AppResult.Empty else AppResult.Success(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                AppResult.NetworkError(e)
            } catch (e: HttpException) {
                // 429·5xx·408은 잠시 뒤 다시 하면 풀린다 — 재시도 버튼을 줘야 한다.
                // 401·400은 몇 번을 눌러도 같은 답이 온다.
                if (e.code() == 408 || e.code() == 429 || e.code() >= 500) {
                    AppResult.NetworkError(e)
                } else {
                    AppResult.Unknown(e)
                }
            } catch (e: Exception) {
                AppResult.Unknown(e)
            }
        }

    private suspend fun <T : Any> callSingle(block: suspend () -> T?): AppResult<T> =
        when (val result = call { listOfNotNull(block()) }) {
            is AppResult.Success -> AppResult.Success(result.data.first())
            AppResult.Empty -> AppResult.Empty
            is AppResult.NetworkError -> result
            is AppResult.Unknown -> result
        }

    companion object {
        private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

        /** 지금 사면 비싸다. 두 달 뒤가 특가가 나오는 구간이다. */
        private const val LEAD_MONTHS = 2L

        /**
         * 피드 한 번에 cheapestDeals와 priceStats가 같은 요청을 연달아 보낸다.
         * 그 간격만 덮으면 되므로 길 필요가 없다. 소스 데이터 자체가 7일 캐시다.
         */
        private val CACHE_TTL: Duration = Duration.ofMinutes(5)
    }
}
