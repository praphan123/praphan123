package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.security.configureAppCheck
import com.example.ui.screens.AiAnalyzerScreen
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.CameraDisasterCaptureScreen
import com.example.ui.screens.DisasterMapScreen
import com.example.ui.screens.MonitorScreen
import com.example.ui.screens.PreparednessScreen
import com.example.ui.theme.DisasterPulseTheme
import com.example.ui.viewmodel.DisasterViewModel

const val ROUTE_MONITOR = "monitor"
const val ROUTE_MAP = "disaster_map"
const val ROUTE_AI_ANALYZER = "ai_analyzer"
const val ROUTE_CAMERA_CAPTURE = "camera_capture"
const val ROUTE_PREPAREDNESS = "preparedness"
const val ROUTE_ANALYTICS = "analytics"

private data class NavDestinationItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val NAV_ITEMS = listOf(
    NavDestinationItem(
        route = ROUTE_MONITOR,
        labelRes = R.string.nav_monitor,
        selectedIcon = Icons.Filled.Radar,
        unselectedIcon = Icons.Outlined.Radar,
        testTag = "nav_monitor"
    ),
    NavDestinationItem(
        route = ROUTE_MAP,
        labelRes = R.string.nav_map,
        selectedIcon = Icons.Filled.Map,
        unselectedIcon = Icons.Outlined.Map,
        testTag = "nav_map"
    ),
    NavDestinationItem(
        route = ROUTE_AI_ANALYZER,
        labelRes = R.string.nav_ai_analyzer,
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        testTag = "nav_ai_analyzer"
    ),
    NavDestinationItem(
        route = ROUTE_PREPAREDNESS,
        labelRes = R.string.nav_preparedness,
        selectedIcon = Icons.Filled.HealthAndSafety,
        unselectedIcon = Icons.Outlined.HealthAndSafety,
        testTag = "nav_preparedness"
    ),
    NavDestinationItem(
        route = ROUTE_ANALYTICS,
        labelRes = R.string.nav_analytics,
        selectedIcon = Icons.Filled.Analytics,
        unselectedIcon = Icons.Outlined.Analytics,
        testTag = "nav_analytics"
    )
)

class MainActivity : ComponentActivity() {
    private val latestNotificationIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before setContent or ViewModel access
        configureAppCheck(this, intent)
        latestNotificationIntent.value = intent

