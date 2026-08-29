package com.sypark.flightdeal.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sypark.flightdeal.domain.model.Airport
import com.sypark.flightdeal.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** IATA 문자열만 저장한다. 도시 이름 등 표시용 값은 [Airport.ORIGINS]에서 채운다. */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override fun observeOrigin(): Flow<Airport> = dataStore.data.map { prefs ->
        val stored = prefs[ORIGIN_IATA_KEY]
        // 목록에서 빠진 공항이 저장돼 있을 수 있다 — 앱 버전이 올라가며 선택지가
        // 줄면 그렇게 된다. 그때 예외를 던지면 앱이 열리지 않는다. 기본값으로 돌아간다.
        Airport.ORIGINS.firstOrNull { it.iata == stored } ?: Airport.INCHEON
    }

    override suspend fun setOrigin(origin: Airport) {
        dataStore.edit { prefs -> prefs[ORIGIN_IATA_KEY] = origin.iata }
    }

    private companion object {
        val ORIGIN_IATA_KEY = stringPreferencesKey("origin_iata")
    }
}
