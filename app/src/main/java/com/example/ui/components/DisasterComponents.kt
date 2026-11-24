package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cyclone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Landslide
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tsunami
import androidx.compose.material.icons.filled.Volcano
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.AiHazardAnalysis
import com.example.domain.model.DisasterCategory
import com.example.domain.model.DisasterEvent
import com.example.domain.model.HighRiskZone
import com.example.ui.theme.LocalHazardColors
import com.example.ui.viewmodel.AiAnalysisUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun severityColor(score: Int): Color {
    val hazard = LocalHazardColors.current
    return when {
        score >= 5 -> hazard.critical
        score == 4 -> hazard.high
        score == 3 -> hazard.moderate
        score == 2 -> hazard.telemetry
        else -> hazard.low
    }
}

fun categoryIcon(category: DisasterCategory): ImageVector {
    return when (category) {
        DisasterCategory.EARTHQUAKE -> Icons.Filled.Sensors
        DisasterCategory.FLOOD -> Icons.Filled.Water
        DisasterCategory.STORM -> Icons.Filled.Cyclone
        DisasterCategory.WILDFIRE -> Icons.Filled.LocalFireDepartment
        DisasterCategory.VOLCANO -> Icons.Filled.Volcano
        DisasterCategory.LANDSLIDE -> Icons.Filled.Landslide
        DisasterCategory.OTHER -> Icons.Filled.Public
    }
}

fun formatRelativeTimeTh(timestampMs: Long): String {
    val diffMin = ((System.currentTimeMillis() - timestampMs) / 60_000L).coerceAtLeast(0L)
    return when {
        diffMin < 1 -> "เมื่อสักครู่"
        diffMin < 60 -> "$diffMin นาทีที่แล้ว"
        diffMin < 1440 -> "${diffMin / 60} ชม. ที่แล้ว"
        else -> {
            val sdf = SimpleDateFormat("dd MMM HH:mm", Locale("th", "TH"))
            sdf.format(Date(timestampMs))
        }
    }
}

