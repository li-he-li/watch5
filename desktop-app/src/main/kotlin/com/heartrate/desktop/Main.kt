package com.heartrate.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.heartrate.desktop.di.desktopAppModule
import com.heartrate.shared.di.getAppModules
import com.heartrate.shared.presentation.model.ConnectionStatus
import com.heartrate.shared.presentation.model.HeartRateUiState
import com.heartrate.shared.presentation.model.TransportMode
import com.heartrate.shared.presentation.model.displayText
import com.heartrate.shared.presentation.viewmodel.HeartRateViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import java.awt.Point
import java.awt.Color as AwtColor
import java.awt.Window as AwtWindow
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import kotlin.math.abs

fun main() = application {
    if (org.koin.core.context.GlobalContext.getOrNull() == null) {
        startKoin {
            modules(getAppModules() + desktopAppModule)
        }
    }

    val app = remember { DesktopHeartRateApp() }
    val viewModel = app.viewModel
    val uiState by viewModel.uiState.collectAsState()
    var lastNonZeroHeartRate by remember { mutableStateOf(0) }

    var compactMode by remember { mutableStateOf(false) }
    var currentRoute by remember { mutableStateOf(DesktopRoute.MONITOR) }
    var serverUrl by remember { mutableStateOf("ws://127.0.0.1:8080/heartrate") }
    var transportMode by remember { mutableStateOf(viewModel.getTransportMode()) }

    val mainWindowState = rememberWindowState(width = 980.dp, height = 720.dp)
    val compactWindowState = rememberWindowState(width = 220.dp, height = 92.dp)
    val closeApplicationSafely = {
        viewModel.onCleared()
        exitApplication()
    }

    LaunchedEffect(Unit) {
        viewModel.startMonitoring()
        viewModel.setTransportMode(transportMode)
    }
    LaunchedEffect(uiState.currentHeartRate) {
        if (uiState.currentHeartRate > 0) {
            lastNonZeroHeartRate = uiState.currentHeartRate
        }
    }

    if (compactMode) {
        Window(
            onCloseRequest = closeApplicationSafely,
            title = "Heart Rate Overlay",
            state = compactWindowState,
            transparent = true,
            undecorated = true,
            alwaysOnTop = true,
            resizable = false
        ) {
            CompactHeartRateOverlay(
                heartRate = if (uiState.currentHeartRate > 0) {
                    uiState.currentHeartRate
                } else {
                    lastNonZeroHeartRate
                },
                hostWindow = window,
                onExitCompactMode = {
                    compactMode = false
                    viewModel.startMonitoring()
                }
            )
        }
    } else {
        Window(
            onCloseRequest = closeApplicationSafely,
            title = "Heart Rate Monitor - Desktop",
            state = mainWindowState,
            resizable = true
        ) {
            DesktopMainContent(
                uiState = uiState,
                currentRoute = currentRoute,
                serverUrl = serverUrl,
                transportMode = transportMode,
                onRouteChange = { currentRoute = it },
                onServerUrlChange = { serverUrl = it },
                onTransportModeChange = {
                    transportMode = it
                    viewModel.setTransportMode(it)
                },
                onEnableCompactMode = {
                    viewModel.startMonitoring()
                    compactMode = true
                },
                onConnectWebSocket = { viewModel.connectWebSocket(serverUrl) },
                onDisconnectWebSocket = { viewModel.disconnectWebSocket() },
                onStartBle = { viewModel.startBLE() },
                onStopBle = { viewModel.stopBLE() }
            )
        }
    }
}

class DesktopHeartRateApp : KoinComponent {
    val viewModel: HeartRateViewModel by inject()
}

private enum class DesktopRoute(val title: String) {
    MONITOR("Monitor"),
    CONNECTION("Connection"),
    ABOUT("About")
}

@Composable
private fun DesktopMainContent(
    uiState: HeartRateUiState,
    currentRoute: DesktopRoute,
    serverUrl: String,
    transportMode: TransportMode,
    onRouteChange: (DesktopRoute) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onTransportModeChange: (TransportMode) -> Unit,
    onEnableCompactMode: () -> Unit,
    onConnectWebSocket: () -> Unit,
    onDisconnectWebSocket: () -> Unit,
    onStartBle: () -> Unit,
    onStopBle: () -> Unit
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    DesktopRoute.entries.forEach { route ->
                        TextButton(
                            onClick = { onRouteChange(route) },
                            enabled = currentRoute != route
                        ) {
                            Text(route.title)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (currentRoute) {
                    DesktopRoute.MONITOR -> MonitorScreen(
                        uiState = uiState,
                        onEnterCompactMode = onEnableCompactMode
                    )

                    DesktopRoute.CONNECTION -> ConnectionScreen(
                        uiState = uiState,
                        serverUrl = serverUrl,
                        transportMode = transportMode,
                        onServerUrlChange = onServerUrlChange,
                        onTransportModeChange = onTransportModeChange,
                        onConnectWebSocket = onConnectWebSocket,
                        onDisconnectWebSocket = onDisconnectWebSocket,
                        onStartBle = onStartBle,
                        onStopBle = onStopBle
                    )

                    DesktopRoute.ABOUT -> AboutScreen()
                }
            }
        }
    }
}

