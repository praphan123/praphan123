package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.domain.model.DisasterEvent
import com.example.domain.model.FeedFilter
import com.example.domain.model.PushAlertConfig
import com.example.domain.model.PushAlertRecord
import com.example.domain.model.THAI_PROVINCE_PRESETS
import com.example.domain.model.ThaiProvincePreset
import com.example.domain.model.WeatherHazardSnapshot
import com.example.ui.components.AiAnalysisResultView
import com.example.ui.components.DisasterEventCard
import com.example.ui.components.EventDetailBottomSheet
import com.example.ui.components.PushAlertCenterSheet
import com.example.ui.components.PushAlertStatusCard
import com.example.ui.components.RiskGaugeArc
import com.example.ui.components.SeverityBadge
import com.example.ui.components.severityColor
import com.example.ui.theme.LocalHazardColors
import com.example.ui.viewmodel.AiAnalysisUiState
import com.example.ui.viewmodel.FcmSyncUiState
import com.example.ui.viewmodel.FeedUiState
import com.example.ui.viewmodel.WeatherUiState
import com.google.android.gms.location.LocationServices
import kotlin.math.roundToInt

@Composable
fun MonitorScreen(
    feedState: FeedUiState,
    weatherState: WeatherUiState,
    selectedFilter: FeedFilter,
    selectedProvince: ThaiProvincePreset,
    selectedEvent: DisasterEvent?,
    eventAnalysisState: AiAnalysisUiState,
    weatherAnalysisState: AiAnalysisUiState,
    pushAlertConfig: PushAlertConfig,
    pushAlerts: List<PushAlertRecord>,
    unreadPushCount: Int,
    fcmSyncState: FcmSyncUiState,
    isPushCenterOpen: Boolean,
    onSelectFilter: (FeedFilter) -> Unit,
    onSelectProvince: (ThaiProvincePreset) -> Unit,
    onLoadWeatherCoords: (String, Double, Double) -> Unit,
    onRefresh: () -> Unit,
    onOpenEvent: (DisasterEvent) -> Unit,
    onCloseEvent: () -> Unit,
    onAnalyzeEventWithAi: () -> Unit,
    onAnalyzeWeatherWithAi: () -> Unit,
    onDismissWeatherAnalysis: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenPushCenter: () -> Unit,
    onClosePushCenter: () -> Unit,
    onEnablePushAlerts: () -> Unit,
    onDisablePushAlerts: () -> Unit,
    onUpdatePushLocation: (String, Double, Double) -> Unit,
    onUpdatePushRadius: (Double) -> Unit,
    onRefreshFcmToken: () -> Unit,
    onSaveServerUrl: (String) -> Unit,
    onSendTestServerPush: (String) -> Unit,
    onClearPushAlerts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var locationNotice by remember { mutableStateOf<String?>(null) }

    @SuppressLint("MissingPermission")
    fun fetchCoarseLocation() {
        fusedClient.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    locationNotice = null
                    onLoadWeatherCoords("ตำแหน่งปัจจุบัน (GPS)", loc.latitude, loc.longitude)
                    if (pushAlertConfig.enabled) {
                        onUpdatePushLocation("ตำแหน่ง GPS ปัจจุบัน", loc.latitude, loc.longitude)
                    }
                } else {
                    locationNotice = "ยังไม่พบสัญญาณพิกัดล่าสุด แสดงผลพื้นที่ที่เลือกแทน"
                }
            }
            .addOnFailureListener {
                locationNotice = "ไม่สามารถอ่านพิกัดได้ แสดงผลจังหวัดที่เลือกแทน"
            }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            fetchCoarseLocation()
        } else {
            locationNotice = "ไม่ได้รับสิทธิ์เข้าถึงตำแหน่ง สามารถเลือกจังหวัดจากรายการได้"
        }
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .testTag("monitor_feed_list")
        ) {
            item {
                HeroRadarBanner(
                    feedState = feedState,
                    onRefresh = onRefresh,
                    onOpenMap = onOpenMap
                )
            }

            item {
                PushAlertStatusCard(
                    config = pushAlertConfig,
                    unreadCount = unreadPushCount,
                    onOpenCenter = onOpenPushCenter
                )
            }

            item {
                LocalWeatherHazardCard(
                    weatherState = weatherState,
                    selectedProvince = selectedProvince,
                    weatherAnalysisState = weatherAnalysisState,
                    locationNotice = locationNotice,
                    onSelectProvince = onSelectProvince,
                    onRequestCurrentLocation = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            fetchCoarseLocation()
                        } else {
                            locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    },
                    onAnalyzeWeather = onAnalyzeWeatherWithAi,
                    onDismissWeatherAnalysis = onDismissWeatherAnalysis
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ฟีดเหตุการณ์ภัยพิบัติเรียลไทม์ (USGS & NASA)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(FeedFilter.entries) { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { onSelectFilter(filter) },
                                label = { Text(filter.labelTh) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("filter_chip_${filter.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            when (feedState) {
                FeedUiState.Loading -> {
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "กำลังซิงค์ข้อมูลแผ่นดินไหวและภัยพิบัติทั่วโลก...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                is FeedUiState.Error -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.CloudOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = feedState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Button(
                                    onClick = onRefresh,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("retry_feed_button")
                                ) {
                                    Text("ลองเชื่อมต่ออีกครั้ง")
                                }
                            }
                        }
                    }
                }

                is FeedUiState.Success -> {
                    feedState.warningMessage?.let { warning ->
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = warning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    if (feedState.filteredEvents.isEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp)
                                ) {
                                    Text(
                                        text = "ไม่พบเหตุการณ์ภัยพิบัติในหมวดหมู่ที่เลือก",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "ลองเปลี่ยนตัวกรองเป็น \"ทั้งหมด\" เพื่อดูเหตุการณ์ล่าสุดทั่วโลก",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            items = feedState.filteredEvents,
                            key = { it.id }
                        ) { event ->
                            DisasterEventCard(
                                event = event,
                                onClick = { onOpenEvent(event) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedEvent != null) {
        EventDetailBottomSheet(
            event = selectedEvent,
            analysisState = eventAnalysisState,
            onAnalyzeWithAi = onAnalyzeEventWithAi,
            onDismiss = onCloseEvent
        )
    }

    if (isPushCenterOpen) {
        PushAlertCenterSheet(
            config = pushAlertConfig,
            alerts = pushAlerts,
            fcmSyncState = fcmSyncState,
            onEnableAlerts = onEnablePushAlerts,
            onDisableAlerts = onDisablePushAlerts,
            onUpdateLocation = onUpdatePushLocation,
            onUpdateRadius = onUpdatePushRadius,
            onRefreshToken = onRefreshFcmToken,
            onSaveServerUrl = onSaveServerUrl,
            onSendTestServerPush = onSendTestServerPush,
            onClearAlerts = onClearPushAlerts,
            onDismiss = onClosePushCenter
        )
    }
}

@Composable
private fun HeroRadarBanner(
    feedState: FeedUiState,
    onRefresh: () -> Unit,
    onOpenMap: () -> Unit
) {
    val hazard = LocalHazardColors.current
    val totalCount = (feedState as? FeedUiState.Success)?.allEvents?.size ?: 0
    val highRiskCount = (feedState as? FeedUiState.Success)?.allEvents?.count { it.severityScore >= 4 } ?: 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
            .clip(MaterialTheme.shapes.large)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                MaterialTheme.shapes.large
            )
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_hero_radar_1795527564440),
            contentDescription = stringResource(R.string.cd_hero_radar),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.30f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
                        )
                    )
                )
        )
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = hazard.lowContainer.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(50.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(hazard.low)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE TELEMETRY",
                            style = MaterialTheme.typography.labelSmall,
                            color = hazard.low
                        )
                    }
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("refresh_telemetry_button")
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.action_refresh),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ศูนย์วิเคราะห์และเฝ้าระวังภัยพิบัติ",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "เหตุการณ์ที่ติดตาม: $totalCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "ความเสี่ยงสูง: $highRiskCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (highRiskCount > 0) hazard.high else hazard.low
                        )
                    }
                    FilledTonalButton(
                        onClick = onOpenMap,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("hero_open_map_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Map,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("เปิดแผนที่ภัย", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalWeatherHazardCard(
    weatherState: WeatherUiState,
    selectedProvince: ThaiProvincePreset,
    weatherAnalysisState: AiAnalysisUiState,
    locationNotice: String?,
    onSelectProvince: (ThaiProvincePreset) -> Unit,
    onRequestCurrentLocation: () -> Unit,
    onAnalyzeWeather: () -> Unit,
    onDismissWeatherAnalysis: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                MaterialTheme.shapes.large
            )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box {
                    OutlinedButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("province_selector_button")
                    ) {
                        Text(
                            text = selectedProvince.nameTh,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "เลือกจังหวัด"
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        THAI_PROVINCE_PRESETS.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text("${preset.nameTh} (${preset.regionTh})") },
                                onClick = {
                                    menuExpanded = false
                                    onSelectProvince(preset)
                                }
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onRequestCurrentLocation,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("use_gps_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = stringResource(R.string.action_use_location),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("พิกัดฉัน", style = MaterialTheme.typography.labelLarge)
                }
            }

            locationNotice?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            when (weatherState) {
                WeatherUiState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "กำลังตรวจวัดสภาพอากาศและความเสี่ยงน้ำท่วม...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is WeatherUiState.Error -> {
                    Text(
                        text = weatherState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                is WeatherUiState.Success -> {
                    WeatherSnapshotContent(
                        snapshot = weatherState.snapshot,
                        weatherAnalysisState = weatherAnalysisState,
                        onAnalyzeWeather = onAnalyzeWeather,
                        onDismissWeatherAnalysis = onDismissWeatherAnalysis
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherSnapshotContent(
    snapshot: WeatherHazardSnapshot,
    weatherAnalysisState: AiAnalysisUiState,
    onAnalyzeWeather: () -> Unit,
    onDismissWeatherAnalysis: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        RiskGaugeArc(score = snapshot.riskScore)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SeverityBadge(
                    score = snapshot.riskScore,
                    label = "ความเสี่ยงอากาศ: ${snapshot.riskLabelTh}"
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${snapshot.locationName} • ${snapshot.weatherConditionTh}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = snapshot.riskReasonTh,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        WeatherMetricTile(
            icon = Icons.Filled.Thermostat,
            label = "อุณหภูมิ",
            value = "${snapshot.temperatureC.roundToInt()}°C",
            modifier = Modifier.weight(1f)
        )
        WeatherMetricTile(
            icon = Icons.Filled.WaterDrop,
            label = "ฝน/ชม.",
            value = "${"%.1f".format(snapshot.precipitationMm)} มม.",
            modifier = Modifier.weight(1f)
        )
        WeatherMetricTile(
            icon = Icons.Filled.Air,
            label = "ลมกระโชก",
            value = "${snapshot.windGustsKmh.roundToInt()} กม./ชม.",
            modifier = Modifier.weight(1f)
        )
    }

    Button(
        onClick = onAnalyzeWeather,
        enabled = weatherAnalysisState !is AiAnalysisUiState.Loading,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("analyze_weather_ai_button")
    ) {
        if (weatherAnalysisState is AiAnalysisUiState.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.onSecondary,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("AI กำลังวิเคราะห์สภาพอากาศ...")
        } else {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("ให้ AI ประเมินแนวโน้มน้ำท่วมและพายุพื้นที่นี้")
        }
    }

    AnimatedVisibility(visible = weatherAnalysisState is AiAnalysisUiState.Success || weatherAnalysisState is AiAnalysisUiState.Error) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "ผลการวิเคราะห์สภาพอากาศด้วย AI",
                    style = MaterialTheme.typography.titleSmall,
                    color = severityColor(snapshot.riskScore)
                )
                IconButton(
                    onClick = onDismissWeatherAnalysis,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "ปิดผลวิเคราะห์"
                    )
                }
            }
            when (weatherAnalysisState) {
                is AiAnalysisUiState.Success -> AiAnalysisResultView(analysis = weatherAnalysisState.analysis)
                is AiAnalysisUiState.Error -> Text(
                    text = weatherAnalysisState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                else -> Unit
            }
        }
    }
}

@Composable
private fun WeatherMetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