@Composable
fun SeverityBadge(
    score: Int,
    label: String? = null,
    modifier: Modifier = Modifier
) {
    val color = severityColor(score)
    val text = label ?: when (score) {
        5 -> "วิกฤต (5/5)"
        4 -> "สูง (4/5)"
        3 -> "ปานกลาง (3/5)"
        2 -> "เฝ้าระวัง (2/5)"
        else -> "ต่ำ (1/5)"
    }
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun RiskGaugeArc(
    score: Int,
    maxScore: Int = 5,
    size: Dp = 68.dp,
    label: String = "$score/$maxScore",
    modifier: Modifier = Modifier
) {
    val color = severityColor(score)
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val animatedProgress by animateFloatAsState(
        targetValue = (score.toFloat() / maxScore.toFloat()).coerceIn(0.05f, 1f),
        animationSpec = spring(),
        label = "risk_gauge_progress"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size)
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = 140f,
                sweepAngle = 260f * animatedProgress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
fun DisasterEventCard(
    event: DisasterEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sevColor = severityColor(event.severityScore)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("disaster_event_card_${event.id}")
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(sevColor.copy(alpha = 0.16f))
                    .border(1.dp, sevColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            ) {
                if (event.magnitude != null) {
                    Text(
                        text = "M${"%.1f".format(event.magnitude)}",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = sevColor
                    )
                } else {
                    Icon(
                        imageVector = categoryIcon(event.category),
                        contentDescription = event.category.labelTh,
                        tint = sevColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = event.category.labelTh,
                        style = MaterialTheme.typography.labelSmall,
                        color = sevColor
                    )
                    Text(
                        text = "• ${event.source}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (event.tsunamiAlert) {
                        Icon(
                            imageVector = Icons.Filled.Tsunami,
                            contentDescription = "เฝ้าระวังสึนามิ",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = event.place,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatRelativeTimeTh(event.timestampMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AiAnalysisResultView(
    analysis: AiHazardAnalysis,
    modifier: Modifier = Modifier
) {
    val hazard = LocalHazardColors.current
    val sevColor = severityColor(analysis.severityScore)

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        AnimatedVisibility(visible = analysis.shouldEvacuate) {
            Surface(
                color = hazard.criticalContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, hazard.critical, MaterialTheme.shapes.medium)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "แจ้งเตือนอพยพ",
                        tint = hazard.critical
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "คำแนะนำเร่งด่วน: สถานการณ์มีความเสี่ยงสูง ควรเตรียมพร้อมอพยพไปยังจุดปลอดภัยทันที",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                RiskGaugeArc(score = analysis.severityScore)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    SeverityBadge(
                        score = analysis.severityScore,
                        label = "ระดับความเสี่ยง: ${analysis.riskLevel}"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = analysis.hazardType,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = analysis.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (analysis.keyObservations.isNotEmpty()) {
            AnalysisBulletSection(
                title = "สิ่งที่ตรวจพบจากการวิเคราะห์",
                items = analysis.keyObservations,
                accent = MaterialTheme.colorScheme.secondary,
                icon = Icons.Filled.Sensors
            )
        }

        if (analysis.secondaryRisks.isNotEmpty()) {
            AnalysisBulletSection(
                title = "ความเสี่ยงต่อเนื่องที่ต้องเฝ้าระวัง",
                items = analysis.secondaryRisks,
                accent = sevColor,
                icon = Icons.Filled.ErrorOutline
            )
        }

        if (analysis.immediateActions.isNotEmpty()) {
            AnalysisBulletSection(
                title = "ขั้นตอนปฏิบัติเพื่อความปลอดภัย",
                items = analysis.immediateActions,
                accent = hazard.low,
                icon = Icons.Filled.Shield
            )
        }
    }
}

@Composable
private fun AnalysisBulletSection(
    title: String,
    items: List<String>,
    accent: Color,
    icon: ImageVector
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            items.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${index + 1}. $item",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailBottomSheet(
    event: DisasterEvent,
    analysisState: AiAnalysisUiState,
    onAnalyzeWithAi: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sevColor = severityColor(event.severityScore)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SeverityBadge(score = event.severityScore)
                Surface(
                    color = if (event.isHistorical) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (event.isHistorical) "ข้อมูลย้อนหลัง" else "เหตุการณ์ปัจจุบัน",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (event.isHistorical) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = "${event.category.labelTh} • ${event.source}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = event.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    MetricRow("พื้นที่", event.place)
                    MetricRow("พิกัด", "${"%.3f".format(event.latitude)}, ${"%.3f".format(event.longitude)}")
                    event.magnitude?.let {
                        MetricRow("ขนาดแผ่นดินไหว", "M${"%.1f".format(it)}", highlight = sevColor)
                    }
                    event.depthKm?.let {
                        MetricRow("ความลึกใต้ผิวโลก", "${"%.1f".format(it)} กม.")
                    }
                    MetricRow("เวลาที่บันทึก", formatRelativeTimeTh(event.timestampMs))
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onAnalyzeWithAi,
                    enabled = analysisState !is AiAnalysisUiState.Loading,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("analyze_event_ai_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("วิเคราะห์ผลกระทบด้วย AI")
                }

                if (!event.externalUrl.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.externalUrl))
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("open_event_source_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "เปิดแหล่งข้อมูลทางการ",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            when (analysisState) {
                AiAnalysisUiState.Idle -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "กดปุ่มด้านบนเพื่อให้ Gemini AI ประเมินรัศมีผลกระทบ ความเสี่ยงอาฟเตอร์ช็อก และวิธีรับมือ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AiAnalysisUiState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "กำลังวิเคราะห์ข้อมูลภัยพิบัติด้วย AI...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                is AiAnalysisUiState.Success -> {
                    AiAnalysisResultView(analysis = analysisState.analysis)
                }

                is AiAnalysisUiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = analysisState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    highlight: Color? = null
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = highlight ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighRiskZoneBottomSheet(
    zone: HighRiskZone,
    analysisState: AiAnalysisUiState,
    onAnalyzeWithAi: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hazard = LocalHazardColors.current
    val sevColor = severityColor(zone.compositeRiskScore)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RiskGaugeArc(score = zone.compositeRiskScore, size = 60.dp)
                Column(modifier = Modifier.weight(1f)) {
                    SeverityBadge(
                        score = zone.compositeRiskScore,
                        label = "พื้นที่เสี่ยงภัยสูง • ${zone.regionTh}"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = zone.nameTh,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                zone.primaryHazards.forEach { cat ->
                    Surface(
                        color = sevColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = cat.labelTh,
                            style = MaterialTheme.typography.labelSmall,
                            color = sevColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            zone.liveClimateSummary?.let { live ->
                Surface(
                    color = hazard.telemetryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, hazard.telemetry.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "สภาพอากาศตรวจวัดจริงล่าสุด (Open-Meteo)",
                            style = MaterialTheme.typography.labelSmall,
                            color = hazard.telemetry
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = live,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            RiskFactorCard(
                title = "1. ปัจจัยด้านภูมิศาสตร์ (Geography)",
                detail = zone.geographyFactor,
                accent = hazard.moderate
            )
            RiskFactorCard(
                title = "2. ปัจจัยด้านภูมิอากาศ (Climate)",
                detail = zone.climateFactor,
                accent = hazard.telemetry
            )
            RiskFactorCard(
                title = "3. ปัจจัยด้านโครงสร้างพื้นฐาน (Infrastructure)",
                detail = zone.infrastructureFactor,
                accent = hazard.high
            )

            Button(
                onClick = onAnalyzeWithAi,
                enabled = analysisState !is AiAnalysisUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("analyze_risk_zone_ai_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("ให้ AI วิเคราะห์เชิงลึกและแผนลดความเสี่ยง")
            }

            when (analysisState) {
                AiAnalysisUiState.Idle -> Unit
                AiAnalysisUiState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("AI กำลังวิเคราะห์ปัจจัยเสี่ยงเชิงพื้นที่...")
                    }
                }
                is AiAnalysisUiState.Success -> {
                    AiAnalysisResultView(analysis = analysisState.analysis)
                }
                is AiAnalysisUiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = analysisState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskFactorCard(
    title: String,
    detail: String,
    accent: Color
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = accent
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
