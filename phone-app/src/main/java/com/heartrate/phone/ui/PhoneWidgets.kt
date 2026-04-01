package com.heartrate.phone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.heartrate.phone.data.persistence.ExportFormat
import com.heartrate.phone.data.persistence.HeartRateEntity
import com.heartrate.phone.data.persistence.HeartRateExportMetadata
import com.heartrate.shared.presentation.model.HeartRateUiState
import com.heartrate.shared.presentation.model.displayText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun HeroHealthCard(uiState: HeartRateUiState, statusLabel: String) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(listOf(PhoneColors.HeroStart, PhoneColors.HeroEnd)),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(22.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(text = statusLabel, background = Color.White.copy(alpha = 0.18f), textColor = Color.White)
                    Text(text = formatClock(System.currentTimeMillis()), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.86f))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = if (uiState.currentHeartRate > 0) "${uiState.currentHeartRate}" else "--",
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(text = "实时心率 BPM", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.88f))
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    HeroStat("电量", uiState.batteryLevel?.let { "$it%" } ?: "--")
                    HeroStat("更新", formatLastUpdateAge(uiState.lastHeartRateTimestamp))
                    HeroStat("状态", uiState.connectionStatus.displayText)
                }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.72f))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun MetricTile(modifier: Modifier = Modifier, label: String, value: String, unit: String, accent: Color) {
    SectionCard(modifier = modifier) {
        BadgeGlyph(text = "HR", accent = accent)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = PhoneColors.TextSecondary)
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = PhoneColors.TextPrimary)
            Spacer(modifier = Modifier.size(6.dp))
            Text(text = unit, style = MaterialTheme.typography.bodyMedium, color = PhoneColors.TextSecondary)
        }
    }
}

@Composable
fun InfoCard(modifier: Modifier = Modifier, title: String, content: String, contentColor: Color = PhoneColors.TextPrimary) {
    SectionCard(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium, color = PhoneColors.TextSecondary)
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = content, style = MaterialTheme.typography.titleMedium, color = contentColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), content = content)
    }
}

@Composable
fun RelayToggleRow(
    title: String,
    description: String,
    iconText: String,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BadgeGlyph(text = iconText, accent = accent, rounded = 16.dp, size = 48.dp)
        Spacer(modifier = Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = PhoneColors.TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = PhoneColors.TextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun UrlPanel(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PhoneColors.AppBackground)
            .padding(14.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = PhoneColors.TextSecondary)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = PhoneColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun RangeSelector(selectedRange: Int, onRangeSelected: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(30, 60, 120, 240).forEach { range ->
            val selected = range == selectedRange
            FilledTonalButton(
                onClick = { onRangeSelected(range) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (selected) PhoneColors.HeroStart.copy(alpha = 0.16f) else PhoneColors.AppBackground,
                    contentColor = if (selected) PhoneColors.HeroEnd else PhoneColors.TextSecondary
                )
            ) {
                Text(text = "$range 条")
            }
        }
    }
}

@Composable
fun HeartRateLineChart(records: List<HeartRateEntity>, modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier) {
        if (records.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                Text(text = "还没有可绘制的心率样本", color = PhoneColors.TextSecondary)
            }
            return@SectionCard
        }

        val values = records.map { it.heartRate.coerceAtLeast(0) }
        val minValue = min(values.minOrNull() ?: 60, 60)
        val maxValue = max(values.maxOrNull() ?: 120, minValue + 10)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "${maxValue} BPM", style = MaterialTheme.typography.labelMedium, color = PhoneColors.TextSecondary)
            Text(text = "${records.size} samples", style = MaterialTheme.typography.labelMedium, color = PhoneColors.TextSecondary)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = PhoneColors.ChartFill,
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
            )
            val width = size.width
            val height = size.height
            val verticalPadding = 16.dp.toPx()
            val chartHeight = height - verticalPadding * 2
            repeat(4) { index ->
                val y = verticalPadding + chartHeight * index / 3f
                drawLine(
                    color = PhoneColors.GridLine,
                    start = Offset(12.dp.toPx(), y),
                    end = Offset(width - 12.dp.toPx(), y),
                    strokeWidth = 2.dp.toPx()
                )
            }
            val path = Path()
            records.forEachIndexed { index, record ->
                val x = if (records.size == 1) width / 2f else 18.dp.toPx() + (width - 36.dp.toPx()) * index / (records.size - 1).toFloat()
                val ratio = (record.heartRate - minValue).toFloat() / (maxValue - minValue).toFloat()
                val y = verticalPadding + chartHeight - ratio.coerceIn(0f, 1f) * chartHeight
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                brush = Brush.horizontalGradient(listOf(PhoneColors.HeroStart, PhoneColors.HeroEnd)),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )
            val last = records.last()
            val lastX = if (records.size == 1) width / 2f else width - 18.dp.toPx()
            val lastRatio = (last.heartRate - minValue).toFloat() / (maxValue - minValue).toFloat()
            val lastY = verticalPadding + chartHeight - lastRatio.coerceIn(0f, 1f) * chartHeight
            drawCircle(color = PhoneColors.HeroEnd.copy(alpha = 0.22f), radius = 16.dp.toPx(), center = Offset(lastX, lastY))
            drawCircle(color = Color.White, radius = 7.dp.toPx(), center = Offset(lastX, lastY))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "${minValue} BPM", style = MaterialTheme.typography.labelMedium, color = PhoneColors.TextSecondary)
    }
}

