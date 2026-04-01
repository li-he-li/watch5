package com.heartrate.phone.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.heartrate.shared.data.model.HeartRateData
import java.util.Collections
import java.util.UUID

/**
 * Phone-side BLE GATT server exposing Heart Rate service as fallback transport.
 */
class PhoneBleGattServer(
    private val appContext: Context
) {
    private val connectedDevices = Collections.synchronizedSet(mutableSetOf<BluetoothDevice>())
    private val subscribedDevices = Collections.synchronizedSet(mutableSetOf<BluetoothDevice>())

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private var measurementCharacteristic: BluetoothGattCharacteristic? = null
    private var latestMeasurementPayload: ByteArray = encodeHeartRateMeasurement(0)
    private var advertising = false
    private var running = false

    @Synchronized
    @SuppressLint("MissingPermission")
    fun start(serverName: String = DEFAULT_SERVER_NAME): Result<Unit> {
        if (running) return Result.success(Unit)
        if (!hasRequiredPermissions()) {
            return Result.failure(IllegalStateException("BLE permissions missing"))
        }

        val manager = appContext.getSystemService(BluetoothManager::class.java)
            ?: return Result.failure(IllegalStateException("BluetoothManager unavailable"))
        val adapter = manager.adapter
            ?: return Result.failure(IllegalStateException("Bluetooth adapter unavailable"))
        if (!adapter.isEnabled) {
            return Result.failure(IllegalStateException("Bluetooth is disabled"))
        }

        bluetoothManager = manager
        bluetoothAdapter = adapter
        advertiser = adapter.bluetoothLeAdvertiser
            ?: return Result.failure(IllegalStateException("BLE advertising unavailable"))

        runCatching {
            if (adapter.name != serverName) {
                adapter.name = serverName
            }
        }.onFailure {
            Log.w(TAG, "unable to set bluetooth name", it)
        }

        val server = manager.openGattServer(appContext, gattServerCallback)
            ?: return Result.failure(IllegalStateException("Failed to open GATT server"))
        gattServer = server

        val service = BluetoothGattService(
            HEART_RATE_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        val characteristic = BluetoothGattCharacteristic(
            HEART_RATE_MEASUREMENT_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        @Suppress("DEPRECATION")
        runCatching { characteristic.value = latestMeasurementPayload }
        val descriptor = BluetoothGattDescriptor(
            CLIENT_CONFIG_DESCRIPTOR_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        characteristic.addDescriptor(descriptor)
        service.addCharacteristic(characteristic)
        server.addService(service)
        measurementCharacteristic = characteristic

        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
            .build()

        return runCatching {
            advertiser?.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
            advertising = true
            running = true
            Log.i(TAG, "BLE GATT server started")
            Unit
        }.onFailure { error ->
            Log.e(TAG, "failed to start BLE GATT server", error)
            stop()
        }
    }

    @Synchronized
    @SuppressLint("MissingPermission")
    fun stop() {
        runCatching { advertiser?.stopAdvertising(advertiseCallback) }
        runCatching { gattServer?.close() }

        advertiser = null
        gattServer = null
        measurementCharacteristic = null
        connectedDevices.clear()
        subscribedDevices.clear()
        advertising = false
        running = false
        Log.i(TAG, "BLE GATT server stopped")
    }

    @Synchronized
    @SuppressLint("MissingPermission")
    fun sendHeartRate(data: HeartRateData): Result<Unit> {
        if (!running) {
            return Result.failure(IllegalStateException("BLE GATT server not started"))
        }
        if (!hasRequiredPermissions()) {
            return Result.failure(IllegalStateException("BLE permissions missing"))
        }

        val characteristic = measurementCharacteristic
            ?: return Result.failure(IllegalStateException("Measurement characteristic unavailable"))
        val server = gattServer
            ?: return Result.failure(IllegalStateException("GATT server unavailable"))
        val payload = encodeHeartRateMeasurement(data.heartRate)
        setLatestMeasurementPayload(payload)
        if (subscribedDevices.isEmpty()) {
            return Result.failure(IllegalStateException("No subscribed BLE clients"))
        }

        val failures = mutableListOf<String>()

        subscribedDevices.toList().forEach { device ->
            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val status = server.notifyCharacteristicChanged(device, characteristic, false, payload)
                status == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = payload
                @Suppress("DEPRECATION")
                server.notifyCharacteristicChanged(device, characteristic, false)
            }
            if (!success) {
                failures += device.address
            }
        }

        if (failures.isNotEmpty()) {
            return Result.failure(IllegalStateException("notify failed for ${failures.joinToString()}"))
        }

        return Result.success(Unit)
    }

    val isRunning: Boolean
        get() = running

    val isAdvertising: Boolean
        get() = advertising

    val connectedClientCount: Int
        get() = connectedDevices.size

    private fun hasRequiredPermissions(): Boolean {
        val connectGranted = ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        val advertiseGranted = ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.BLUETOOTH_ADVERTISE
        ) == PackageManager.PERMISSION_GRANTED
        return connectGranted && advertiseGranted
    }

    private fun encodeHeartRateMeasurement(heartRate: Int): ByteArray {
        return if (heartRate in 0..255) {
            byteArrayOf(0x00, heartRate.toByte())
        } else {
            val low = (heartRate and 0xFF).toByte()
            val high = ((heartRate shr 8) and 0xFF).toByte()
            byteArrayOf(0x01, low, high)
        }
    }

    @Suppress("DEPRECATION")
    private fun setLatestMeasurementPayload(payload: ByteArray) {
        latestMeasurementPayload = payload
        measurementCharacteristic?.value = payload
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            advertising = true
            Log.i(TAG, "BLE advertising started")
        }

        override fun onStartFailure(errorCode: Int) {
            advertising = false
            Log.e(TAG, "BLE advertising failed code=$errorCode")
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    connectedDevices += device
                    Log.i(TAG, "device connected=${device.address}")
                }

                BluetoothGatt.STATE_DISCONNECTED -> {
                    connectedDevices -= device
                    subscribedDevices -= device
                    Log.i(TAG, "device disconnected=${device.address}")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            val server = gattServer ?: return
            if (characteristic.uuid != HEART_RATE_MEASUREMENT_CHAR_UUID) {
                server.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                return
            }
            val value = latestMeasurementPayload
            server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (descriptor.uuid == CLIENT_CONFIG_DESCRIPTOR_UUID) {
                when {
                    value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) -> {
                        subscribedDevices += device
                    }

                    value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE) -> {
                        subscribedDevices -= device
                    }
                }
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            val value = if (subscribedDevices.contains(device)) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            }
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
        }
    }

    companion object {
        private const val TAG = "PhoneBleGattServer"
        private const val DEFAULT_SERVER_NAME = "HeartRate Monitor"

        private val HEART_RATE_SERVICE_UUID: UUID =
            UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
        private val HEART_RATE_MEASUREMENT_CHAR_UUID: UUID =
            UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")
        private val CLIENT_CONFIG_DESCRIPTOR_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }
}
