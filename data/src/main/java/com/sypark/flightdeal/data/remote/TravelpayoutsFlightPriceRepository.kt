package com.sypark.flightdeal.data.remote

import com.sypark.flightdeal.domain.model.AppResult
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.PriceQuote
import com.sypark.flightdeal.domain.model.PriceStats
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TripType
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
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class TravelpayoutsFlightPriceRepository(
    private val api: TravelpayoutsApi,
    private val marker: String,
    private val clock: Clock,
    private val destinations: List<String> = DEFAULT_DESTINATIONS,
) : FlightPriceRepository {

    /** 예약처는 특가 목록을 고를 때만 쓴다. 도메인 밖으로 새 나가지 않는다. */
    private data class GatedQuote(val gate: String?, val quote: PriceQuote)

    override suspend fun cheapestDeals(
        origin: Airport,
        limit: Int,
        tripType: TripType,
    ): AppResult<List<PriceQuote>> = call {
        val month = YearMonth.now(clock).plusMonths(LEAD_MONTHS)

        coroutineScope {
            destinations
                .map { destination -> async { fetch(origin.iata, destination, month, tripType) } }
                .awaitAll()
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

        val foundAt = clock.instant()
        return response.data.orEmpty().mapNotNull { dto ->
            PriceQuoteMapper.toDomain(dto, foundAt, marker)?.let { GatedQuote(dto.gate, it) }
        }
    }

    /**
     * 예외를 [AppResult]로 옮긴다.
     *
     * 연결 실패만 [AppResult.NetworkError]다 — 재시도할 가치가 있는 유일한 경우다.
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
                AppResult.Unknown(e)
            } catch (e: Exception) {
                AppResult.Unknown(e)
            }
        }

    companion object {
        private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

        /** 지금 사면 비싸다. 두 달 뒤가 특가가 나오는 구간이다. */
        private const val LEAD_MONTHS = 2L

        /** 피드에 띄울 인기 목적지. 설정 화면이 생기면 사용자가 고르게 한다. */
        val DEFAULT_DESTINATIONS = listOf("TYO", "BKK", "DAD", "TPE", "HKG", "SIN")
    }
}