@Composable
fun ExportHistoryRow(item: HeartRateExportMetadata) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PhoneColors.AppBackground)
            .padding(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when (item.format) {
                    ExportFormat.SVG -> "SVG 折线图"
                    ExportFormat.CSV -> "CSV 数据"
                    ExportFormat.JSON -> "JSON 数据"
                },
                style = MaterialTheme.typography.titleSmall,
                color = PhoneColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(text = formatClock(item.generatedAt), style = MaterialTheme.typography.labelMedium, color = PhoneColors.TextSecondary)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "范围: ${formatDateTime(item.rangeStart)} - ${formatDateTime(item.rangeEnd)}", style = MaterialTheme.typography.bodyMedium, color = PhoneColors.TextPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "样本 ${item.sampleCount} 条", style = MaterialTheme.typography.bodySmall, color = PhoneColors.TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = item.filePath, style = MaterialTheme.typography.bodySmall, color = PhoneColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun StatusPill(text: String, background: Color = PhoneColors.StatusBg, textColor: Color = PhoneColors.StatusText) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text = text, color = textColor, style = MaterialTheme.typography.labelLarge)
    }
}

fun formatLastUpdateAge(timestamp: Long?): String {
    if (timestamp == null) return "--"
    val ageSeconds = ((System.currentTimeMillis() - timestamp) / 1000L).coerceAtLeast(0L)
    return "${ageSeconds}s"
}

private fun formatClock(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatDateTime(timestamp: Long?): String {
    if (timestamp == null) return "--"
    return SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

object PhoneColors {
    val AppBackground = Color(0xFFF4F1EC)
    val HeroStart = Color(0xFFFFA340)
    val HeroEnd = Color(0xFFFF6B3D)
    val HeartAccent = Color(0xFFFF7A45)
    val PowerAccent = Color(0xFF38A169)
    val TextPrimary = Color(0xFF231913)
    val TextSecondary = Color(0xFF7D6B63)
    val StatusBg = Color(0xFFFFEEE4)
    val StatusText = Color(0xFFB84D1B)
    val GridLine = Color(0xFFF1D7CA)
    val ChartFill = Color(0xFFFFF7F2)
    val ErrorAccent = Color(0xFFC53030)
}

@Composable
fun TabBadge(route: Enum<*>) {
    val label = when (route.name) {
        "HEALTH" -> "健"
        "DEVICE" -> "设"
        else -> "我"
    }
    BadgeGlyph(text = label, accent = PhoneColors.HeartAccent, size = 24.dp, rounded = 999.dp, textStyle = MaterialTheme.typography.labelMedium)
}

@Composable
private fun BadgeGlyph(
    text: String,
    accent: Color,
    rounded: androidx.compose.ui.unit.Dp = 14.dp,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelLarge
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(rounded))
            .background(accent.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = accent, style = textStyle, fontWeight = FontWeight.Bold)
    }
}
