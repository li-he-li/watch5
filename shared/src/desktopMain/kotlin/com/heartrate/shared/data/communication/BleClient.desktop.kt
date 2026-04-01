package com.heartrate.shared.data.communication

import com.heartrate.shared.data.model.HeartRateData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.io.File

/**
 * Desktop BLE client implementation.
 *
 * Linux backend uses BlueZ CLI (`bluetoothctl` + `gatttool`).
 * Windows backend uses WinRT via PowerShell command bridge.
 */
actual class BleClient {
    private enum class Backend {
        LINUX_BLUEZ,
        WINDOWS_WINRT,
        UNSUPPORTED
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _heartRateFlow = MutableStateFlow<HeartRateData?>(null)
    private val _bleState = MutableStateFlow(BleState.IDLE)
    private val _isAdvertising = MutableStateFlow(false)
    private val _isConnected = MutableStateFlow(false)

    private val backend = detectBackend()
    private var scanJob: Job? = null
    private var pollJob: Job? = null
    private var connectedAddress: String? = null
    private val configuredWindowsTargetMac = normalizeMac(System.getenv(WINDOWS_TARGET_MAC_ENV))

    actual val heartRateDataFlow: Flow<HeartRateData>
        get() = _heartRateFlow.filterNotNull()

    actual suspend fun startAdvertising(serviceName: String): Result<Unit> {
        if (_isAdvertising.value) return Result.success(Unit)

        return when (backend) {
            Backend.LINUX_BLUEZ -> startLinuxBackend()
            Backend.WINDOWS_WINRT -> startWindowsBackend(serviceName)
            Backend.UNSUPPORTED -> {
                _bleState.value = BleState.ERROR
                Result.failure(
                    IllegalStateException(
                        "Desktop BLE fallback supports Linux (BlueZ) or Windows (PowerShell WinRT) only"
                    )
                )
            }
        }
    }

    private suspend fun startLinuxBackend(): Result<Unit> {
        val hasBluetoothCtl = commandExists("bluetoothctl")
        val hasGattTool = commandExists("gatttool")
        if (!hasBluetoothCtl || !hasGattTool) {
            _bleState.value = BleState.ERROR
            return Result.failure(
                IllegalStateException("Required commands missing on Linux: bluetoothctl/gatttool")
            )
        }

        _isAdvertising.value = true
        _bleState.value = BleState.SCANNING
        scanJob?.cancel()
        scanJob = scope.launch {
            while (isActive && !_isConnected.value) {
                val scanOutput = runLinuxCommand("bluetoothctl --timeout 6 scan on")
                val mac = parseFirstMacAddress(scanOutput)
                if (mac == null) {
                    delay(2_000)
                    continue
                }

                val connectOutput = runLinuxCommand("bluetoothctl --timeout 10 connect $mac")
                if (connectOutput.contains("successful", ignoreCase = true)) {
                    connectedAddress = mac
                    _isConnected.value = true
                    _bleState.value = BleState.CONNECTED
                    startPolling(mac)
                    break
                }
                delay(2_000)
            }

            if (!_isConnected.value && _isAdvertising.value) {
                _bleState.value = BleState.SCANNING
            }
        }
        return Result.success(Unit)
    }

    private suspend fun startWindowsBackend(serviceName: String): Result<Unit> {
        if (!isPowerShellAvailable()) {
            _bleState.value = BleState.ERROR
            return Result.failure(
                IllegalStateException("PowerShell not available for Windows BLE backend")
            )
        }
        if (!isWinRtBleAvailable()) {
            _bleState.value = BleState.ERROR
            return Result.failure(
                IllegalStateException("Windows WinRT BLE API is unavailable")
            )
        }

        _isAdvertising.value = true
        _bleState.value = BleState.SCANNING
        log("windows backend started, serviceName='$serviceName', fixedMac=${configuredWindowsTargetMac ?: "none"}")
        scanJob?.cancel()
        scanJob = scope.launch {
            var transientFailures = 0
            while (isActive && _isAdvertising.value) {
                val mac = connectedAddress
                    ?: configuredWindowsTargetMac
                    ?: discoverWindowsTargetMac(serviceName)

                if (mac.isNullOrBlank()) {
                    _isConnected.value = false
                    _bleState.value = BleState.SCANNING
                    log("scan miss: no target MAC discovered")
                    delay(WINDOWS_SCAN_RETRY_MS)
                    continue
                }
                connectedAddress = mac

                val readResult = readHeartRateOnWindows(mac)
                if (readResult.bpm != null) {
                    transientFailures = 0
                    _isConnected.value = true
                    _bleState.value = BleState.CONNECTED
                    log("read success: mac=$mac bpm=${readResult.bpm}")
                    _heartRateFlow.value = HeartRateData(
                        timestamp = System.currentTimeMillis(),
                        heartRate = readResult.bpm.coerceAtLeast(1),
                        deviceId = "ble:$mac",
                        batteryLevel = null,
                        signalQuality = null
                    )
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                transientFailures += 1
                _isConnected.value = false
                _bleState.value = BleState.SCANNING
                log(
                    "read failed: mac=$mac reason=${readResult.reason ?: "unknown"} failures=$transientFailures"
                )
                if (configuredWindowsTargetMac == null && transientFailures >= WINDOWS_RESET_SCAN_THRESHOLD) {
                    log("resetting target MAC after repeated failures")
                    connectedAddress = null
                    transientFailures = 0
                }
                delay(WINDOWS_SCAN_RETRY_MS)
            }

            if (!_isConnected.value && _isAdvertising.value) {
                _bleState.value = BleState.SCANNING
            }
        }
        return Result.success(Unit)
    }

    actual suspend fun stopAdvertising() {
        _isAdvertising.value = false
        _isConnected.value = false
        _bleState.value = BleState.IDLE

        scanJob?.cancelAndJoin()
        scanJob = null
        pollJob?.cancelAndJoin()
        pollJob = null

        if (backend == Backend.LINUX_BLUEZ) {
            connectedAddress?.let { runLinuxCommand("bluetoothctl --timeout 5 disconnect $it") }
        }
        connectedAddress = null
    }

    actual suspend fun sendHeartRateData(data: HeartRateData): Result<Unit> {
        return Result.failure(
            UnsupportedOperationException("Desktop BLE client is receive-only")
        )
    }

    actual val isAdvertising: Boolean
        get() = _isAdvertising.value

    actual val isConnected: Boolean
        get() = _isConnected.value

    actual val bleState: BleState
        get() = _bleState.value

    private fun startPolling(mac: String) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && _isConnected.value && _isAdvertising.value) {
                val output = runLinuxCommand("gatttool -b $mac --char-read --uuid=0x2a37")
                val bytes = parseMeasurementBytes(output)
                val bpm = bytes?.let(::decodeHeartRateMeasurement)
                if (bpm != null) {
                    _heartRateFlow.value = HeartRateData(
                        timestamp = System.currentTimeMillis(),
                        heartRate = bpm.coerceAtLeast(1),
                        deviceId = "ble:$mac",
                        batteryLevel = null,
                        signalQuality = null
                    )
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun commandExists(command: String): Boolean {
        val output = runLinuxCommand("command -v $command")
        return output.isNotBlank()
    }

    private suspend fun runLinuxCommand(command: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val process = ProcessBuilder("bash", "-lc", command)
                .redirectErrorStream(true)
                .start()
            val out = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            out
        }.getOrElse { "" }
    }

    private suspend fun runPowerShellScript(
        script: String,
        timeoutMs: Long = WINDOWS_COMMAND_TIMEOUT_MS
    ): String = withContext(Dispatchers.IO) {
        runCatching {
            val process = ProcessBuilder(
                "powershell",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                script
            )
                .redirectErrorStream(true)
                .start()
            val out = process.inputStream.bufferedReader().use { reader ->
                if (process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                    reader.readText()
                } else {
                    process.destroyForcibly()
                    ""
                }
            }
            out
        }.getOrElse { "" }
    }

    private suspend fun runPowerShellScriptFile(
        scriptFileName: String,
        script: String,
        timeoutMs: Long = WINDOWS_COMMAND_TIMEOUT_MS
    ): String = withContext(Dispatchers.IO) {
        runCatching {
            val tmpRoot = System.getProperty("java.io.tmpdir").orEmpty().ifBlank { ".tmp" }
            val tmpDir = File(tmpRoot)
            if (!tmpDir.exists()) tmpDir.mkdirs()
            val scriptFile = File(tmpDir, scriptFileName)
            scriptFile.writeText(script)

            val process = ProcessBuilder(
                "powershell",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                scriptFile.absolutePath
            )
                .redirectErrorStream(true)
                .start()
            val out = process.inputStream.bufferedReader().use { reader ->
                if (process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                    reader.readText()
                } else {
                    process.destroyForcibly()
                    ""
                }
            }
            out
        }.getOrElse { "" }
    }

    private suspend fun isPowerShellAvailable(): Boolean {
        val output = runPowerShellScript("\$PSVersionTable.PSVersion.ToString()", 4_000)
        return output.trim().isNotBlank()
    }

    private suspend fun isWinRtBleAvailable(): Boolean {
        val script = ps(
            """
            __D__ErrorActionPreference='Stop'
            [void][Windows.Devices.Bluetooth.BluetoothLEDevice,Windows.Devices.Bluetooth,ContentType=WindowsRuntime]
            Write-Output 'OK'
            """
        )
        return runPowerShellScript(script, 4_000).contains("OK")
    }

    private suspend fun discoverWindowsTargetMac(serviceName: String): String? {
        val normalizedFilter = serviceName.filterNot(Char::isWhitespace)
        val escapedFilter = normalizedFilter.replace("'", "''")
        val script = ps(
            """
            __D__ErrorActionPreference='Stop'
            [void][Windows.Devices.Bluetooth.Advertisement.BluetoothLEAdvertisementWatcher,Windows.Devices.Bluetooth,ContentType=WindowsRuntime]
            [void][Windows.Devices.Bluetooth.BluetoothUuidHelper,Windows.Devices.Bluetooth,ContentType=WindowsRuntime]
            __D__script:targetService=[Windows.Devices.Bluetooth.BluetoothUuidHelper]::FromShortId(0x180D)
            __D__script:nameFilter='$escapedFilter'
            __D__script:hit=''
            __D__watcher=New-Object Windows.Devices.Bluetooth.Advertisement.BluetoothLEAdvertisementWatcher
            __D__watcher.ScanningMode=[Windows.Devices.Bluetooth.Advertisement.BluetoothLEScanningMode]::Active
            __D__handler=[Windows.Foundation.TypedEventHandler[Windows.Devices.Bluetooth.Advertisement.BluetoothLEAdvertisementWatcher,Windows.Devices.Bluetooth.Advertisement.BluetoothLEAdvertisementReceivedEventArgs]]{
                param(__D__sender,__D__args)
                __D__hasService=__D__false
                if(__D__args.Advertisement.ServiceUuids){
                    __D__hasService=__D__args.Advertisement.ServiceUuids.Contains(__D__script:targetService)
                }
                if(-not __D__hasService){ return }
                if(__D__script:nameFilter){
                    __D__localName=(__D__args.Advertisement.LocalName -replace '\s','')
                    if(__D__localName -and (__D__localName -notlike "*__D__(__D__script:nameFilter)*")){ return }
                }
                __D__hex='{0:X12}' -f __D__args.BluetoothAddress
                __D__script:hit=(__D__hex -replace '(.{2})(?=.)','__D__1:')
            }
            __D__token=__D__watcher.add_Received(__D__handler)
            __D__watcher.Start()
            __D__deadline=(Get-Date).AddSeconds($WINDOWS_SCAN_SECONDS)
            while((-not __D__script:hit) -and ((Get-Date) -lt __D__deadline)){
                Start-Sleep -Milliseconds 200
            }
            __D__watcher.Stop()
            __D__watcher.remove_Received(__D__token)
            if(__D__script:hit){ Write-Output __D__script:hit } else { Write-Output '' }
            """
        )

        val output = runPowerShellScript(script, WINDOWS_SCAN_COMMAND_TIMEOUT_MS)
        val activeScanMac = output.lineSequence()
            .map { it.trim() }
            .firstOrNull { normalizeMac(it) != null }
            ?.let(::normalizeMac)
        if (activeScanMac != null) {
            log("scan hit via watcher: mac=$activeScanMac")
            return activeScanMac
        }

        log("watcher scan produced no MAC, fallback to known devices")
        return discoverWindowsKnownMacByName(escapedFilter)
    }

    private suspend fun discoverWindowsKnownMacByName(escapedFilter: String): String? {
        val script = ps(
            """
            __D__ErrorActionPreference='Stop'
            Add-Type -AssemblyName System.Runtime.WindowsRuntime
            [void][Windows.Devices.Bluetooth.BluetoothLEDevice,Windows.Devices.Bluetooth,ContentType=WindowsRuntime]
            [void][Windows.Devices.Enumeration.DeviceInformation,Windows.Devices.Enumeration,ContentType=WindowsRuntime]

            function Convert-ToTask([object]__D__asyncOperation, [Type]__D__resultType) {
                __D__allMethods=[System.WindowsRuntimeSystemExtensions].GetMethods()
                __D__method=__D__null
                foreach(__D__m in __D__allMethods){
                    if(__D__m.Name -ne 'AsTask'){ continue }
                    if(-not __D__m.IsGenericMethodDefinition){ continue }
                    if(__D__m.GetParameters().Count -ne 1){ continue }
                    __D__method=__D__m
                    break
                }
                if(-not __D__method){
                    throw 'ERR|AS_TASK_METHOD'
                }
                __D__genericMethod=__D__method.MakeGenericMethod(__D__resultType)
                __D__genericMethod.Invoke(__D__null, @(__D__asyncOperation))
            }

            function Await-Result([object]__D__asyncOperation, [Type]__D__resultType, [int]__D__timeoutMs) {
                __D__task=Convert-ToTask __D__asyncOperation __D__resultType
                if(-not __D__task.Wait(__D__timeoutMs)){ return __D__null }
                __D__task.Result
            }

            __D__nameFilter='$escapedFilter'
            __D__selector=[Windows.Devices.Bluetooth.BluetoothLEDevice]::GetDeviceSelector()
            __D__devices=Await-Result ([Windows.Devices.Enumeration.DeviceInformation]::FindAllAsync(__D__selector)) ([Windows.Devices.Enumeration.DeviceInformationCollection]) 8000
            if(-not __D__devices){ Write-Output ''; exit 0 }

            foreach(__D__info in __D__devices){
                if(__D__nameFilter){
                    __D__localName=(__D__info.Name -replace '\s','')
                    if(-not __D__localName){ continue }
                    if(__D__localName -notlike "*__D__(__D__nameFilter)*"){ continue }
                }

                __D__id=__D__info.Id
                if(-not __D__id){ continue }
                __D__candidate=(__D__id -split '-')[-1]
                __D__hex=(__D__candidate -replace ':','')
                if(__D__hex -match '^[0-9A-Fa-f]{12}$'){
                    __D__normalized=(__D__hex.ToUpperInvariant() -replace '(.{2})(?=.)','__D__1:')
                    Write-Output __D__normalized
                    exit 0
                }
            }

            Write-Output ''
            """
        )
        val output = runPowerShellScript(script, WINDOWS_COMMAND_TIMEOUT_MS)
        val mac = output.lineSequence()
            .map { it.trim() }
            .firstOrNull { normalizeMac(it) != null }
            ?.let(::normalizeMac)
        if (mac != null) {
            log("scan hit via known devices: mac=$mac")
        } else {
            log("known devices fallback produced no MAC")
        }
        return mac
    }

    private suspend fun readHeartRateOnWindows(mac: String): WindowsReadResult {
        val escapedMac = mac.replace("'", "''")
        val script = ps(
            """
            __D__ErrorActionPreference='Stop'
            Add-Type -AssemblyName System.Runtime.WindowsRuntime
            [void][Windows.Devices.Bluetooth.BluetoothLEDevice,Windows.Devices.Bluetooth,ContentType=WindowsRuntime]
            [void][Windows.Devices.Bluetooth.BluetoothUuidHelper,Windows.Devices.Bluetooth,ContentType=WindowsRuntime]
            [void][Windows.Devices.Bluetooth.GenericAttributeProfile.GattDeviceService,Windows.Devices.Bluetooth,ContentType=WindowsRuntime]
            [void][Windows.Devices.Bluetooth.GenericAttributeProfile.GattCharacteristic,Windows.Devices.Bluetooth,ContentType=WindowsRuntime]
            [void][Windows.Devices.Bluetooth.GenericAttributeProfile.GattDeviceServicesResult,Windows.Devices.Bluetooth,ContentType=WindowsRuntime]
            [void][Windows.Devices.Bluetooth.GenericAttributeProfile.GattCharacteristicsResult,Windows.Devices.Bluetooth,ContentType=WindowsRuntime]
            [void][Windows.Devices.Bluetooth.GenericAttributeProfile.GattReadResult,Windows.Devices.Bluetooth,ContentType=WindowsRuntime]
            [void][Windows.Storage.Streams.IBuffer,Windows.Storage.Streams,ContentType=WindowsRuntime]

            function Convert-ToTask([object]__D__asyncOperation, [Type]__D__resultType) {
                __D__allMethods=[System.WindowsRuntimeSystemExtensions].GetMethods()
                __D__method=__D__null
                foreach(__D__m in __D__allMethods){
                    if(__D__m.Name -ne 'AsTask'){ continue }
                    if(-not __D__m.IsGenericMethodDefinition){ continue }
                    if(__D__m.GetParameters().Count -ne 1){ continue }
                    __D__method=__D__m
                    break
                }
                if(-not __D__method){
                    throw 'ERR|AS_TASK_METHOD'
                }
                __D__genericMethod=__D__method.MakeGenericMethod(__D__resultType)
                __D__genericMethod.Invoke(__D__null, @(__D__asyncOperation))
            }

            function Await-Result([object]__D__asyncOperation, [Type]__D__resultType, [int]__D__timeoutMs) {
                __D__task=Convert-ToTask __D__asyncOperation __D__resultType
                if(-not __D__task.Wait(__D__timeoutMs)){ return __D__null }
                __D__task.Result
            }

            try {
                __D__addressString='$escapedMac'
                __D__address=[UInt64]::Parse((__D__addressString -replace ':',''), [System.Globalization.NumberStyles]::HexNumber)
                __D__device=Await-Result ([Windows.Devices.Bluetooth.BluetoothLEDevice]::FromBluetoothAddressAsync(__D__address)) ([Windows.Devices.Bluetooth.BluetoothLEDevice]) 7000
                if(-not __D__device){ Write-Output 'ERR|DEVICE'; exit 0 }

                __D__serviceUuid=[Windows.Devices.Bluetooth.BluetoothUuidHelper]::FromShortId(0x180D)
                __D__characteristicUuid=[Windows.Devices.Bluetooth.BluetoothUuidHelper]::FromShortId(0x2A37)
                __D__statusType=[Windows.Devices.Bluetooth.GenericAttributeProfile.GattCommunicationStatus]

                __D__servicesResult=Await-Result (__D__device.GetGattServicesForUuidAsync(__D__serviceUuid,[Windows.Devices.Bluetooth.BluetoothCacheMode]::Uncached)) ([Windows.Devices.Bluetooth.GenericAttributeProfile.GattDeviceServicesResult]) 7000
                if((-not __D__servicesResult) -or (__D__servicesResult.Status -ne __D__statusType::Success) -or (__D__servicesResult.Services.Count -eq 0)){
                    Write-Output 'ERR|SERVICE'
                    __D__device.Dispose()
                    exit 0
                }

                __D__service=__D__servicesResult.Services[0]
                __D__characteristicsResult=__D__null
                try {
                    __D__characteristicsResult=Await-Result (__D__service.GetCharacteristicsForUuidAsync(__D__characteristicUuid,[Windows.Devices.Bluetooth.BluetoothCacheMode]::Uncached)) ([Windows.Devices.Bluetooth.GenericAttributeProfile.GattCharacteristicsResult]) 7000
                } catch {
                    __D__characteristicsResult=Await-Result (__D__service.GetCharacteristicsAsync([Windows.Devices.Bluetooth.BluetoothCacheMode]::Uncached)) ([Windows.Devices.Bluetooth.GenericAttributeProfile.GattCharacteristicsResult]) 7000
                }
                if((-not __D__characteristicsResult) -or (__D__characteristicsResult.Status -ne __D__statusType::Success) -or (__D__characteristicsResult.Characteristics.Count -eq 0)){
                    Write-Output 'ERR|CHAR'
                    __D__service.Dispose()
                    __D__device.Dispose()
                    exit 0
                }

                __D__characteristic=__D__null
                foreach(__D__c in __D__characteristicsResult.Characteristics){
                    if(__D__c.Uuid -eq __D__characteristicUuid){
                        __D__characteristic=__D__c
                        break
                    }
                }
                if(-not __D__characteristic){
                    Write-Output 'ERR|CHAR_UUID'
                    __D__service.Dispose()
                    __D__device.Dispose()
                    exit 0
                }
                __D__readResult=Await-Result (__D__characteristic.ReadValueAsync([Windows.Devices.Bluetooth.BluetoothCacheMode]::Uncached)) ([Windows.Devices.Bluetooth.GenericAttributeProfile.GattReadResult]) 7000
                if((-not __D__readResult) -or (__D__readResult.Status -ne __D__statusType::Success) -or (-not __D__readResult.Value)){
                    Write-Output 'ERR|READ'
                    __D__service.Dispose()
                    __D__device.Dispose()
                    exit 0
                }

                __D__toArrayMethod=[System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBufferExtensions].GetMethod(
                    'ToArray',
                    [Type[]]@([Windows.Storage.Streams.IBuffer])
                )
                if(-not __D__toArrayMethod){
                    Write-Output 'ERR|BUFFER_METHOD'
                    __D__service.Dispose()
                    __D__device.Dispose()
                    exit 0
                }
                try {
                    __D__bytes=__D__toArrayMethod.Invoke(__D__null, @(__D__readResult.Value))
                } catch {
                    Write-Output 'ERR|BUFFER'
                    __D__service.Dispose()
                    __D__device.Dispose()
                    exit 0
                }
                if(__D__bytes.Length -lt 2){
                    Write-Output 'ERR|PAYLOAD'
                    __D__service.Dispose()
                    __D__device.Dispose()
                    exit 0
                }

                __D__flags=[int]__D__bytes[0]
                if((__D__flags -band 0x01) -eq 0){
                    __D__bpm=[int]__D__bytes[1]
                } else {
                    if(__D__bytes.Length -lt 3){
                        Write-Output 'ERR|PAYLOAD16'
                        __D__service.Dispose()
                        __D__device.Dispose()
                        exit 0
                    }
                    __D__bpm=((([int]__D__bytes[2]) -shl 8) -bor ([int]__D__bytes[1]))
                }

                Write-Output ("OK|{0}" -f __D__bpm)
                __D__service.Dispose()
                __D__device.Dispose()
            } catch {
                Write-Output ("ERR|EXCEPTION|{0}" -f __D___.Exception.Message)
            }
            """
        )

        val output = runPowerShellScriptFile("desktop_ble_read_script.ps1", script, WINDOWS_COMMAND_TIMEOUT_MS)
        val lines = output.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        val okToken = lines.firstOrNull { it.startsWith("OK|") }
        if (okToken != null) {
            val bpm = okToken.substringAfter("OK|").toIntOrNull()
            if (bpm != null) return WindowsReadResult(bpm = bpm)
            return WindowsReadResult(reason = "OK token parse failed: $okToken")
        }
        val errToken = lines.firstOrNull { it.startsWith("ERR|") }
        if (errToken != null) return WindowsReadResult(reason = errToken)
        val fallback = lines.takeLast(2).joinToString(" | ").ifBlank { "no_output" }
        return WindowsReadResult(reason = "NO_TOKEN|$fallback")
    }

    private fun parseFirstMacAddress(output: String): String? {
        val pattern = Pattern.compile("([0-9A-F]{2}(:[0-9A-F]{2}){5})")
        val matcher = pattern.matcher(output.uppercase(Locale.US))
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun parseMeasurementBytes(output: String): ByteArray? {
        val hexMatch = Regex("([0-9a-fA-F]{2}(\\s+[0-9a-fA-F]{2})+)")
            .find(output)
            ?.value
            ?: return null

        val bytes = hexMatch.trim().split(Regex("\\s+"))
            .mapNotNull { token -> token.toIntOrNull(radix = 16)?.toByte() }
        return if (bytes.size >= 2) bytes.toByteArray() else null
    }

    private fun decodeHeartRateMeasurement(payload: ByteArray): Int? {
        if (payload.size < 2) return null
        val flags = payload[0].toInt() and 0xFF
        return if ((flags and 0x01) == 0) {
            payload[1].toInt() and 0xFF
        } else {
            if (payload.size < 3) return null
            val low = payload[1].toInt() and 0xFF
            val high = payload[2].toInt() and 0xFF
            (high shl 8) or low
        }
    }

    private fun detectBackend(): Backend {
        return when {
            isLinux() -> Backend.LINUX_BLUEZ
            isWindows() -> Backend.WINDOWS_WINRT
            else -> Backend.UNSUPPORTED
        }
    }

    private fun isWindows(): Boolean {
        val osName = System.getProperty("os.name").orEmpty().lowercase(Locale.US)
        return osName.contains("windows")
    }

    private fun isLinux(): Boolean {
        val osName = System.getProperty("os.name").orEmpty().lowercase(Locale.US)
        return osName.contains("linux")
    }

    private fun normalizeMac(raw: String?): String? {
        val compact = raw
            ?.trim()
            ?.uppercase(Locale.US)
            ?.replace("-", "")
            ?.replace(":", "")
            ?: return null
        if (!compact.matches(Regex("^[0-9A-F]{12}$"))) return null
        return compact.chunked(2).joinToString(":")
    }

    private fun ps(script: String): String = script.trimIndent().replace("__D__", "$")

    private fun log(message: String) {
        val line = "DESKTOP_BLE ${System.currentTimeMillis()} $message"
        println(line)
        runCatching {
            val dirPath = System.getProperty("java.io.tmpdir").orEmpty()
            if (dirPath.isBlank()) return@runCatching
            val dir = File(dirPath)
            if (!dir.exists()) dir.mkdirs()
            File(dir, "desktop_ble_trace.log").appendText("$line\n")
        }
    }

    private data class WindowsReadResult(
        val bpm: Int? = null,
        val reason: String? = null
    )

    companion object {
        private const val POLL_INTERVAL_MS = 1_000L
        private const val WINDOWS_SCAN_SECONDS = 10
        private const val WINDOWS_SCAN_RETRY_MS = 2_000L
        private const val WINDOWS_RESET_SCAN_THRESHOLD = 3
        private const val WINDOWS_COMMAND_TIMEOUT_MS = 20_000L
        private const val WINDOWS_SCAN_COMMAND_TIMEOUT_MS = 25_000L
        private const val WINDOWS_TARGET_MAC_ENV = "HRM_BLE_TARGET_MAC"
    }
}
