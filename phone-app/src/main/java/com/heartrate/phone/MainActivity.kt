package com.heartrate.phone

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.heartrate.phone.data.PhoneBleRelayController
import com.heartrate.phone.data.PhoneWebSocketRelayController
import com.heartrate.shared.presentation.model.ConnectionStatus
import com.heartrate.shared.presentation.model.HeartRateUiState
import com.heartrate.shared.presentation.model.displayText
import com.heartrate.shared.presentation.viewmodel.HeartRateViewModel
import com.heartrate.phone.service.PhoneRelayForegroundService
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val sharedViewModel: HeartRateViewModel by inject()
    private val bleRelayController: PhoneBleRelayController by inject()
    private val webSocketRelayController: PhoneWebSocketRelayController by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PhoneApp(
                    viewModel = sharedViewModel,
                    bleRelayController = bleRelayController,
                    webSocketRelayController = webSocketRelayController
                )
            }
        }
    }

    override fun onDestroy() {
        sharedViewModel.detachUi()
        super.onDestroy()
    }
}

private enum class PhoneRoute(val route: String, val title: String) {
    MONITOR("monitor", "Monitor"),
    CONNECTION("connection", "Connection")
}

@Composable
private fun PhoneApp(
    viewModel: HeartRateViewModel,
    bleRelayController: PhoneBleRelayController,
    webSocketRelayController: PhoneWebSocketRelayController
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val requiredBlePermissions = remember { bleTransferPermissions() }
    val clipboardManager = remember { context.getSystemService(ClipboardManager::class.java) }
    val connectivityManager = remember { context.getSystemService(ConnectivityManager::class.java) }
    var wsRelayEnabled by remember { mutableStateOf(false) }
    var bleRelayEnabled by remember { mutableStateOf(false) }
    var lanIpv4 by remember { mutableStateOf<String?>(null) }
    var wsEndpoint by remember { mutableStateOf<String?>(null) }
    var batteryOptimizationIgnored by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var batteryPromptTriggered by remember { mutableStateOf(false) }
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
        if (denied.isNotEmpty()) {
            bleRelayEnabled = false
        } else {
            val startResult = bleRelayController.startBleRelay()
            bleRelayEnabled = startResult.isSuccess
        }
    }
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        batteryOptimizationIgnored = isIgnoringBatteryOptimizations(context)
    }

    LaunchedEffect(Unit) {
        PhoneRelayForegroundService.start(context)
        viewModel.startMonitoring()
        wsRelayEnabled = webSocketRelayController.isWebSocketRelayEnabled()
        refreshWsDetailsState()
        bleRelayEnabled = bleRelayController.isBleRelayEnabled()
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryOptimizationIgnored = isIgnoringBatteryOptimizations(context)
                refreshWsDetailsState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(connectivityManager) {
        if (connectivityManager == null) {
            onDispose { }
        } else {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    coroutineScope.launch { refreshWsDetailsState() }
                }

                override fun onLost(network: android.net.Network) {
                    coroutineScope.launch { refreshWsDetailsState() }
                }

                override fun onLinkPropertiesChanged(
                    network: android.net.Network,
                    linkProperties: android.net.LinkProperties
                ) {
                    coroutineScope.launch { refreshWsDetailsState() }
                }
            }
            connectivityManager.registerDefaultNetworkCallback(callback)
            onDispose {
                runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            }
        }
    }

    LaunchedEffect(batteryOptimizationIgnored, batteryPromptTriggered) {
        if (!batteryOptimizationIgnored && !batteryPromptTriggered) {
            batteryPromptTriggered = true
            batteryOptimizationIntent(context)?.let { intent ->
                batteryOptimizationLauncher.launch(intent)
            }
        }
    }

    Scaffold(
        topBar = {
            TopNavBar(navController)
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = PhoneRoute.MONITOR.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(PhoneRoute.MONITOR.route) {
                MonitorScreen(viewModel.uiState.collectAsState().value)
            }

            composable(PhoneRoute.CONNECTION.route) {
                ConnectionScreen(
                    uiState = viewModel.uiState.collectAsState().value,
                    wsRelayEnabled = wsRelayEnabled,
                    bleRelayEnabled = bleRelayEnabled,
                    lanIpv4 = lanIpv4,
                    wsEndpoint = wsEndpoint,
                    batteryOptimizationIgnored = batteryOptimizationIgnored,
                    onWebSocketToggle = { enabled ->
                        if (enabled) {
                            wsRelayEnabled = webSocketRelayController.startWebSocketRelay().isSuccess
                        } else {
                            webSocketRelayController.stopWebSocketRelay()
                            wsRelayEnabled = false
                        }
                        refreshWsDetailsState()
                    },
                    onBleToggle = { enabled ->
                        if (enabled) {
                            if (hasBleTransferPermissions(context, requiredBlePermissions)) {
                                bleRelayEnabled = bleRelayController.startBleRelay().isSuccess
                            } else {
                                requestBlePermissionsLauncher.launch(requiredBlePermissions)
                            }
                        } else {
                            bleRelayController.stopBleRelay()
                            bleRelayEnabled = false
                        }
                    },
                    onRequestBatteryOptimizationExemption = {
                        batteryOptimizationIntent(context)?.let { intent ->
                            batteryOptimizationLauncher.launch(intent)
                        }
                    },
                    onCopyWebSocketEndpoint = {
                        val endpoint = wsEndpoint
                        if (!endpoint.isNullOrBlank()) {
                            clipboardManager?.setPrimaryClip(
                                ClipData.newPlainText("ws-endpoint", endpoint)
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TopNavBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        PhoneRoute.entries.forEach { item ->
            TextButton(
                onClick = { navController.navigate(item.route) },
                enabled = currentRoute != item.route
            ) {
                Text(text = item.title)
            }
        }
    }
}

@Composable
private fun MonitorScreen(uiState: HeartRateUiState) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Heart Rate Monitor",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (uiState.currentHeartRate > 0) "${uiState.currentHeartRate}" else "--",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "BPM",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 24.dp),
                        thickness = 2.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    val isConnected = uiState.connectionStatus == ConnectionStatus.CONNECTED
                    Text(
                        text = when {
                            uiState.isMonitoring && isConnected -> "Connected"
                            uiState.isMonitoring -> "Connecting..."
                            else -> "Stopped"
                        },
                        color = when {
                            uiState.isMonitoring && isConnected -> MaterialTheme.colorScheme.primary
                            uiState.isMonitoring -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Battery: ${uiState.batteryLevel?.toString() ?: "--"}%")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Last update: ${formatLastUpdateAge(uiState.lastHeartRateTimestamp)}")
                }
            }
        }
    }
}

@Composable
private fun ConnectionScreen(
    uiState: HeartRateUiState,
    wsRelayEnabled: Boolean,
    bleRelayEnabled: Boolean,
    lanIpv4: String?,
    wsEndpoint: String?,
    batteryOptimizationIgnored: Boolean,
    onWebSocketToggle: (Boolean) -> Unit,
    onBleToggle: (Boolean) -> Unit,
    onRequestBatteryOptimizationExemption: () -> Unit,
    onCopyWebSocketEndpoint: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Connection Controls",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Status: ${uiState.connectionStatus.displayText}",
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Error: ${uiState.errorMessage ?: "None"}",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "WebSocket Relay")
                Switch(
                    checked = wsRelayEnabled,
                    onCheckedChange = onWebSocketToggle
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "BLE Relay")
                Switch(
                    checked = bleRelayEnabled,
                    onCheckedChange = onBleToggle
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "LAN IPv4: ${lanIpv4 ?: "Unavailable"}",
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "WS Endpoint: ${wsEndpoint ?: "Unavailable"}",
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onCopyWebSocketEndpoint,
                enabled = !wsEndpoint.isNullOrBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Copy WS Endpoint")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (batteryOptimizationIgnored) {
                    "Battery optimization: unrestricted"
                } else {
                    "Battery optimization: restricted"
                },
                textAlign = TextAlign.Center
            )
            if (!batteryOptimizationIgnored) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRequestBatteryOptimizationExemption,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Allow Unrestricted")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Spacer(modifier = Modifier.height(20.dp))
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

private fun formatLastUpdateAge(timestamp: Long?): String {
    if (timestamp == null) return "--"
    val ageSeconds = ((System.currentTimeMillis() - timestamp) / 1000L).coerceAtLeast(0L)
    return "${ageSeconds}s ago"
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
    return if (settingsIntent.resolveActivity(context.packageManager) != null) {
        settingsIntent
    } else {
        null
    }
}