        enableEdgeToEdge()
        setContent {
            DisasterPulseTheme {
                DisasterPulseApp(
                    notificationIntent = latestNotificationIntent.value,
                    onConsumeNotificationIntent = { latestNotificationIntent.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        latestNotificationIntent.value = intent
    }
}

@Composable
fun DisasterPulseApp(
    notificationIntent: Intent? = null,
    onConsumeNotificationIntent: () -> Unit = {},
    viewModel: DisasterViewModel = viewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val feedState by viewModel.feedState.collectAsStateWithLifecycle()
    val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedProvince by viewModel.selectedProvince.collectAsStateWithLifecycle()
    val selectedEvent by viewModel.selectedEvent.collectAsStateWithLifecycle()
    val eventAnalysisState by viewModel.eventAnalysisState.collectAsStateWithLifecycle()
    val weatherAnalysisState by viewModel.weatherAnalysisState.collectAsStateWithLifecycle()
    val selectedPhotoUri by viewModel.selectedPhotoUri.collectAsStateWithLifecycle()
    val photoAnalysisState by viewModel.photoAnalysisState.collectAsStateWithLifecycle()
    val capturedBitmap by viewModel.capturedBitmap.collectAsStateWithLifecycle()
    val cameraAnalysisState by viewModel.cameraAnalysisState.collectAsStateWithLifecycle()
    val advisorStreamState by viewModel.advisorStreamState.collectAsStateWithLifecycle()
    val checkedKitIds by viewModel.checkedKitIds.collectAsStateWithLifecycle()

    // FCM Push Alert states
    val pushAlertConfig by viewModel.pushAlertConfig.collectAsStateWithLifecycle()
    val pushAlerts by viewModel.pushAlerts.collectAsStateWithLifecycle()
    val unreadPushCount by viewModel.unreadPushCount.collectAsStateWithLifecycle()
    val fcmSyncState by viewModel.fcmSyncState.collectAsStateWithLifecycle()
    val isPushCenterOpen by viewModel.isPushCenterOpen.collectAsStateWithLifecycle()

    LaunchedEffect(notificationIntent) {
        if (notificationIntent != null) {
            viewModel.handleNotificationIntent(notificationIntent)
            onConsumeNotificationIntent()
        }
    }

    // Map states
    val mapEventsState by viewModel.mapEventsState.collectAsStateWithLifecycle()
    val highRiskZones by viewModel.highRiskZones.collectAsStateWithLifecycle()
    val mapTimePeriod by viewModel.mapTimePeriod.collectAsStateWithLifecycle()
    val mapTypeFilter by viewModel.mapTypeFilter.collectAsStateWithLifecycle()
    val showCurrentLayer by viewModel.showCurrentLayer.collectAsStateWithLifecycle()
    val showHistoricalLayer by viewModel.showHistoricalLayer.collectAsStateWithLifecycle()
    val showRiskZonesLayer by viewModel.showRiskZonesLayer.collectAsStateWithLifecycle()
    val selectedRiskZone by viewModel.selectedRiskZone.collectAsStateWithLifecycle()
    val riskZoneAnalysisState by viewModel.riskZoneAnalysisState.collectAsStateWithLifecycle()

    fun navigateToRoute(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp
        val showPrimaryNav = currentDestination?.route != ROUTE_CAMERA_CAPTURE

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!useNavRail && showPrimaryNav) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NAV_ITEMS.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == item.route
                            } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToRoute(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(item.labelRes),
                                        maxLines = 1
                                    )
                                },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail && showPrimaryNav) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        NAV_ITEMS.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == item.route
                            } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToRoute(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_MONITOR,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_MONITOR) {
                        MonitorScreen(
                            feedState = feedState,
                            weatherState = weatherState,
                            selectedFilter = selectedFilter,
                            selectedProvince = selectedProvince,
                            selectedEvent = selectedEvent,
                            eventAnalysisState = eventAnalysisState,
                            weatherAnalysisState = weatherAnalysisState,
                            pushAlertConfig = pushAlertConfig,
                            pushAlerts = pushAlerts,
                            unreadPushCount = unreadPushCount,
                            fcmSyncState = fcmSyncState,
                            isPushCenterOpen = isPushCenterOpen,
                            onSelectFilter = viewModel::selectFilter,
                            onSelectProvince = viewModel::selectProvince,
                            onLoadWeatherCoords = viewModel::loadWeatherForCoordinates,
                            onRefresh = viewModel::refreshAllTelemetry,
                            onOpenEvent = viewModel::openEventDetail,
                            onCloseEvent = viewModel::closeEventDetail,
                            onAnalyzeEventWithAi = viewModel::analyzeSelectedEventWithAi,
                            onAnalyzeWeatherWithAi = viewModel::analyzeLocalWeatherWithAi,
                            onDismissWeatherAnalysis = viewModel::dismissWeatherAnalysis,
                            onOpenMap = { navigateToRoute(ROUTE_MAP) },
                            onOpenPushCenter = viewModel::openPushAlertCenter,
                            onClosePushCenter = viewModel::closePushAlertCenter,
                            onEnablePushAlerts = viewModel::enablePushAlerts,
                            onDisablePushAlerts = viewModel::disablePushAlerts,
                            onUpdatePushLocation = viewModel::updatePushAlertLocation,
                            onUpdatePushRadius = viewModel::updatePushAlertRadius,
                            onRefreshFcmToken = viewModel::refreshFcmToken,
                            onSaveServerUrl = viewModel::saveFcmServerUrl,
                            onSendTestServerPush = viewModel::sendTestPushViaServer,
                            onClearPushAlerts = viewModel::clearPushAlerts
                        )
                    }
                    composable(ROUTE_MAP) {
                        DisasterMapScreen(
                            mapEventsState = mapEventsState,
                            highRiskZones = highRiskZones,
                            selectedTimePeriod = mapTimePeriod,
                            selectedTypeFilter = mapTypeFilter,
                            showCurrentLayer = showCurrentLayer,
                            showHistoricalLayer = showHistoricalLayer,
                            showRiskZonesLayer = showRiskZonesLayer,
                            selectedEvent = selectedEvent,
                            eventAnalysisState = eventAnalysisState,
                            selectedRiskZone = selectedRiskZone,
                            riskZoneAnalysisState = riskZoneAnalysisState,
                            onSelectTimePeriod = viewModel::selectMapTimePeriod,
                            onSelectTypeFilter = viewModel::selectMapTypeFilter,
                            onToggleCurrentLayer = viewModel::toggleCurrentLayer,
                            onToggleHistoricalLayer = viewModel::toggleHistoricalLayer,
                            onToggleRiskZonesLayer = viewModel::toggleRiskZonesLayer,
                            onRefreshMap = viewModel::refreshMapTelemetry,
                            onOpenEvent = viewModel::openEventDetail,
                            onCloseEvent = viewModel::closeEventDetail,
                            onAnalyzeEventWithAi = viewModel::analyzeSelectedEventWithAi,
                            onOpenRiskZone = viewModel::openRiskZoneDetail,
                            onCloseRiskZone = viewModel::closeRiskZoneDetail,
                            onAnalyzeRiskZoneWithAi = viewModel::analyzeSelectedRiskZoneWithAi
                        )
                    }
                    composable(ROUTE_AI_ANALYZER) {
                        AiAnalyzerScreen(
                            selectedPhotoUri = selectedPhotoUri,
                            photoAnalysisState = photoAnalysisState,
                            advisorStreamState = advisorStreamState,
                            onPhotoSelected = viewModel::onPhotoSelected,
                            onAnalyzePhoto = viewModel::analyzeSelectedPhoto,
                            onOpenCamera = {
                                viewModel.clearCapturedCameraImage()
                                navController.navigate(ROUTE_CAMERA_CAPTURE)
                            },
                            onAskAdvisor = viewModel::askEmergencyAdvisor
                        )
                    }
                    composable(ROUTE_CAMERA_CAPTURE) {
                        CameraDisasterCaptureScreen(
                            capturedBitmap = capturedBitmap,
                            analysisState = cameraAnalysisState,
                            onImageCaptured = viewModel::onCameraImageCaptured,
                            onRetake = viewModel::clearCapturedCameraImage,
                            onAnalyzeCapturedImage = viewModel::analyzeCapturedCameraImage,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable(ROUTE_PREPAREDNESS) {
                        PreparednessScreen(
                            checkedKitIds = checkedKitIds,
                            onToggleKitItem = viewModel::toggleKitItem
                        )
                    }
                    composable(ROUTE_ANALYTICS) {
                        AnalyticsScreen(feedState = feedState)
                    }
                }
            }
        }
    }
}
