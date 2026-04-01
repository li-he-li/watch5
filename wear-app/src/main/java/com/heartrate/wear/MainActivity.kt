package com.heartrate.wear

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.heartrate.shared.presentation.model.ConnectionStatus
import com.heartrate.shared.presentation.model.HeartRateUiState
import com.heartrate.shared.presentation.viewmodel.HeartRateViewModel
import com.heartrate.wear.service.WearMonitoringForegroundService
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val sharedViewModel: HeartRateViewModel by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WearPermissionGate(sharedViewModel)
            }
        }
    }

    override fun onDestroy() {
        sharedViewModel.detachUi()
        super.onDestroy()
    }
}

@Composable
private fun WearPermissionGate(viewModel: HeartRateViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(hasRequiredSensorPermissions(context)) }
    var permissionDenied by remember { mutableStateOf(false) }
    var monitoringEnabled by remember { mutableStateOf(loadMonitoringEnabled(context)) }
    var batteryOptimizationIgnored by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var batteryPromptTriggered by remember { mutableStateOf(false) }
    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val hasForeground = granted || hasForegroundSensorPermission(context)
        hasPermission = hasRequiredSensorPermissions(context)
        permissionDenied = !hasPermission
        if (hasForeground && !hasBackgroundSensorPermission(context)) {
            openAppPermissionSettings(context)
        }
    }
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted || hasRequiredSensorPermissions(context)
        permissionDenied = !hasPermission
        if (!hasPermission) {
            openAppPermissionSettings(context)
        }
    }
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        batteryOptimizationIgnored = isIgnoringBatteryOptimizations(context)
    }

    LaunchedEffect(context) {
        hasPermission = hasRequiredSensorPermissions(context)
        batteryOptimizationIgnored = isIgnoringBatteryOptimizations(context)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = hasRequiredSensorPermissions(context)
                batteryOptimizationIgnored = isIgnoringBatteryOptimizations(context)
                if (hasPermission && monitoringEnabled) {
                    viewModel.attachUiToMonitoring()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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

    LaunchedEffect(hasPermission, monitoringEnabled) {
        if (hasPermission && monitoringEnabled) {
            WearMonitoringForegroundService.start(context)
            viewModel.attachUiToMonitoring()
        } else {
            WearMonitoringForegroundService.stop(context)
            viewModel.stopMonitoring()
        }
    }

    if (hasPermission) {
        WearNavApp(
            viewModel = viewModel,
            monitoringEnabled = monitoringEnabled,
            batteryOptimizationIgnored = batteryOptimizationIgnored,
            onMonitoringEnabledChange = { enabled ->
                monitoringEnabled = enabled
                saveMonitoringEnabled(context, enabled)
            },
            onRequestBatteryOptimizationExemption = {
                batteryOptimizationIntent(context)?.let { intent ->
                    batteryOptimizationLauncher.launch(intent)
                }
            }
        )
    } else {
        PermissionScreen(
            permissionDenied = permissionDenied,
            onRequestPermission = {
                when {
                    !hasForegroundSensorPermission(context) -> {
                        foregroundPermissionLauncher.launch(Manifest.permission.BODY_SENSORS)
                    }

                    !hasBackgroundSensorPermission(context) -> {
                        backgroundPermissionLauncher.launch(Manifest.permission.BODY_SENSORS_BACKGROUND)
                    }

                    else -> {
                        hasPermission = true
                        permissionDenied = false
                    }
                }
            }
        )
    }
}

private fun hasRequiredSensorPermissions(context: Context): Boolean {
    return hasForegroundSensorPermission(context) && hasBackgroundSensorPermission(context)
}

private fun hasForegroundSensorPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BODY_SENSORS
    ) == PackageManager.PERMISSION_GRANTED
}

