package com.heartrate.phone.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.heartrate.phone.data.PhoneBleRelayController
import com.heartrate.phone.data.PhoneWebSocketRelayController
import com.heartrate.phone.data.persistence.HeartRateDao
import com.heartrate.phone.data.persistence.HeartRateExportManager
import com.heartrate.phone.data.persistence.HeartRateExportMetadata
import com.heartrate.phone.service.PhoneRelayForegroundService
import com.heartrate.shared.presentation.viewmodel.HeartRateViewModel
import kotlinx.coroutines.launch

private enum class PhoneRoute(val title: String) {
    HEALTH("健康"),
    DEVICE("设备"),
    ME("我的")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneApp(
    viewModel: HeartRateViewModel,
    bleRelayController: PhoneBleRelayController,
    webSocketRelayController: PhoneWebSocketRelayController,
    heartRateDao: HeartRateDao,
    exportManager: HeartRateExportManager
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val requiredBlePermissions = remember { bleTransferPermissions() }
    val clipboardManager = remember { context.getSystemService(ClipboardManager::class.java) }
    val connectivityManager = remember { context.getSystemService(ConnectivityManager::class.java) }
    val uiState by viewModel.uiState.collectAsState()
    val allRecords by heartRateDao.observeAll().collectAsState(initial = emptyList())
    val exportHistory = remember { mutableStateListOf<HeartRateExportMetadata>() }
    var selectedRoute by remember { mutableStateOf(PhoneRoute.HEALTH) }
    var wsRelayEnabled by remember { mutableStateOf(false) }
    var bleRelayEnabled by remember { mutableStateOf(false) }
    var lanIpv4 by remember { mutableStateOf<String?>(null) }
    var wsEndpoint by remember { mutableStateOf<String?>(null) }
    var batteryOptimizationIgnored by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var batteryPromptTriggered by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var selectedChartRange by remember { mutableIntStateOf(60) }
    val refreshWsDetailsState by rememberUpdatedState(
        newValue = {
            lanIpv4 = webSocketRelayController.getCurrentLanIpv4Address()
            wsEndpoint = webSocketRelayController.getCurrentWebSocketEndpoint()
        }
    )
    val requestBlePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val denied = requiredBlePermissions.filter { grants[it] != true }
        bleRelayEnabled = if (denied.isEmpty()) {
            bleRelayController.startBleRelay().isSuccess
        } else {
            false
        }
    }
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        batteryOptimizationIgnored = isIgnoringBatteryOptimizations(context)
    }

    fun refreshExportHistory() {
        coroutineScope.launch {
            exportHistory.clear()
            exportHistory.addAll(exportManager.listExportHistory())
        }
    }

    LaunchedEffect(Unit) {
        PhoneRelayForegroundService.start(context)
        viewModel.startMonitoring()
        wsRelayEnabled = webSocketRelayController.isWebSocketRelayEnabled()
        bleRelayEnabled = bleRelayController.isBleRelayEnabled()
        refreshWsDetailsState()
        refreshExportHistory()
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryOptimizationIgnored = isIgnoringBatteryOptimizations(context)
                refreshWsDetailsState()
                refreshExportHistory()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(connectivityManager) {
        if (connectivityManager == null) {
            onDispose { }
        } else {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    coroutineScope.launch { refreshWsDetailsState() }
                }

                override fun onLost(network: Network) {
                    coroutineScope.launch { refreshWsDetailsState() }
                }

                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    coroutineScope.launch { refreshWsDetailsState() }
                }
            }
            connectivityManager.registerDefaultNetworkCallback(callback)
            onDispose { runCatching { connectivityManager.unregisterNetworkCallback(callback) } }
        }
    }

    LaunchedEffect(batteryOptimizationIgnored, batteryPromptTriggered) {
        if (!batteryOptimizationIgnored && !batteryPromptTriggered) {
            batteryPromptTriggered = true
            batteryOptimizationIntent(context)?.let { batteryOptimizationLauncher.launch(it) }
        }
    }

    val chartRecords = remember(allRecords, selectedChartRange) {
        allRecords.takeLast(selectedChartRange)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = selectedRoute.title, fontWeight = FontWeight.SemiBold) }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                PhoneRoute.entries.forEach { route ->
                    NavigationBarItem(
                        selected = selectedRoute == route,
                        onClick = { selectedRoute = route },
                        icon = { TabBadge(route = route) },
                        label = { Text(text = route.title) }
                    )
                }
            }
        },
        containerColor = PhoneColors.AppBackground
    ) { innerPadding ->
        when (selectedRoute) {
            PhoneRoute.HEALTH -> HealthScreen(
                modifier = Modifier,
                innerPadding = innerPadding,
                uiState = uiState
            )

            PhoneRoute.DEVICE -> DeviceScreen(
                modifier = Modifier,
                innerPadding = innerPadding,
                uiState = uiState,
                wsRelayEnabled = wsRelayEnabled,
                bleRelayEnabled = bleRelayEnabled,
                lanIpv4 = lanIpv4,
                wsEndpoint = wsEndpoint,
                batteryOptimizationIgnored = batteryOptimizationIgnored,
                onWebSocketToggle = { enabled ->
                    wsRelayEnabled = if (enabled) {
                        webSocketRelayController.startWebSocketRelay().isSuccess
                    } else {
                        webSocketRelayController.stopWebSocketRelay()
                        false
                    }
                    refreshWsDetailsState()
                },
                onBleToggle = { enabled ->
                    bleRelayEnabled = if (enabled) {
                        if (hasBleTransferPermissions(context, requiredBlePermissions)) {
                            bleRelayController.startBleRelay().isSuccess
                        } else {
                            requestBlePermissionsLauncher.launch(requiredBlePermissions)
                            bleRelayEnabled
                        }
                    } else {
                        bleRelayController.stopBleRelay()
                        false
                    }
                },
                onRequestBatteryOptimizationExemption = {
                    batteryOptimizationIntent(context)?.let { batteryOptimizationLauncher.launch(it) }
                },
                onCopyWebSocketEndpoint = {
                    wsEndpoint?.let { endpoint ->
                        clipboardManager?.setPrimaryClip(ClipData.newPlainText("ws-endpoint", endpoint))
                        exportMessage = "WS 地址已复制"
                    }
                }
            )

            PhoneRoute.ME -> MeScreen(
                modifier = Modifier,
                innerPadding = innerPadding,
                uiState = uiState,
                records = chartRecords,
                allRecordCount = allRecords.size,
                selectedRange = selectedChartRange,
                exportHistory = exportHistory,
                exportMessage = exportMessage,
                onRangeSelected = { selectedChartRange = it },
                onExportSvg = {
                    coroutineScope.launch {
                        exportManager.exportChartSvg()
                            .onSuccess {
                                exportMessage = "已导出 SVG 折线图: ${it.file.name}"
                                refreshExportHistory()
                            }
                            .onFailure { exportMessage = "导出 SVG 失败: ${it.message ?: "未知错误"}" }
                    }
                },
                onExportCsv = {
                    coroutineScope.launch {
                        exportManager.exportCsv()
                            .onSuccess {
                                exportMessage = "已导出 CSV: ${it.file.name}"
                                refreshExportHistory()
                            }
                            .onFailure { exportMessage = "导出 CSV 失败: ${it.message ?: "未知错误"}" }
                    }
                },
                onExportJson = {
                    coroutineScope.launch {
                        exportManager.exportJson()
                            .onSuccess {
                                exportMessage = "已导出 JSON: ${it.file.name}"
                                refreshExportHistory()
                            }
                            .onFailure { exportMessage = "导出 JSON 失败: ${it.message ?: "未知错误"}" }
                    }
                }
            )
        }
    }
}

private fun bleTransferPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )
    } else {
        emptyArray()
    }
}

private fun hasBleTransferPermissions(context: Context, requiredPermissions: Array<String>): Boolean {
    return requiredPermissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(PowerManager::class.java) ?: return true
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun batteryOptimizationIntent(context: Context): Intent? {
    val requestIntent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
    )
    if (requestIntent.resolveActivity(context.packageManager) != null) {
        return requestIntent
    }
    val settingsIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    return settingsIntent.takeIf { it.resolveActivity(context.packageManager) != null }
}
