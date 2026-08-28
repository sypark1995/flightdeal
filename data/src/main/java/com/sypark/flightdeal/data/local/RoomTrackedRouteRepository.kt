package com.sypark.flightdeal.data.local

import android.util.Log
import com.sypark.flightdeal.data.local.entity.TrackedRouteEntity
import com.sypark.flightdeal.data.remote.AirportNames
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.model.Route
import com.sypark.flightdeal.domain.model.TrackedRoute
import com.sypark.flightdeal.domain.model.TripType
import com.sypark.flightdeal.domain.model.Won
import com.sypark.flightdeal.domain.repository.TrackedRouteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

class RoomTrackedRouteRepository(
    private val dao: TrackedRouteDao,
    private val clock: Clock,
) : TrackedRouteRepository {

    override fun observeAll(): Flow<List<TrackedRoute>> =
        dao.observeAll().map { entities -> entities.mapNotNull { it.toDomain() } }

    override suspend fun getAll(): List<TrackedRoute> = dao.getAll().mapNotNull { it.toDomain() }

    override suspend fun add(
        route: Route,
        departDate: LocalDate,
        returnDate: LocalDate?,
        tripType: TripType,
        targetPrice: Won?,
    ): Long = dao.insert(
        TrackedRouteEntity(
            originIata = route.origin.iata,
            destinationIata = route.destination.iata,
            departDate = departDate.toString(),
            returnDate = returnDate?.toString(),
            tripType = tripType.name,
            targetPrice = targetPrice?.amount,
            createdAt = clock.instant().epochSecond,
        )
    )

    override suspend fun remove(id: Long) = dao.deleteById(id)

    /**
     * DB에는 IATA만 저장한다. 도시 이름은 표시용이므로 읽을 때 채운다 —
     * 이름이 바뀌어도 저장된 데이터를 건드릴 일이 없다.
     *
     * 읽을 수 없는 행은 버린다. `TripType.valueOf`와 `LocalDate.parse`는
     * 예상 밖 값에 예외를 던지는데, 그게 `map` 안에서 터지면 행 하나 때문에
     * 추적 화면 전체를 열 수 없게 된다.
     */
    private fun TrackedRouteEntity.toDomain(): TrackedRoute? = runCatching {
        TrackedRoute(
            id = id,
            route = Route(
                origin = Airport(originIata, AirportNames.cityOf(originIata), ""),
                destination = Airport(destinationIata, AirportNames.cityOf(destinationIata), ""),
            ),
            departDate = LocalDate.parse(departDate),
            returnDate = returnDate?.let(LocalDate::parse),
            tripType = TripType.valueOf(tripType),
            targetPrice = targetPrice?.let(::Won),
            createdAt = Instant.ofEpochSecond(createdAt),
        )
    }.onFailure { Log.w(TAG, "읽을 수 없는 추적 항목을 건너뛴다: id=$id", it) }.getOrNull()

    private companion object {
        const val TAG = "TrackedRoutes"
    }
}
