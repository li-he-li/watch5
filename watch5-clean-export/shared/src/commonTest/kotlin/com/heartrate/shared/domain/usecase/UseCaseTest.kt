package com.heartrate.shared.domain.usecase

import com.heartrate.shared.data.model.HeartRateData
import com.heartrate.shared.domain.repository.HeartRateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for domain use cases with mock repositories.
 */
class UseCaseTest {

    /**
     * Mock repository for testing purposes.
     */
    private class MockHeartRateRepository(
        private val heartRateFlow: Flow<HeartRateData> = flowOf(),
        private val mockBatteryLevel: Int? = 85
    ) : HeartRateRepository {
        private var listening = false

        override fun observeHeartRate(): Flow<HeartRateData> = heartRateFlow

        override suspend fun startListening() {
            listening = true
        }

        override suspend fun stopListening() {
            listening = false
        }

        override suspend fun getBatteryLevel(): Int? = mockBatteryLevel

        override fun isListening(): Boolean = listening
    }

    @Test
    fun `ObserveHeartRate invoke returns flow from repository`() = runTest {
        // Arrange
        val expectedData = HeartRateData(
            timestamp = 1234567890L,
            heartRate = 72,
            deviceId = "test-device",
            batteryLevel = 85,
            signalQuality = 95
        )
        val mockRepository = MockHeartRateRepository(
            heartRateFlow = flowOf(expectedData)
        )
        val useCase = ObserveHeartRate(mockRepository)

        // Act
        val flow = useCase()

        // Assert
        val collectedData = mutableListOf<HeartRateData>()
        flow.collect { collectedData.add(it) }

        assertEquals(1, collectedData.size)
        assertEquals(expectedData, collectedData[0])
    }

    @Test
    fun `ObserveHeartRate start calls repository startListening`() = runTest {
        // Arrange
        val mockRepository = MockHeartRateRepository()
        val useCase = ObserveHeartRate(mockRepository)

        // Act
        useCase.start()

        // Assert
        assertTrue(mockRepository.isListening())
    }

    @Test
    fun `ObserveHeartRate stop calls repository stopListening`() = runTest {
        // Arrange
        val mockRepository = MockHeartRateRepository()
        val useCase = ObserveHeartRate(mockRepository)

        // Act
        useCase.start()
        assertTrue(useCase.isActive()) // Verify started
        useCase.stop()

        // Assert
        assertFalse(mockRepository.isListening())
    }

    @Test
    fun `ObserveHeartRate isActive returns repository isListening state`() = runTest {
        // Arrange
        val mockRepository = MockHeartRateRepository()
        val useCase = ObserveHeartRate(mockRepository)

        // Act & Assert - Initially not active
        assertFalse(useCase.isActive())

        // Act & Assert - After start
        useCase.start()
        assertTrue(useCase.isActive())

        // Act & Assert - After stop
        useCase.stop()
        assertFalse(useCase.isActive())
    }

    @Test
    fun `ObserveHeartRate emits multiple values from flow`() = runTest {
        // Arrange
        val heartRates = listOf(
            HeartRateData(timestamp = 1000L, heartRate = 70, deviceId = "device1"),
            HeartRateData(timestamp = 2000L, heartRate = 75, deviceId = "device1"),
            HeartRateData(timestamp = 3000L, heartRate = 80, deviceId = "device1")
        )
        val mockRepository = MockHeartRateRepository(
            heartRateFlow = flowOf(*heartRates.toTypedArray())
        )
        val useCase = ObserveHeartRate(mockRepository)

        // Act
        val flow = useCase()
        val collectedData = mutableListOf<HeartRateData>()
        flow.collect { collectedData.add(it) }

        // Assert
        assertEquals(3, collectedData.size)
        assertEquals(70, collectedData[0].heartRate)
        assertEquals(75, collectedData[1].heartRate)
        assertEquals(80, collectedData[2].heartRate)
    }

    @Test
    fun `GetBatteryLevel returns battery level from repository`() = runTest {
        // Arrange
        val expectedBatteryLevel = 75
        val mockRepository = MockHeartRateRepository(
            mockBatteryLevel = expectedBatteryLevel
        )
        val useCase = GetBatteryLevel(mockRepository)

        // Act
        val result = useCase()

        // Assert
        assertEquals(expectedBatteryLevel, result)
    }

    @Test
    fun `GetBatteryLevel returns null when unavailable`() = runTest {
        // Arrange
        val mockRepository = MockHeartRateRepository(
            mockBatteryLevel = null
        )
        val useCase = GetBatteryLevel(mockRepository)

        // Act
        val result = useCase()

        // Assert
        assertEquals(null, result)
    }

    @Test
    fun `GetBatteryLevel returns maximum value of 100`() = runTest {
        // Arrange
        val mockRepository = MockHeartRateRepository(
            mockBatteryLevel = 100
        )
        val useCase = GetBatteryLevel(mockRepository)

        // Act
        val result = useCase()

        // Assert
        assertEquals(100, result)
    }

    @Test
    fun `GetBatteryLevel returns minimum value of 0`() = runTest {
        // Arrange
        val mockRepository = MockHeartRateRepository(
            mockBatteryLevel = 0
        )
        val useCase = GetBatteryLevel(mockRepository)

        // Act
        val result = useCase()

        // Assert
        assertEquals(0, result)
    }

    @Test
    fun `Multiple use cases can use same repository`() = runTest {
        // Arrange
        val mockRepository = MockHeartRateRepository(
            mockBatteryLevel = 90
        )
        val observeHeartRate = ObserveHeartRate(mockRepository)
        val getBatteryLevel = GetBatteryLevel(mockRepository)

        // Act
        observeHeartRate.start()
        val batteryLevel = getBatteryLevel()

        // Assert
        assertTrue(mockRepository.isListening())
        assertEquals(90, batteryLevel)

        // Cleanup
        observeHeartRate.stop()
    }

    @Test
    fun `ObserveHeartRate start and stop are idempotent`() = runTest {
        // Arrange
        val mockRepository = MockHeartRateRepository()
        val useCase = ObserveHeartRate(mockRepository)

        // Act - Start twice
        useCase.start()
        useCase.start()
        assertTrue(useCase.isActive())

        // Act - Stop twice
        useCase.stop()
        useCase.stop()
        assertFalse(useCase.isActive())
    }
}
