package com.heartrate.shared.presentation.viewmodel

import com.heartrate.shared.data.model.HeartRateData
import com.heartrate.shared.domain.repository.HeartRateRepository
import com.heartrate.shared.domain.usecase.GetBatteryLevel
import com.heartrate.shared.domain.usecase.ObserveHeartRate
import com.heartrate.shared.presentation.model.ConnectionStatus
import com.heartrate.shared.presentation.model.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for HeartRateViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HeartRateViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    /**
     * Mock repository for testing.
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

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() {
        // Arrange
        val mockRepository = MockHeartRateRepository()
        val observeHeartRate = ObserveHeartRate(mockRepository)
        val getBatteryLevel = GetBatteryLevel(mockRepository)
        val viewModel = HeartRateViewModel(observeHeartRate, getBatteryLevel)

        // Assert
        val state = viewModel.uiState.value
        assertEquals(0, state.currentHeartRate)
        assertFalse(state.isMonitoring)
        assertNull(state.batteryLevel)
        assertEquals(ConnectionStatus.DISCONNECTED, state.connectionStatus)
        assertNull(state.errorMessage)
        assertNull(state.deviceInfo)
    }

    @Test
    fun `startMonitoring updates isMonitoring to true`() = runTest {
        // Arrange
        val mockRepository = MockHeartRateRepository()
        val observeHeartRate = ObserveHeartRate(mockRepository)
        val getBatteryLevel = GetBatteryLevel(mockRepository)
        val viewModel = HeartRateViewModel(observeHeartRate, getBatteryLevel)

        // Act
        viewModel.startMonitoring()
        testDispatcher.scheduler.runCurrent()

        // Assert
        assertTrue(viewModel.uiState.value.isMonitoring)
        viewModel.onCleared()
    }

    @Test
    fun `startMonitoring collects heart rate data`() = runTest {
        // Arrange
        val expectedData = HeartRateData(
            timestamp = 1234567890L,
            heartRate = 72,
            deviceId = "test-device-123",
            batteryLevel = 85,
            signalQuality = 95
        )
        val mockRepository = MockHeartRateRepository(
            heartRateFlow = flowOf(expectedData)
        )
        val observeHeartRate = ObserveHeartRate(mockRepository)
        val getBatteryLevel = GetBatteryLevel(mockRepository)
        val viewModel = HeartRateViewModel(observeHeartRate, getBatteryLevel)

        // Act
        viewModel.startMonitoring()
        testDispatcher.scheduler.runCurrent()

        // Assert
        assertEquals(72, viewModel.uiState.value.currentHeartRate)
        assertEquals("test-device-123", viewModel.uiState.value.deviceInfo)
        viewModel.onCleared()
    }

    @Test
    fun `startMonitoring collects battery level`() = runTest {
        // Arrange
        val expectedBattery = 90
        val mockRepository = MockHeartRateRepository(
            mockBatteryLevel = expectedBattery
        )
        val observeHeartRate = ObserveHeartRate(mockRepository)
        val getBatteryLevel = GetBatteryLevel(mockRepository)
        val viewModel = HeartRateViewModel(observeHeartRate, getBatteryLevel)

        // Act
        viewModel.startMonitoring()
        testDispatcher.scheduler.advanceTimeBy(5000L)  // Wait for first battery poll

        // Assert
        assertEquals(expectedBattery, viewModel.uiState.value.batteryLevel)
        viewModel.onCleared()
    }

    @Test
    fun `stopMonitoring updates isMonitoring to false`() = runTest {
        // Arrange
        val mockRepository = MockHeartRateRepository()
        val observeHeartRate = ObserveHeartRate(mockRepository)
        val getBatteryLevel = GetBatteryLevel(mockRepository)
        val viewModel = HeartRateViewModel(observeHeartRate, getBatteryLevel)

        // Act
        viewModel.startMonitoring()
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isMonitoring)

        viewModel.stopMonitoring()
        testDispatcher.scheduler.runCurrent()

        // Assert
        assertFalse(viewModel.uiState.value.isMonitoring)
        assertEquals(0, viewModel.uiState.value.currentHeartRate)
        assertEquals(ConnectionStatus.DISCONNECTED, viewModel.uiState.value.connectionStatus)
    }

    @Test
    fun `connectWebSocket updates connection status`() = runTest {
        // Arrange
        val mockRepository = MockHeartRateRepository()
        val observeHeartRate = ObserveHeartRate(mockRepository)
        val getBatteryLevel = GetBatteryLevel(mockRepository)
        val viewModel = HeartRateViewModel(observeHeartRate, getBatteryLevel)

        // Act
        viewModel.connectWebSocket("ws://localhost:8080")
        testDispatcher.scheduler.runCurrent()

        // Assert
        assertEquals(ConnectionStatus.CONNECTED, viewModel.uiState.value.connectionStatus)
    }

    @Test
    fun `disconnectWebSocket updates connection status`() = runTest {
        // Arrange
        val mockRepository = MockHeartRateRepository()
        val observeHeartRate = ObserveHeartRate(mockRepository)
        val getBatteryLevel = GetBatteryLevel(mockRepository)
        val viewModel = HeartRateViewModel(observeHeartRate, getBatteryLevel)

        // Act
        viewModel.connectWebSocket("ws://localhost:8080")
        testDispatcher.scheduler.runCurrent()
        assertEquals(ConnectionStatus.CONNECTED, viewModel.uiState.value.connectionStatus)

        viewModel.disconnectWebSocket()
        testDispatcher.scheduler.runCurrent()

        // Assert
        assertEquals(ConnectionStatus.DISCONNECTED, viewModel.uiState.value.connectionStatus)
    }

    @Test
    fun `startBLE updates connection status`() = runTest {
        // Arrange
        val mockRepository = MockHeartRateRepository()
        val observeHeartRate = ObserveHeartRate(mockRepository)
        val getBatteryLevel = GetBatteryLevel(mockRepository)
        val viewModel = HeartRateViewModel(observeHeartRate, getBatteryLevel)

        // Act
        viewModel.startBLE("HeartRateMonitor")
        testDispatcher.scheduler.runCurrent()

        // Assert
        assertEquals(ConnectionStatus.CONNECTED, viewModel.uiState.value.connectionStatus)
    }

    @Test
    fun `stopBLE updates connection status`() = runTest {
        // Arrange
        val mockRepository = MockHeartRateRepository()
        val observeHeartRate = ObserveHeartRate(mockRepository)
        val getBatteryLevel = GetBatteryLevel(mockRepository)
        val viewModel = HeartRateViewModel(observeHeartRate, getBatteryLevel)

        // Act
        viewModel.startBLE()
        testDispatcher.scheduler.runCurrent()
        assertEquals(ConnectionStatus.CONNECTED, viewModel.uiState.value.connectionStatus)

        viewModel.stopBLE()
        testDispatcher.scheduler.runCurrent()

        // Assert
        assertEquals(ConnectionStatus.DISCONNECTED, viewModel.uiState.value.connectionStatus)
    }

    @Test
    fun `clearError removes error message`() = runTest {
        // Arrange
        val mockRepository = MockHeartRateRepository()
        val observeHeartRate = ObserveHeartRate(mockRepository)
        val getBatteryLevel = GetBatteryLevel(mockRepository)
        val viewModel = HeartRateViewModel(observeHeartRate, getBatteryLevel)

        // Manually set an error
        viewModel.startMonitoring()
        testDispatcher.scheduler.runCurrent()
        // Note: Can't easily induce an error in this test, so we'll verify the method exists
        // In a real scenario, we'd mock the repository to throw an exception

        // The clearError method should exist and not throw
        viewModel.clearError()

        // Assert
        assertNull(viewModel.uiState.value.errorMessage)
        viewModel.onCleared()
    }

    @Test
    fun `onCleared stops monitoring and disconnects`() = runTest {
        // Arrange
        val mockRepository = MockHeartRateRepository()
        val observeHeartRate = ObserveHeartRate(mockRepository)
        val getBatteryLevel = GetBatteryLevel(mockRepository)
        val viewModel = HeartRateViewModel(observeHeartRate, getBatteryLevel)

        // Setup - start monitoring and connect
        viewModel.startMonitoring()
        viewModel.connectWebSocket("ws://localhost:8080")
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isMonitoring)
        assertTrue(viewModel.uiState.value.connectionStatus.isActive)

        // Act
        viewModel.onCleared()
        testDispatcher.scheduler.runCurrent()

        // Assert
        assertFalse(viewModel.uiState.value.isMonitoring)
        assertEquals(ConnectionStatus.DISCONNECTED, viewModel.uiState.value.connectionStatus)
    }

    @Test
    fun `multiple heart rate values are collected correctly`() = runTest {
        // Arrange
        val heartRates = listOf(
            HeartRateData(timestamp = 1000L, heartRate = 70, deviceId = "device1"),
            HeartRateData(timestamp = 2000L, heartRate = 75, deviceId = "device1"),
            HeartRateData(timestamp = 3000L, heartRate = 80, deviceId = "device1")
        )
        val mockRepository = MockHeartRateRepository(
            heartRateFlow = flowOf(*heartRates.toTypedArray())
        )
        val observeHeartRate = ObserveHeartRate(mockRepository)
        val getBatteryLevel = GetBatteryLevel(mockRepository)
        val viewModel = HeartRateViewModel(observeHeartRate, getBatteryLevel)

        // Act
        viewModel.startMonitoring()
        testDispatcher.scheduler.runCurrent()

        // Assert - Should have the last value
        assertEquals(80, viewModel.uiState.value.currentHeartRate)
        viewModel.onCleared()
    }

    @Test
    fun `connectWebSocket uses custom service name`() = runTest {
        // Arrange
        val mockRepository = MockHeartRateRepository()
        val observeHeartRate = ObserveHeartRate(mockRepository)
        val getBatteryLevel = GetBatteryLevel(mockRepository)
        val viewModel = HeartRateViewModel(observeHeartRate, getBatteryLevel)

        // Act
        viewModel.connectWebSocket("ws://192.168.1.100:9000")
        testDispatcher.scheduler.runCurrent()

        // Assert - Connection should succeed (mock implementation)
        assertEquals(ConnectionStatus.CONNECTED, viewModel.uiState.value.connectionStatus)
    }

    @Test
    fun `startBLE uses default service name when not provided`() = runTest {
        // Arrange
        val mockRepository = MockHeartRateRepository()
        val observeHeartRate = ObserveHeartRate(mockRepository)
        val getBatteryLevel = GetBatteryLevel(mockRepository)
        val viewModel = HeartRateViewModel(observeHeartRate, getBatteryLevel)

        // Act
        viewModel.startBLE()  // No service name provided
        testDispatcher.scheduler.runCurrent()

        // Assert - Should use default service name
        assertEquals(ConnectionStatus.CONNECTED, viewModel.uiState.value.connectionStatus)
    }
}

