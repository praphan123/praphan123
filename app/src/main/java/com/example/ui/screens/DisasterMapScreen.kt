package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.domain.model.DisasterCategory
import com.example.domain.model.DisasterEvent
import com.example.domain.model.HighRiskZone
import com.example.domain.model.MapDisasterTypeFilter
import com.example.domain.model.MapTimePeriod
import com.example.ui.components.EventDetailBottomSheet
import com.example.ui.components.HighRiskZoneBottomSheet
import com.example.ui.components.severityColor
import com.example.ui.data.WorldGeoPolygons
import com.example.ui.theme.LocalHazardColors
import com.example.ui.viewmodel.AiAnalysisUiState
import com.example.ui.viewmodel.FeedUiState
import com.google.android.gms.location.LocationServices
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sinh

@Composable
fun DisasterMapScreen(
    mapEventsState: FeedUiState,
    highRiskZones: List<HighRiskZone>,
    selectedTimePeriod: MapTimePeriod,
    selectedTypeFilter: MapDisasterTypeFilter,
    showCurrentLayer: Boolean,
    showHistoricalLayer: Boolean,
    showRiskZonesLayer: Boolean,
    selectedEvent: DisasterEvent?,
    eventAnalysisState: AiAnalysisUiState,
    selectedRiskZone: HighRiskZone?,
    riskZoneAnalysisState: AiAnalysisUiState,
    onSelectTimePeriod: (MapTimePeriod) -> Unit,
    onSelectTypeFilter: (MapDisasterTypeFilter) -> Unit,
    onToggleCurrentLayer: () -> Unit,
    onToggleHistoricalLayer: () -> Unit,
    onToggleRiskZonesLayer: () -> Unit,
    onRefreshMap: () -> Unit,
    onOpenEvent: (DisasterEvent) -> Unit,
    onCloseEvent: () -> Unit,
    onAnalyzeEventWithAi: () -> Unit,
    onOpenRiskZone: (HighRiskZone) -> Unit,
    onCloseRiskZone: () -> Unit,
    onAnalyzeRiskZoneWithAi: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Default center on Thailand
    var centerLat by remember { mutableStateOf(13.5) }
    var centerLon by remember { mutableStateOf(100.5) }
    var zoomLevel by remember { mutableFloatStateOf(14f) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    var userLatLon by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    @SuppressLint("MissingPermission")
    fun centerOnUserGps() {
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                userLatLon = loc.latitude to loc.longitude
                centerLat = loc.latitude
                centerLon = loc.longitude
                zoomLevel = 22f
            }
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) centerOnUserGps()
    }

    val events = (mapEventsState as? FeedUiState.Success)?.filteredEvents.orEmpty()
    val currentCount = events.count { !it.isHistorical }
    val historicalCount = events.count { it.isHistorical }

    val hazard = LocalHazardColors.current
    val colorScheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()

    Box(modifier = modifier.fillMaxSize()) {
        // Interactive Web Mercator Canvas Map
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .testTag("interactive_disaster_map_canvas")
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val newZoom = (zoomLevel * zoom).coerceIn(1.1f, 60f)
                        val worldSpan = width * newZoom
                        val curNormX = lonToNormX(centerLon)
                        val curNormY = latToNormY(centerLat)
                        val nextNormX = (curNormX - pan.x / worldSpan).coerceIn(0.0, 1.0)
                        val nextNormY = (curNormY - pan.y / worldSpan).coerceIn(0.06, 0.94)
                        centerLon = nextNormX * 360.0 - 180.0
                        centerLat = normYToLat(nextNormY)
                        zoomLevel = newZoom
                    }
                }
                .pointerInput(events, highRiskZones, zoomLevel, centerLat, centerLon) {
                    detectTapGestures { tapOffset ->
                        val w = canvasSize.width.toFloat().coerceAtLeast(1f)
                        val h = canvasSize.height.toFloat().coerceAtLeast(1f)
                        val worldSpan = w * zoomLevel
                        val tapRadiusPx = 28.dp.toPx()

                        // 1. Hit-test disaster event markers (current & historical)
                        var hitEvent: DisasterEvent? = null
                        var bestEventDist = Float.MAX_VALUE
                        events.forEach { ev ->
                            val pos = latLonToScreen(
                                ev.latitude, ev.longitude, w, h, centerLat, centerLon, worldSpan
                            )
                            val dist = hypot(tapOffset.x - pos.x, tapOffset.y - pos.y)
                            if (dist <= tapRadiusPx && dist < bestEventDist) {
                                bestEventDist = dist
                                hitEvent = ev
                            }
                        }
                        if (hitEvent != null) {
                            onOpenEvent(hitEvent!!)
                            return@detectTapGestures
                        }

                        // 2. Hit-test high-risk zones
                        var hitZone: HighRiskZone? = null
                        var bestZoneDist = Float.MAX_VALUE
                        highRiskZones.forEach { zone ->
                            val pos = latLonToScreen(
                                zone.latitude, zone.longitude, w, h, centerLat, centerLon, worldSpan
                            )
                            val radiusPx = zoneRadiusPx(
                                zone, worldSpan, 22.dp.toPx(), 150.dp.toPx()
                            )
                            val dist = hypot(tapOffset.x - pos.x, tapOffset.y - pos.y)
                            if (dist <= maxOf(radiusPx, tapRadiusPx) && dist < bestZoneDist) {
                                bestZoneDist = dist
                                hitZone = zone
                            }
                        }
                        if (hitZone != null) {
                            onOpenRiskZone(hitZone!!)
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val worldSpan = w * zoomLevel

            // 1. Graticule (Lat/Lon coordinate grid)
            drawGraticule(
                w = w,
                h = h,
                centerLat = centerLat,
                centerLon = centerLon,
                worldSpan = worldSpan,
                zoomLevel = zoomLevel,
                gridColor = colorScheme.outline.copy(alpha = 0.22f)
            )

            // 2. Continents & Islands Fill + Stroke
            val landFill = colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
            val landStroke = colorScheme.outline.copy(alpha = 0.65f)
            drawGeoPolygon(
                points = WorldGeoPolygons.EURASIA_SE_COAST,
                w = w, h = h, centerLat = centerLat, centerLon = centerLon, worldSpan = worldSpan,
                fillColor = landFill, strokeColor = landStroke, strokeWidth = 1.2.dp.toPx()
            )
            WorldGeoPolygons.ISLAND_POLYGONS.forEach { poly ->
                drawGeoPolygon(
                    points = poly,
                    w = w, h = h, centerLat = centerLat, centerLon = centerLon, worldSpan = worldSpan,
                    fillColor = landFill, strokeColor = landStroke, strokeWidth = 1.2.dp.toPx()
                )
            }

            // 3. Highlighted Thailand Territory Border & Major River Corridors
            drawGeoPolygon(
                points = WorldGeoPolygons.THAILAND_OUTLINE,
                w = w, h = h, centerLat = centerLat, centerLon = centerLon, worldSpan = worldSpan,
                fillColor = colorScheme.surfaceVariant.copy(alpha = 0.85f),
                strokeColor = hazard.telemetry.copy(alpha = 0.85f),
                strokeWidth = 1.8.dp.toPx()
            )

            WorldGeoPolygons.MAJOR_RIVER_CORRIDORS.forEach { river ->
                drawGeoPolyline(
                    points = river,
                    w = w, h = h, centerLat = centerLat, centerLon = centerLon, worldSpan = worldSpan,
                    color = hazard.telemetry.copy(alpha = 0.55f),
                    strokeWidth = 1.6.dp.toPx()
                )
            }

            // 4. Tectonic Fault & Subduction Lines
            WorldGeoPolygons.TECTONIC_FAULT_LINES.forEach { fault ->
                drawGeoPolyline(
                    points = fault,
                    w = w, h = h, centerLat = centerLat, centerLon = centerLon, worldSpan = worldSpan,
                    color = hazard.high.copy(alpha = 0.55f),
                    strokeWidth = 1.6.dp.toPx(),
                    dashed = true
                )
            }

            // 5. High-Risk Zones (Geography + Climate + Infrastructure)
            highRiskZones.forEach { zone ->
                val center = latLonToScreen(
                    zone.latitude, zone.longitude, w, h, centerLat, centerLon, worldSpan
                )
                if (center.x in -200f..(w + 200f) && center.y in -200f..(h + 200f)) {
                    val zoneColor = when {
                        zone.compositeRiskScore >= 5 -> hazard.critical
                        zone.compositeRiskScore == 4 -> hazard.high
                        else -> hazard.moderate
                    }
                    val radiusPx = zoneRadiusPx(zone, worldSpan, 22.dp.toPx(), 150.dp.toPx())
                    drawCircle(
                        color = zoneColor.copy(alpha = 0.16f),
                        radius = radiusPx,
                        center = center
                    )
                    drawCircle(
                        color = zoneColor.copy(alpha = 0.68f),
                        radius = radiusPx,
                        center = center,
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                        )
                    )
                    drawCircle(
                        color = zoneColor,
                        radius = 4.dp.toPx(),
                        center = center
                    )

                    if (zoomLevel >= 6f) {
                        val labelText = zone.nameTh.substringBefore(" (")
                        drawMapLabel(
                            textMeasurer = textMeasurer,
                            text = labelText,
                            center = Offset(center.x, center.y - radiusPx.coerceAtMost(48f) - 10f),
                            textColor = zoneColor
                        )
                    }
                }
            }

            // 6. Historical Disaster Events (Diamond / Hollow Ring Markers)
            events.filter { it.isHistorical }.forEach { ev ->
                val pos = latLonToScreen(
                    ev.latitude, ev.longitude, w, h, centerLat, centerLon, worldSpan
                )
                if (pos.x in -50f..(w + 50f) && pos.y in -50f..(h + 50f)) {
                    val evColor = eventCategoryColor(ev.category, hazard.critical, hazard.high, hazard.moderate, hazard.telemetry, hazard.low)
                    val markerSize = (6f + ev.severityScore * 2.2f).dp.toPx() * 0.65f
                    val diamond = Path().apply {
                        moveTo(pos.x, pos.y - markerSize)
                        lineTo(pos.x + markerSize, pos.y)
                        lineTo(pos.x, pos.y + markerSize)
                        lineTo(pos.x - markerSize, pos.y)
                        close()
                    }
                    drawPath(diamond, color = evColor.copy(alpha = 0.28f))
                    drawPath(
                        diamond,
                        color = evColor.copy(alpha = 0.92f),
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                }
            }

            // 7. Current Disaster Events (Glowing Solid Pulse Markers)
            events.filter { !it.isHistorical }.forEach { ev ->
                val pos = latLonToScreen(
                    ev.latitude, ev.longitude, w, h, centerLat, centerLon, worldSpan
                )
                if (pos.x in -50f..(w + 50f) && pos.y in -50f..(h + 50f)) {
                    val evColor = eventCategoryColor(ev.category, hazard.critical, hazard.high, hazard.moderate, hazard.telemetry, hazard.low)
                    val radius = (5f + ev.severityScore * 1.8f).dp.toPx() * 0.75f
                    drawCircle(
                        color = evColor.copy(alpha = 0.24f),
                        radius = radius * 1.8f,
                        center = pos
                    )
                    drawCircle(
                        color = evColor,
                        radius = radius,
                        center = pos
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = radius * 0.35f,
                        center = pos
                    )
                }
            }

            // 8. User GPS Marker (if fetched)
            userLatLon?.let { (uLat, uLon) ->
                val pos = latLonToScreen(
                    uLat, uLon, w, h, centerLat, centerLon, worldSpan
                )
                drawCircle(
                    color = hazard.telemetry.copy(alpha = 0.25f),
                    radius = 18.dp.toPx(),
                    center = pos
                )
                drawCircle(
                    color = Color.White,
                    radius = 7.dp.toPx(),
                    center = pos
                )
                drawCircle(
                    color = hazard.telemetry,
                    radius = 5.dp.toPx(),
                    center = pos
                )
            }
        }

        // Top Floating Filter Panel (Time Period, Disaster Type, Layer Toggles)
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 10.dp)
        ) {
            // Header + Telemetry Status Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            ) {
                Surface(
                    color = colorScheme.surface.copy(alpha = 0.90f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.border(
                        1.dp,
                        colorScheme.outline.copy(alpha = 0.45f),
                        RoundedCornerShape(14.dp)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Layers,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "แผนที่วิเคราะห์ภัยพิบัติเชิงพื้นที่",
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "ปัจจุบัน $currentCount • ย้อนหลัง $historicalCount • พื้นที่เสี่ยง ${highRiskZones.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                FilledTonalIconButton(
                    onClick = onRefreshMap,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("map_refresh_button"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = colorScheme.surface.copy(alpha = 0.92f)
                    )
                ) {
                    if (mapEventsState is FeedUiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_refresh)
                        )
                    }
                }
            }

            // Time Period Filter Row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(MapTimePeriod.entries) { period ->
                    FilterChip(
                        selected = selectedTimePeriod == period,
                        onClick = { onSelectTimePeriod(period) },
                        label = { Text(period.labelTh) },
                        leadingIcon = if (period.includesHistorical) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = colorScheme.surface.copy(alpha = 0.90f),
                            selectedContainerColor = colorScheme.primaryContainer,
                            selectedLabelColor = colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("map_time_${period.name.lowercase()}")
                    )
                }
            }

            // Disaster Type Filter Row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(MapDisasterTypeFilter.entries) { typeFilter ->
                    FilterChip(
                        selected = selectedTypeFilter == typeFilter,
                        onClick = { onSelectTypeFilter(typeFilter) },
                        label = { Text(typeFilter.labelTh) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = colorScheme.surface.copy(alpha = 0.90f),
                            selectedContainerColor = colorScheme.secondaryContainer,
                            selectedLabelColor = colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("map_type_${typeFilter.name.lowercase()}")
                    )
                }
            }

            // Layer Toggle Chips (Current / Historical / High-Risk Zones)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    LayerToggleChip(
                        label = "ปัจจุบัน",
                        selected = showCurrentLayer,
                        dotColor = hazard.telemetry,
                        onClick = onToggleCurrentLayer,
                        testTag = "layer_toggle_current"
                    )
                }
                item {
                    LayerToggleChip(
                        label = "ย้อนหลัง",
                        selected = showHistoricalLayer,
                        dotColor = hazard.moderate,
                        onClick = onToggleHistoricalLayer,
                        testTag = "layer_toggle_historical"
                    )
                }
                item {
                    LayerToggleChip(
                        label = "พื้นที่เสี่ยงสูง",
                        selected = showRiskZonesLayer,
                        dotColor = hazard.critical,
                        onClick = onToggleRiskZonesLayer,
                        testTag = "layer_toggle_risk_zones"
                    )
                }
            }

            (mapEventsState as? FeedUiState.Success)?.warningMessage?.let { cacheNotice ->
                Surface(
                    color = if (mapEventsState.isFromCache) {
                        hazard.moderateContainer.copy(alpha = 0.94f)
                    } else {
                        colorScheme.surface.copy(alpha = 0.90f)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .border(
                            1.dp,
                            if (mapEventsState.isFromCache) hazard.moderate else colorScheme.outline.copy(alpha = 0.45f),
                            RoundedCornerShape(12.dp)
                        )
                        .testTag("map_cache_status_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        if (mapEventsState.isFromCache) {
                            Icon(
                                imageVector = Icons.Filled.CloudOff,
                                contentDescription = null,
                                tint = hazard.moderate,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = cacheNotice,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (mapEventsState.isFromCache) colorScheme.onPrimaryContainer else colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Right-Side Map Zoom & Region Navigation Controls
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 78.dp)
        ) {
            FilledTonalIconButton(
                onClick = { zoomLevel = (zoomLevel * 1.45f).coerceIn(1.1f, 60f) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("map_zoom_in"),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_zoom_in))
            }
            FilledTonalIconButton(
                onClick = { zoomLevel = (zoomLevel / 1.45f).coerceIn(1.1f, 60f) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("map_zoom_out"),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.cd_zoom_out))
            }
            FilledTonalIconButton(
                onClick = {
                    centerLat = 13.5
                    centerLon = 100.5
                    zoomLevel = 14f
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("map_center_thailand"),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Icon(Icons.Filled.Explore, contentDescription = stringResource(R.string.cd_center_thailand))
            }
            FilledTonalIconButton(
                onClick = {
                    centerLat = 12.0
                    centerLon = 100.0
                    zoomLevel = 1.6f
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("map_center_global"),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Icon(Icons.Filled.Public, contentDescription = stringResource(R.string.cd_center_global))
            }
            FilledTonalIconButton(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) centerOnUserGps()
                    else locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("map_my_location"),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = colorScheme.primaryContainer
                )
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.action_use_location))
            }
        }

        // Bottom Map Legend & Coordinate Telemetry HUD
        Surface(
            color = colorScheme.surface.copy(alpha = 0.92f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .border(1.dp, colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LegendDot(color = hazard.telemetry, text = "ปัจจุบัน")
                    LegendDot(color = hazard.moderate, text = "ย้อนหลัง", hollow = true)
                    LegendDot(color = hazard.critical, text = "เสี่ยงสูง")
                }
                Text(
                    text = "${"%.2f".format(centerLat)}°, ${"%.2f".format(centerLon)}°",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
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

    if (selectedRiskZone != null) {
        HighRiskZoneBottomSheet(
            zone = selectedRiskZone,
            analysisState = riskZoneAnalysisState,
            onAnalyzeWithAi = onAnalyzeRiskZoneWithAi,
            onDismiss = onCloseRiskZone
        )
    }
}

@Composable
private fun LayerToggleChip(
    label: String,
    selected: Boolean,
    dotColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        color = if (selected) {
            dotColor.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        },
        shape = RoundedCornerShape(50.dp),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(50.dp))
            .border(
                1.dp,
                if (selected) dotColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                RoundedCornerShape(50.dp)
            )
            .testTag(testTag)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (selected) dotColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LegendDot(
    color: Color,
    text: String,
    hollow: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .then(
                    if (hollow) {
                        Modifier.border(2.dp, color, CircleShape)
                    } else {
                        Modifier.background(color)
                    }
                )
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// --- Web Mercator Projection & Canvas Helpers ---

private fun lonToNormX(lon: Double): Double = ((lon + 180.0) / 360.0).coerceIn(0.0, 1.0)

private fun latToNormY(lat: Double): Double {
    val clamped = lat.coerceIn(-85.0, 85.0)
    val sinLat = sin(Math.toRadians(clamped))
    val y = 0.5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI)
    return y.coerceIn(0.0, 1.0)
}

private fun normYToLat(normY: Double): Double {
    val n = PI * (1.0 - 2.0 * normY)
    return Math.toDegrees(atan(sinh(n))).coerceIn(-85.0, 85.0)
}

private fun latLonToScreen(
    lat: Double,
    lon: Double,
    canvasW: Float,
    canvasH: Float,
    centerLat: Double,
    centerLon: Double,
    worldSpan: Float
): Offset {
    val centerX = lonToNormX(centerLon) * worldSpan
    val centerY = latToNormY(centerLat) * worldSpan
    val x = (canvasW / 2f) + (lonToNormX(lon) * worldSpan - centerX).toFloat()
    val y = (canvasH / 2f) + (latToNormY(lat) * worldSpan - centerY).toFloat()
    return Offset(x, y)
}

private fun zoneRadiusPx(
    zone: HighRiskZone,
    worldSpan: Float,
    minPx: Float,
    maxPx: Float
): Float {
    val cosLat = cos(Math.toRadians(zone.latitude)).coerceAtLeast(0.2)
    val radiusNorm = zone.radiusKm / (40075.0 * cosLat)
    return (radiusNorm * worldSpan).toFloat().coerceIn(minPx, maxPx)
}

private fun eventCategoryColor(
    category: DisasterCategory,
    critical: Color,
    high: Color,
    moderate: Color,
    telemetry: Color,
    low: Color
): Color {
    return when (category) {
        DisasterCategory.EARTHQUAKE -> moderate
        DisasterCategory.FLOOD -> telemetry
        DisasterCategory.STORM -> telemetry
        DisasterCategory.WILDFIRE -> high
        DisasterCategory.VOLCANO -> critical
        DisasterCategory.LANDSLIDE -> high
        DisasterCategory.OTHER -> low
    }
}

private fun DrawScope.drawGraticule(
    w: Float,
    h: Float,
    centerLat: Double,
    centerLon: Double,
    worldSpan: Float,
    zoomLevel: Float,
    gridColor: Color
) {
    val stepDeg = when {
        zoomLevel >= 12f -> 5
        zoomLevel >= 4f -> 15
        else -> 30
    }
    var lon = -180
    while (lon <= 180) {
        val x = latLonToScreen(0.0, lon.toDouble(), w, h, centerLat, centerLon, worldSpan).x
        if (x in 0f..w) {
            drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
        }
        lon += stepDeg
    }
    var lat = -75
    while (lat <= 75) {
        val y = latLonToScreen(lat.toDouble(), 0.0, w, h, centerLat, centerLon, worldSpan).y
        if (y in 0f..h) {
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        lat += stepDeg
    }
}

private fun DrawScope.drawGeoPolygon(
    points: List<Pair<Double, Double>>,
    w: Float,
    h: Float,
    centerLat: Double,
    centerLon: Double,
    worldSpan: Float,
    fillColor: Color,
    strokeColor: Color,
    strokeWidth: Float
) {
    if (points.size < 3) return
    val path = Path()
    points.forEachIndexed { index, (lat, lon) ->
        val pt = latLonToScreen(lat, lon, w, h, centerLat, centerLon, worldSpan)
        if (index == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
    }
    path.close()
    drawPath(path, color = fillColor)
    drawPath(path, color = strokeColor, style = Stroke(width = strokeWidth))
}

private fun DrawScope.drawGeoPolyline(
    points: List<Pair<Double, Double>>,
    w: Float,
    h: Float,
    centerLat: Double,
    centerLon: Double,
    worldSpan: Float,
    color: Color,
    strokeWidth: Float,
    dashed: Boolean = false
) {
    if (points.size < 2) return
    val path = Path()
    points.forEachIndexed { index, (lat, lon) ->
        val pt = latLonToScreen(lat, lon, w, h, centerLat, centerLon, worldSpan)
        if (index == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f) else null
        )
    )
}

private fun DrawScope.drawMapLabel(
    textMeasurer: TextMeasurer,
    text: String,
    center: Offset,
    textColor: Color
) {
    val result = textMeasurer.measure(
        text = text,
        style = androidx.compose.ui.text.TextStyle(
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp)
        )
    )
    val topLeft = Offset(
        x = (center.x - result.size.width / 2f).coerceIn(4f, size.width - result.size.width - 4f),
        y = (center.y - result.size.height / 2f).coerceIn(4f, size.height - result.size.height - 4f)
    )
    drawText(result, topLeft = topLeft)
}
