package com.heartrate.phone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.heartrate.phone.data.persistence.HeartRateEntity
import com.heartrate.phone.data.persistence.HeartRateExportMetadata
import com.heartrate.shared.presentation.model.ConnectionStatus
import com.heartrate.shared.presentation.model.HeartRateUiState
import com.heartrate.shared.presentation.model.displayText

@Composable
fun HealthScreen(
    modifier: Modifier,
    innerPadding: PaddingValues,
    uiState: HeartRateUiState
) {
    val statusLabel = when {
        uiState.isMonitoring && uiState.connectionStatus == ConnectionStatus.CONNECTED -> "实时同步中"
        uiState.isMonitoring -> "等待设备数据"
        else -> "监测未启动"
    }
    Surface(
        modifier = modifier.fillMaxSize(),
        color = PhoneColors.AppBackground
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { HeroHealthCard(uiState = uiState, statusLabel = statusLabel) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricTile(
                        modifier = Modifier.weight(1f),
                        label = "当前心率",
                        value = if (uiState.currentHeartRate > 0) "${uiState.currentHeartRate}" else "--",
                        unit = "BPM",
                        accent = PhoneColors.HeartAccent
                    )
                    MetricTile(
                        modifier = Modifier.weight(1f),
                        label = "当前电量",
                        value = uiState.batteryLevel?.toString() ?: "--",
                        unit = "%",
                        accent = PhoneColors.PowerAccent
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoCard(
                        modifier = Modifier.weight(1f),
                        title = "连接状态",
                        content = uiState.connectionStatus.displayText
                    )
                    InfoCard(
                        modifier = Modifier.weight(1f),
                        title = "最后更新",
                        content = formatLastUpdateAge(uiState.lastHeartRateTimestamp)
                    )
                }
            }
            if (!uiState.deviceInfo.isNullOrBlank()) {
                item { InfoCard(title = "当前设备", content = uiState.deviceInfo.orEmpty()) }
            }
            uiState.errorMessage?.takeIf { it.isNotBlank() }?.let { error ->
                item { InfoCard(title = "错误提示", content = error, contentColor = PhoneColors.ErrorAccent) }
            }
        }
    }
}

@Composable
fun DeviceScreen(
    modifier: Modifier,
    innerPadding: PaddingValues,
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
        modifier = modifier.fillMaxSize(),
        color = PhoneColors.AppBackground
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "设备桥接",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = PhoneColors.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "这里只控制手机对电脑的转发模式，手表到手机的采样链路保持不变。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PhoneColors.TextSecondary
                            )
                        }
                        StatusPill(text = uiState.connectionStatus.displayText)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    RelayToggleRow(
                        title = "WebSocket 模式",
                        description = "适合局域网直连电脑，实时推送到桌面端。",
                        iconText = "WS",
                        checked = wsRelayEnabled,
                        accent = PhoneColors.HeartAccent,
                        onCheckedChange = onWebSocketToggle
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    RelayToggleRow(
                        title = "BLE 模式",
                        description = "适合近距离蓝牙连接桌面端，作为 WS 之外的传输方式。",
                        iconText = "BLE",
                        checked = bleRelayEnabled,
                        accent = PhoneColors.PowerAccent,
                        onCheckedChange = onBleToggle
                    )
                }
            }
            item {
                SectionCard {
                    Text(
                        text = "WebSocket URL",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PhoneColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    UrlPanel(label = "局域网 IPv4", value = lanIpv4 ?: "未获取到可用地址")
                    Spacer(modifier = Modifier.height(12.dp))
                    UrlPanel(label = "完整 WS 地址", value = wsEndpoint ?: "请先打开 WebSocket 模式")
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = onCopyWebSocketEndpoint,
                        enabled = !wsEndpoint.isNullOrBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(text = "复制 WS 地址")
                    }
                }
            }
            item {
                SectionCard {
                    Text(
                        text = "后台保活",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PhoneColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (batteryOptimizationIgnored) {
                            "系统已允许无限制运行，手机更适合持续转发心率。"
                        } else {
                            "当前仍受电池优化限制，可能影响长时间转发稳定性。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhoneColors.TextSecondary
                    )
                    if (!batteryOptimizationIgnored) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onRequestBatteryOptimizationExemption,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(text = "允许无限制运行")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MeScreen(
    modifier: Modifier,
    innerPadding: PaddingValues,
    uiState: HeartRateUiState,
    records: List<HeartRateEntity>,
    allRecordCount: Int,
    selectedRange: Int,
    exportHistory: List<HeartRateExportMetadata>,
    exportMessage: String?,
    onRangeSelected: (Int) -> Unit,
    onExportSvg: () -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = PhoneColors.AppBackground
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "我的趋势",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = PhoneColors.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "最近 ${records.size} 条样本，数据库累计 $allRecordCount 条。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PhoneColors.TextSecondary
                            )
                        }
                        StatusPill(text = if (uiState.currentHeartRate > 0) "${uiState.currentHeartRate} BPM" else "无实时值")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    RangeSelector(selectedRange = selectedRange, onRangeSelected = onRangeSelected)
                    Spacer(modifier = Modifier.height(16.dp))
                    HeartRateLineChart(
                        records = records,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                }
            }
            item {
                SectionCard {
                    Text(
                        text = "导出折线图",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PhoneColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "SVG 会导出图形，CSV/JSON 会导出原始心率数据。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhoneColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onExportSvg,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text(text = "导出 SVG 折线图") }
                    Spacer(modifier = Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = onExportCsv,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text(text = "导出 CSV 数据") }
                    Spacer(modifier = Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = onExportJson,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text(text = "导出 JSON 数据") }
                    if (!exportMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = exportMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = PhoneColors.TextSecondary
                        )
                    }
                }
            }
            item {
                SectionCard {
                    Text(
                        text = "导出记录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PhoneColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (exportHistory.isEmpty()) {
                        Text(
                            text = "还没有导出记录。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PhoneColors.TextSecondary
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            exportHistory.forEach { item -> ExportHistoryRow(item) }
                        }
                    }
                }
            }
        }
    }
}