private fun hasBackgroundSensorPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BODY_SENSORS_BACKGROUND
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun WearNavApp(
    viewModel: HeartRateViewModel,
    monitoringEnabled: Boolean,
    batteryOptimizationIgnored: Boolean,
    onMonitoringEnabledChange: (Boolean) -> Unit,
    onRequestBatteryOptimizationExemption: () -> Unit
) {
    DisposableEffect(Unit) {
        onDispose {
            viewModel.detachUi()
        }
    }

    MonitorScreen(
        uiState = viewModel.uiState.collectAsState().value,
        monitoringEnabled = monitoringEnabled,
        batteryOptimizationIgnored = batteryOptimizationIgnored,
        onMonitoringEnabledChange = onMonitoringEnabledChange,
        onRequestBatteryOptimizationExemption = onRequestBatteryOptimizationExemption
    )
}

@Composable
private fun PermissionScreen(
    permissionDenied: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val permanentlyDenied = activity != null &&
        permissionDenied &&
        !hasForegroundSensorPermission(context) &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.BODY_SENSORS)

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            item {
                Text("Sensor Permission", color = Color.White)
            }
            item {
                Text("Required: sensors permission for foreground and background.", color = Color.White)
            }
            if (permissionDenied) {
                item {
                    Text("Permission denied. Monitoring cannot start.", color = Color.White)
                }
            }
            item {
                Chip(
                    onClick = onRequestPermission,
                    label = { Text("Grant Permission") },
                    colors = ChipDefaults.primaryChipColors()
                )
            }
            if (permanentlyDenied) {
                item {
                    Chip(
                        onClick = {
                            openAppPermissionSettings(context)
                        },
                        label = { Text("Open Settings") }
                    )
                }
            }
        }
    }
}

@Composable
private fun MonitorScreen(
    uiState: HeartRateUiState,
    monitoringEnabled: Boolean,
    batteryOptimizationIgnored: Boolean,
    onMonitoringEnabledChange: (Boolean) -> Unit,
    onRequestBatteryOptimizationExemption: () -> Unit
) {
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            item {
                ToggleChip(
                    checked = monitoringEnabled,
                    onCheckedChange = onMonitoringEnabledChange,
                    label = { Text(if (monitoringEnabled) "Monitoring On" else "Monitoring Off") },
                    toggleControl = { Switch(checked = monitoringEnabled) },
                    colors = ToggleChipDefaults.toggleChipColors()
                )
            }
            item {
                Text(text = "Heart Rate", color = Color.White)
            }
            item {
                Text(
                    text = if (uiState.currentHeartRate > 0) "${uiState.currentHeartRate} BPM" else "-- BPM",
                    color = Color(0xFF00E676)
                )
            }
            item {
                Text(
                    text = when {
                        !monitoringEnabled -> "Stopped"
                        uiState.isMonitoring && uiState.connectionStatus == ConnectionStatus.CONNECTED -> "Connected"
                        uiState.isMonitoring -> "Connecting"
                        else -> "Stopped"
                    },
                    color = Color.White
                )
            }
            item {
                Text(text = "Battery: ${uiState.batteryLevel?.toString() ?: "--"}%", color = Color.White)
            }
            item {
                Text(
                    text = "Last update: ${formatLastUpdateAge(uiState.lastHeartRateTimestamp)}",
                    color = Color.White
                )
            }
            if (!batteryOptimizationIgnored) {
                item {
                    Text(
                        text = "Battery optimization is ON. Background heart-rate may stop when screen off.",
                        color = Color.White
                    )
                }
                item {
                    Chip(
                        onClick = onRequestBatteryOptimizationExemption,
                        label = { Text("Allow Unrestricted") },
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            }
            uiState.errorMessage?.let { message ->
                item {
                    Text(text = message, color = Color.Red)
                }
            }
        }
    }
}

private fun loadMonitoringEnabled(context: Context): Boolean {
    return monitoringPreferences(context).getBoolean(KEY_MONITORING_ENABLED, true)
}

private fun saveMonitoringEnabled(context: Context, enabled: Boolean) {
    monitoringPreferences(context).edit().putBoolean(KEY_MONITORING_ENABLED, enabled).apply()
}

private fun monitoringPreferences(context: Context): SharedPreferences {
    return context.getSharedPreferences(PREFS_MONITORING, Context.MODE_PRIVATE)
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

private fun openAppPermissionSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )
    context.startActivity(intent)
}

private const val PREFS_MONITORING = "wear_monitoring_prefs"
private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