@Composable
private fun MonitorScreen(
    uiState: HeartRateUiState,
    onEnterCompactMode: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Heart Rate Monitor",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Card(
            modifier = Modifier
                .width(500.dp)
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(48.dp),
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

                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Battery: ${uiState.batteryLevel?.toString() ?: "--"}%")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Need a minimal view? Switch to compact transparent overlay mode.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onEnterCompactMode) {
            Text("Enter Compact Overlay")
        }
    }
}

@Composable
private fun CompactHeartRateOverlay(
    heartRate: Int,
    hostWindow: AwtWindow,
    onExitCompactMode: () -> Unit
) {
    val longPressMs = 250L
    val moveThreshold = 2
    var dragStartPointer: Point? by remember { mutableStateOf(null) }
    var dragStartWindow: Point? by remember { mutableStateOf(null) }
    var pressTimeMs: Long by remember { mutableStateOf(0L) }

    DisposableEffect(hostWindow) {
        hostWindow.background = AwtColor(0, 0, 0, 0)

        val pressListener = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                pressTimeMs = System.currentTimeMillis()
                dragStartPointer = e.locationOnScreen
                dragStartWindow = hostWindow.location
            }
        }

        val dragListener = object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                if (System.currentTimeMillis() - pressTimeMs < longPressMs) return

                val pointer = dragStartPointer ?: return
                val origin = dragStartWindow ?: return
                val current = e.locationOnScreen
                val dx = current.x - pointer.x
                val dy = current.y - pointer.y
                if (abs(dx) < moveThreshold && abs(dy) < moveThreshold) return

                val nextX = origin.x + dx
                val nextY = origin.y + dy
                hostWindow.setLocation(nextX, nextY)
            }
        }

        hostWindow.addMouseListener(pressListener)
        hostWindow.addMouseMotionListener(dragListener)

        onDispose {
            hostWindow.removeMouseListener(pressListener)
            hostWindow.removeMouseMotionListener(dragListener)
        }
    }

    val heartRateText = if (heartRate > 0) heartRate.toString() else "0"

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\u2764",
                color = Color(0xFFE53935),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onExitCompactMode() }
                    )
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = heartRateText,
                color = Color(0xFFE53935),
                fontSize = 50.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun ConnectionScreen(
    uiState: HeartRateUiState,
    serverUrl: String,
    transportMode: TransportMode,
    onServerUrlChange: (String) -> Unit,
    onTransportModeChange: (TransportMode) -> Unit,
    onConnectWebSocket: () -> Unit,
    onDisconnectWebSocket: () -> Unit,
    onStartBle: () -> Unit,
    onStopBle: () -> Unit
) {
    var wsEnabled by remember { mutableStateOf(false) }
    var bleEnabled by remember { mutableStateOf(false) }
    val wsAllowed = transportMode != TransportMode.BLE_ONLY
    val bleAllowed = transportMode != TransportMode.WS_ONLY

    LaunchedEffect(wsAllowed) {
        if (!wsAllowed) {
            wsEnabled = false
        }
    }
    LaunchedEffect(bleAllowed) {
        if (!bleAllowed) {
            bleEnabled = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Connection Controls",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Status: ${uiState.connectionStatus.displayText}")
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Current BPM: ${if (uiState.currentHeartRate > 0) uiState.currentHeartRate else "--"}")
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "Mode: $transportMode")
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "Source: ${uiState.deviceInfo ?: "--"}")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Error: ${uiState.errorMessage ?: "None"}",
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onTransportModeChange(TransportMode.AUTO) },
                enabled = transportMode != TransportMode.AUTO
            ) { Text("Auto") }
            Button(
                onClick = { onTransportModeChange(TransportMode.WS_ONLY) },
                enabled = transportMode != TransportMode.WS_ONLY
            ) { Text("WS-only") }
            Button(
                onClick = { onTransportModeChange(TransportMode.BLE_ONLY) },
                enabled = transportMode != TransportMode.BLE_ONLY
            ) { Text("BLE-only") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrlChange,
            label = { Text("WebSocket URL") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("WebSocket")
                Text(
                    text = if (wsAllowed) {
                        if (wsEnabled) "Enabled" else "Disabled"
                    } else {
                        "Unavailable in BLE-only mode"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (wsAllowed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                )
            }
            Switch(
                checked = wsEnabled,
                enabled = wsAllowed,
                onCheckedChange = { enabled ->
                    wsEnabled = enabled
                    if (enabled) {
                        onConnectWebSocket()
                    } else {
                        onDisconnectWebSocket()
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("BLE")
                Text(
                    text = if (bleAllowed) {
                        if (bleEnabled) "Enabled" else "Disabled"
                    } else {
                        "Unavailable in WS-only mode"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (bleAllowed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                )
            }
            Switch(
                checked = bleEnabled,
                enabled = bleAllowed,
                onCheckedChange = { enabled ->
                    bleEnabled = enabled
                    if (enabled) {
                        onStartBle()
                    } else {
                        onStopBle()
                    }
                }
            )
        }
    }
}

@Composable
private fun AboutScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Desktop App",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "KMP visualization scaffold with navigation.",
            textAlign = TextAlign.Center
        )
    }
}
