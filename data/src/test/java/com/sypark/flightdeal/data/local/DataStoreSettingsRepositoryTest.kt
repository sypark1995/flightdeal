package com.sypark.flightdeal.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.sypark.flightdeal.domain.model.Airport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

class DataStoreSettingsRepositoryTest {

    private lateinit var repository: DataStoreSettingsRepository
    private lateinit var file: File

    @Before
    fun setUp() {
        file = File.createTempFile("settings-test", ".preferences_pb").apply { deleteOnExit() }
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        repository = DataStoreSettingsRepository(dataStore)
    }

    @Test
    fun `고른 적이 없으면 인천이다`() = runTest {
        assertEquals(Airport.INCHEON, repository.observeOrigin().first())
    }

    @Test
    fun `고른 공항이 남는다`() = runTest {
        repository.setOrigin(Airport("GMP", "김포", "대한민국"))

        assertEquals("GMP", repository.observeOrigin().first().iata)
    }

    @Test
    fun `목록에 없는 값이 저장돼 있으면 인천으로 돌아간다`() = runTest {
        // ORIGINS에서 빠진 공항 코드. 앱 버전이 올라가며 선택지가 줄면 이런 값이
        // 남을 수 있다 — 그때 예외를 던지면 앱이 열리지 않는다.
        repository.setOrigin(Airport("NRT", "나리타", "일본"))

        assertEquals(Airport.INCHEON, repository.observeOrigin().first())
    }
}
