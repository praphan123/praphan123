package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.DisasterCategory
import com.example.domain.model.DisasterEvent
import com.example.ui.components.severityColor
import com.example.ui.theme.LocalHazardColors
import com.example.ui.viewmodel.FeedUiState

@Composable
fun AnalyticsScreen(
    feedState: FeedUiState,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        when (feedState) {
            FeedUiState.Loading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CircularProgressIndicator()
                }
            }

            is FeedUiState.Error -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "ยังไม่มีข้อมูลสถิติ: ${feedState.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            is FeedUiState.Success -> {
                AnalyticsContent(events = feedState.allEvents)
            }
        }
    }
}

@Composable
private fun AnalyticsContent(events: List<DisasterEvent>) {
    val hazard = LocalHazardColors.current
    val quakes = events.filter { it.category == DisasterCategory.EARTHQUAKE }
    val maxMag = quakes.mapNotNull { it.magnitude }.maxOrNull() ?: 0.0
    val shallowCount = quakes.count { (it.depthKm ?: 999.0) < 70.0 }
    val deepCount = quakes.size - shallowCount
    val tsunamiCount = events.count { it.tsunamiAlert }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxSize()
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "สรุปสถิติและแนวโน้มภัยพิบัติล่าสุด",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatSummaryCard(
                    title = "เหตุการณ์ทั้งหมด",
                    value = "${events.size}",
                    subtitle = "USGS + NASA",
                    accent = hazard.telemetry,
                    modifier = Modifier.weight(1f)
                )
                StatSummaryCard(
                    title = "แผ่นดินไหวสูงสุด",
                    value = if (maxMag > 0) "M${"%.1f".format(maxMag)}" else "-",
                    subtitle = "${quakes.size} ครั้งล่าสุด",
                    accent = if (maxMag >= 5.5) hazard.critical else hazard.moderate,
                    modifier = Modifier.weight(1f)
                )
                StatSummaryCard(
                    title = "เตือนสึนามิ",
                    value = "$tsunamiCount",
                    subtitle = "เหตุการณ์เฝ้าระวัง",
                    accent = if (tsunamiCount > 0) hazard.critical else hazard.low,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            SeismicMagnitudeChartCard(quakes = quakes)
        }

        item {
            CategoryDistributionCard(events = events)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "การวิเคราะห์ความลึกแผ่นดินไหว (Depth Telemetry)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "แผ่นดินไหวระดับตื้น (< 70 กม.) มักส่งแรงสั่นสะเทือนถึงผิวโลกและอาคารบ้านเรือนรุนแรงกว่าแผ่นดินไหวระดับลึก",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            color = hazard.high.copy(alpha = 0.14f),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "$shallowCount ครั้ง",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = hazard.high
                                )
                                Text(
                                    text = "ระดับตื้น (< 70 กม.)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Surface(
                            color = hazard.telemetry.copy(alpha = 0.14f),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "$deepCount ครั้ง",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = hazard.telemetry
                                )
                                Text(
                                    text = "ระดับกลาง/ลึก (≥ 70 กม.)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatSummaryCard(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.border(
            1.dp,
            accent.copy(alpha = 0.35f),
            MaterialTheme.shapes.medium
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = accent
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SeismicMagnitudeChartCard(quakes: List<DisasterEvent>) {
    val hazard = LocalHazardColors.current
    val buckets = listOf(
        Triple("M2.5–3.9", quakes.count { (it.magnitude ?: 0.0) < 4.0 }, hazard.low),
        Triple("M4.0–4.9", quakes.count { (it.magnitude ?: 0.0) in 4.0..4.99 }, hazard.telemetry),
        Triple("M5.0–5.9", quakes.count { (it.magnitude ?: 0.0) in 5.0..5.99 }, hazard.moderate),
        Triple("M6.0+", quakes.count { (it.magnitude ?: 0.0) >= 6.0 }, hazard.critical)
    )
    val maxBucket = (buckets.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "การกระจายระดับความรุนแรงแผ่นดินไหว (USGS)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                val barCount = buckets.size
                val spacing = 18.dp.toPx()
                val totalSpacing = spacing * (barCount + 1)
                val barWidth = ((size.width - totalSpacing) / barCount).coerceAtLeast(12f)
                val maxBarHeight = size.height - 16.dp.toPx()

                // Horizontal reference lines
                for (i in 1..3) {
                    val y = size.height - (maxBarHeight * i / 3f)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                buckets.forEachIndexed { idx, (_, count, color) ->
                    val ratio = count.toFloat() / maxBucket.toFloat()
                    val h = (maxBarHeight * ratio).coerceAtLeast(6.dp.toPx())
                    val x = spacing + idx * (barWidth + spacing)
                    val y = size.height - h
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier.fillMaxWidth()
            ) {
                buckets.forEach { (label, count, color) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.titleSmall,
                            color = color
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryDistributionCard(events: List<DisasterEvent>) {
    val total = events.size.coerceAtLeast(1)
    val grouped = DisasterCategory.entries.mapNotNull { cat ->
        val count = events.count { it.category == cat }
        if (count > 0) cat to count else null
    }.sortedByDescending { it.second }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "สัดส่วนประเภทภัยพิบัติที่กำลังเฝ้าระวัง",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            grouped.forEach { (cat, count) ->
                val ratio = count.toFloat() / total.toFloat()
                val color = severityColor(
                    when (cat) {
                        DisasterCategory.VOLCANO, DisasterCategory.STORM -> 4
                        DisasterCategory.EARTHQUAKE, DisasterCategory.FLOOD -> 3
                        else -> 2
                    }
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = cat.labelTh,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$count เหตุการณ์ (${(ratio * 100).toInt()}%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = color
                        )
                    }
                    LinearProgressIndicator(
                        progress = { ratio },
                        color = color,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}
