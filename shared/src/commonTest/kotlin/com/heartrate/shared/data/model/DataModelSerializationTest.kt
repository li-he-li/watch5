package com.heartrate.shared.data.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for data model serialization and validation.
 */
class DataModelSerializationTest {

    private val json = Json { prettyPrint = true }

    @Test
    fun `HeartRateData serializes to JSON correctly`() {
        val data = HeartRateData(
            timestamp = 1234567890L,
            heartRate = 72,
            deviceId = "test-device-123",
            batteryLevel = 85,
            signalQuality = 95
        )

        val jsonString = json.encodeToString(HeartRateData.serializer(), data)

        // Verify the JSON contains the expected values (flexible matching for spacing)
        assertTrue(jsonString.contains("timestamp"), "JSON should contain timestamp field")
        assertTrue(jsonString.contains("1234567890"), "JSON should contain timestamp value")
        assertTrue(jsonString.contains("heartRate"), "JSON should contain heartRate field")
        assertTrue(jsonString.contains("72"), "JSON should contain heartRate value")
        assertTrue(jsonString.contains("deviceId"), "JSON should contain deviceId field")
        assertTrue(jsonString.contains("test-device-123"), "JSON should contain deviceId value")
    }

    @Test
    fun `HeartRateData deserializes from JSON correctly`() {
        val jsonString = """
            {
                "timestamp": 1234567890,
                "heartRate": 72,
                "deviceId": "test-device-123",
                "batteryLevel": 85,
                "signalQuality": 95
            }
        """.trimIndent()

        val data = json.decodeFromString(HeartRateData.serializer(), jsonString)

        assertEquals(1234567890L, data.timestamp)
        assertEquals(72, data.heartRate)
        assertEquals("test-device-123", data.deviceId)
        assertEquals(85, data.batteryLevel)
        assertEquals(95, data.signalQuality)
    }

    @Test
    fun `HeartRateData with null optional fields serializes correctly`() {
        val data = HeartRateData(
            timestamp = 1234567890L,
            heartRate = 72,
            deviceId = "test-device-123"
        )

        val jsonString = json.encodeToString(HeartRateData.serializer(), data)
        val deserialized = json.decodeFromString(HeartRateData.serializer(), jsonString)

        assertEquals(data, deserialized)
        assertEquals(null, deserialized.batteryLevel)
        assertEquals(null, deserialized.signalQuality)
    }

    @Test
    fun `HeartRateData validates heart rate is positive`() {
        assertFailsWith<IllegalArgumentException> {
            HeartRateData(
                timestamp = 1234567890L,
                heartRate = 0,
                deviceId = "test-device"
            )
        }
    }

    @Test
    fun `HeartRateData validates heart rate maximum`() {
        assertFailsWith<IllegalArgumentException> {
            HeartRateData(
                timestamp = 1234567890L,
                heartRate = 251,
                deviceId = "test-device"
            )
        }
    }

    @Test
    fun `DeviceInfo serializes to JSON correctly`() {
        val deviceInfo = DeviceInfo(
            deviceId = "watch-123",
            deviceType = DeviceType.WATCH,
            deviceName = "Galaxy Watch 5",
            osVersion = "Wear OS 4.0",
            appVersion = "1.0.0",
            batteryLevel = 75,
            isCharging = true
        )

        val jsonString = json.encodeToString(DeviceInfo.serializer(), deviceInfo)
        val deserialized = json.decodeFromString(DeviceInfo.serializer(), jsonString)

        assertEquals(deviceInfo, deserialized)
        assertEquals(DeviceType.WATCH, deserialized.deviceType)
        assertTrue(deserialized.isCharging)
    }

    @Test
    fun `SensorReading serializes to JSON correctly`() {
        val reading = SensorReading(
            timestamp = 1234567890L,
            value = 0.85f,
            accuracy = SensorAccuracy.HIGH,
            deviceId = "test-device"
        )

        val jsonString = json.encodeToString(SensorReading.serializer(), reading)
        val deserialized = json.decodeFromString(SensorReading.serializer(), jsonString)

        assertEquals(reading, deserialized)
        assertEquals(SensorAccuracy.HIGH, deserialized.accuracy)
        assertEquals(0.85f, deserialized.value)
    }

    @Test
    fun `SensorAccuracy enum serializes correctly`() {
        val accuracies = listOf(
            SensorAccuracy.UNRELIABLE,
            SensorAccuracy.LOW,
            SensorAccuracy.MEDIUM,
            SensorAccuracy.HIGH
        )

        accuracies.forEach { accuracy ->
            val jsonString = json.encodeToString(SensorAccuracy.serializer(), accuracy)
            val deserialized = json.decodeFromString(SensorAccuracy.serializer(), jsonString)

            assertEquals(accuracy, deserialized)
        }
    }

    @Test
    fun `HeartRateData validates battery level range`() {
        assertFailsWith<IllegalArgumentException> {
            HeartRateData(
                timestamp = 1234567890L,
                heartRate = 72,
                deviceId = "test-device",
                batteryLevel = 101
            )
        }
    }

    @Test
    fun `HeartRateData validates signal quality range`() {
        assertFailsWith<IllegalArgumentException> {
            HeartRateData(
                timestamp = 1234567890L,
                heartRate = 72,
                deviceId = "test-device",
                signalQuality = -1
            )
        }
    }
}
