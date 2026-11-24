package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DisasterDatabase
import com.example.data.local.PreparednessPreferences
import com.example.data.local.PushAlertEntity
import com.example.data.repository.AiDisasterAnalyzer
import com.example.data.repository.DisasterRepository
import com.example.domain.model.AiHazardAnalysis
import com.example.domain.model.DEFAULT_HIGH_RISK_ZONES
import com.example.domain.model.DisasterCategory
import com.example.domain.model.DisasterEvent
import com.example.domain.model.FeedFilter
import com.example.domain.model.GeoAlertUtils
import com.example.domain.model.HighRiskZone
import com.example.domain.model.MapDisasterTypeFilter
import com.example.domain.model.MapTimePeriod
import com.example.domain.model.PushAlertConfig
import com.example.domain.model.PushAlertRecord
import com.example.domain.model.THAI_PROVINCE_PRESETS
import com.example.domain.model.ThaiProvincePreset
import com.example.domain.model.WeatherHazardSnapshot
import com.example.notifications.DisasterNotificationHelper
import com.example.notifications.DisasterPushManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "DisasterViewModel"

sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Success(
        val allEvents: List<DisasterEvent>,
        val filteredEvents: List<DisasterEvent>,
        val isFromCache: Boolean,
        val warningMessage: String?
    ) : FeedUiState
    data class Error(val message: String) : FeedUiState
}

sealed interface WeatherUiState {
    data object Loading : WeatherUiState
    data class Success(val snapshot: WeatherHazardSnapshot) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

sealed interface AiAnalysisUiState {
    data object Idle : AiAnalysisUiState
    data object Loading : AiAnalysisUiState
    data class Success(val analysis: AiHazardAnalysis) : AiAnalysisUiState
    data class Error(val message: String) : AiAnalysisUiState
}

sealed interface AdvisorStreamUiState {
    data object Idle : AdvisorStreamUiState
    data object Loading : AdvisorStreamUiState
    data class Streaming(val question: String, val text: String) : AdvisorStreamUiState
    data class Success(val question: String, val text: String) : AdvisorStreamUiState
    data class Error(val message: String) : AdvisorStreamUiState
}

sealed interface FcmSyncUiState {
    data object Idle : FcmSyncUiState
    data object Syncing : FcmSyncUiState
    data class Synced(val message: String) : FcmSyncUiState
    data class Error(val message: String) : FcmSyncUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class DisasterViewModel(application: Application) : AndroidViewModel(application) {

    private val database = DisasterDatabase.getInstance(application)
    private val pushAlertDao = database.pushAlertDao()
    private val repository = DisasterRepository(
        eventDao = database.disasterEventDao(),
        mapCacheDao = database.mapDisasterCacheDao(),
        highRiskZoneDao = database.highRiskZoneCacheDao()
    )
    private val aiAnalyzer = AiDisasterAnalyzer()
    private val preferences = PreparednessPreferences(application)
    private val pushManager = DisasterPushManager(preferences)

    private val _rawEvents = MutableStateFlow<List<DisasterEvent>>(emptyList())
    private val _feedMeta = MutableStateFlow<Pair<Boolean, String?>>(false to null)
    private val _feedLoading = MutableStateFlow(true)
    private val _feedError = MutableStateFlow<String?>(null)

    private val _selectedFilter = MutableStateFlow(FeedFilter.ALL)
    val selectedFilter: StateFlow<FeedFilter> = _selectedFilter.asStateFlow()

    val feedState: StateFlow<FeedUiState> = combine(
        _rawEvents,
        _selectedFilter,
        _feedLoading,
        _feedError,
        _feedMeta
    ) { events, filter, loading, error, meta ->
        when {
            loading && events.isEmpty() -> FeedUiState.Loading
            error != null && events.isEmpty() -> FeedUiState.Error(error)
            else -> {
                val filtered = applyFilter(events, filter)
                FeedUiState.Success(
                    allEvents = events,
                    filteredEvents = filtered,
                    isFromCache = meta.first,
                    warningMessage = meta.second
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState.Loading)

    private val _weatherState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val weatherState: StateFlow<WeatherUiState> = _weatherState.asStateFlow()

    private val _selectedProvince = MutableStateFlow(THAI_PROVINCE_PRESETS.first())
    val selectedProvince: StateFlow<ThaiProvincePreset> = _selectedProvince.asStateFlow()

    private val _selectedEvent = MutableStateFlow<DisasterEvent?>(null)
    val selectedEvent: StateFlow<DisasterEvent?> = _selectedEvent.asStateFlow()

    private val _eventAnalysisState = MutableStateFlow<AiAnalysisUiState>(AiAnalysisUiState.Idle)
    val eventAnalysisState: StateFlow<AiAnalysisUiState> = _eventAnalysisState.asStateFlow()

    private val _weatherAnalysisState = MutableStateFlow<AiAnalysisUiState>(AiAnalysisUiState.Idle)
    val weatherAnalysisState: StateFlow<AiAnalysisUiState> = _weatherAnalysisState.asStateFlow()

    private val _selectedPhotoUri = MutableStateFlow<Uri?>(null)
    val selectedPhotoUri: StateFlow<Uri?> = _selectedPhotoUri.asStateFlow()

    private val _photoAnalysisState = MutableStateFlow<AiAnalysisUiState>(AiAnalysisUiState.Idle)
    val photoAnalysisState: StateFlow<AiAnalysisUiState> = _photoAnalysisState.asStateFlow()

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()

    private val _cameraAnalysisState = MutableStateFlow<AiAnalysisUiState>(AiAnalysisUiState.Idle)
    val cameraAnalysisState: StateFlow<AiAnalysisUiState> = _cameraAnalysisState.asStateFlow()

    private val _advisorStreamState = MutableStateFlow<AdvisorStreamUiState>(AdvisorStreamUiState.Idle)
    val advisorStreamState: StateFlow<AdvisorStreamUiState> = _advisorStreamState.asStateFlow()

    val checkedKitIds: StateFlow<Set<String>> = preferences.checkedKitItemIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    // --- FCM Real-Time Push Alerts State ---
    val pushAlertConfig: StateFlow<PushAlertConfig> = preferences.pushAlertConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PushAlertConfig())

    val pushAlerts: StateFlow<List<PushAlertRecord>> = pushAlertDao.observeAlerts()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unreadPushCount: StateFlow<Int> = pushAlertDao.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _fcmSyncState = MutableStateFlow<FcmSyncUiState>(FcmSyncUiState.Idle)
    val fcmSyncState: StateFlow<FcmSyncUiState> = _fcmSyncState.asStateFlow()

    private val _isPushCenterOpen = MutableStateFlow(false)
    val isPushCenterOpen: StateFlow<Boolean> = _isPushCenterOpen.asStateFlow()

    // --- Interactive Map State ---
    private val _mapTimePeriod = MutableStateFlow(MapTimePeriod.LAST_30_DAYS)
    val mapTimePeriod: StateFlow<MapTimePeriod> = _mapTimePeriod.asStateFlow()

    private val _mapTypeFilter = MutableStateFlow(MapDisasterTypeFilter.ALL)
    val mapTypeFilter: StateFlow<MapDisasterTypeFilter> = _mapTypeFilter.asStateFlow()

    private val _showCurrentLayer = MutableStateFlow(true)
    val showCurrentLayer: StateFlow<Boolean> = _showCurrentLayer.asStateFlow()

    private val _showHistoricalLayer = MutableStateFlow(true)
    val showHistoricalLayer: StateFlow<Boolean> = _showHistoricalLayer.asStateFlow()

    private val _showRiskZonesLayer = MutableStateFlow(true)
    val showRiskZonesLayer: StateFlow<Boolean> = _showRiskZonesLayer.asStateFlow()

    private val _mapLoading = MutableStateFlow(true)
    private val _mapError = MutableStateFlow<String?>(null)
    private val _mapMeta = MutableStateFlow<Pair<Boolean, String?>>(false to null)

    private val _mapCachedEventsFlow = _mapTimePeriod.flatMapLatest { period ->
        repository.observeMapEvents(period)
    }

    val mapEventsState: StateFlow<FeedUiState> = combine(
        _mapCachedEventsFlow,
        _mapTypeFilter,
        _showCurrentLayer,
        _showHistoricalLayer,
        _mapLoading,
        _mapError,
        _mapMeta
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val events = values[0] as List<DisasterEvent>
        val typeFilter = values[1] as MapDisasterTypeFilter
        val showCurrent = values[2] as Boolean
        val showHistorical = values[3] as Boolean
        val loading = values[4] as Boolean
        val error = values[5] as String?
        @Suppress("UNCHECKED_CAST")
        val meta = values[6] as Pair<Boolean, String?>

        when {
            loading && events.isEmpty() -> FeedUiState.Loading
            error != null && events.isEmpty() -> FeedUiState.Error(error)
            else -> {
                val filtered = events.filter { event ->
                    val layerOk = if (event.isHistorical) showHistorical else showCurrent
                    layerOk && matchesMapType(event.category, typeFilter)
                }
                FeedUiState.Success(
                    allEvents = events,
                    filteredEvents = filtered,
                    isFromCache = meta.first,
                    warningMessage = meta.second
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState.Loading)

    val highRiskZones: StateFlow<List<HighRiskZone>> = combine(
        repository.observeHighRiskZones(),
        _mapTypeFilter,
        _showRiskZonesLayer
    ) { zones, typeFilter, showRisk ->
        if (!showRisk) emptyList()
        else if (typeFilter == MapDisasterTypeFilter.ALL) zones
        else zones.filter { zone ->
            zone.primaryHazards.any { matchesMapType(it, typeFilter) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_HIGH_RISK_ZONES)

    private val _selectedRiskZone = MutableStateFlow<HighRiskZone?>(null)
    val selectedRiskZone: StateFlow<HighRiskZone?> = _selectedRiskZone.asStateFlow()

    private val _riskZoneAnalysisState = MutableStateFlow<AiAnalysisUiState>(AiAnalysisUiState.Idle)
    val riskZoneAnalysisState: StateFlow<AiAnalysisUiState> = _riskZoneAnalysisState.asStateFlow()

    private var advisorJob: Job? = null
    private var mapFetchJob: Job? = null

    init {
        DisasterNotificationHelper.createNotificationChannels(application)
        viewModelScope.launch {
            repository.observeCachedLiveEvents().collect { cached ->
                if (cached.isNotEmpty()) {
                    _rawEvents.value = cached
                }
            }
        }
        refreshAllTelemetry()
        refreshMapTelemetry()
        enrichHighRiskZones()
        viewModelScope.launch {
            preferences.selectedProvinceId.collect { id ->
                val preset = THAI_PROVINCE_PRESETS.firstOrNull { it.id == id }
                    ?: THAI_PROVINCE_PRESETS.first()
                _selectedProvince.value = preset
                loadWeatherForCoordinates(preset.nameTh, preset.latitude, preset.longitude)
            }
        }
        viewModelScope.launch {
            val currentPush = preferences.pushAlertConfig.first()
            if (currentPush.enabled) {
                pushManager.refreshTokenAndTopics()
            }
        }
    }

    fun refreshAllTelemetry() {
        viewModelScope.launch {
            _feedLoading.value = true
            _feedError.value = null
            try {
                val result = repository.fetchLiveDisasters()
                _rawEvents.value = result.events
                _feedMeta.value = result.isFromCache to result.warningMessage
                if (!result.isFromCache) {
                    evaluateImmediateLocationDisasters(result.events)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch live disasters", e)
                _feedError.value = e.localizedMessage ?: "เกิดข้อผิดพลาดในการเชื่อมต่อข้อมูลภัยพิบัติ"
            } finally {
                _feedLoading.value = false
            }
        }
        val current = _selectedProvince.value
        loadWeatherForCoordinates(current.nameTh, current.latitude, current.longitude)
    }

    private suspend fun evaluateImmediateLocationDisasters(events: List<DisasterEvent>) {
        val config = preferences.pushAlertConfig.first()
        if (!config.enabled) return

        val alreadyNotified = preferences.notifiedEventIds.first()
        val nowMs = System.currentTimeMillis()
        val recentThresholdMs = nowMs - 12L * 3_600_000L

        val nearbyNewAlerts = events.mapNotNull { event ->
            if (event.timestampMs < recentThresholdMs || event.severityScore < 3 || event.id in alreadyNotified) {
                return@mapNotNull null
            }
            val distKm = GeoAlertUtils.haversineDistanceKm(
                config.latitude,
                config.longitude,
                event.latitude,
                event.longitude
            )
            if (distKm <= config.radiusKm) event to distKm else null
        }

        if (nearbyNewAlerts.isEmpty()) return

        nearbyNewAlerts.forEach { (event, distKm) ->
            pushAlertDao.upsert(
                PushAlertEntity(
                    id = "local_${event.id}",
                    eventId = event.id,
                    title = "เตือนภัยใกล้พื้นที่: ${event.category.labelTh}",
                    body = "${event.title} (${event.place})",
                    categoryName = event.category.name,
                    severityScore = event.severityScore,
                    latitude = event.latitude,
                    longitude = event.longitude,
                    distanceKm = distKm,
                    place = event.place,
                    source = event.source,
                    receivedAtMs = event.timestampMs,
                    withinImmediateRadius = true,
                    isRead = false
                )
            )
            DisasterNotificationHelper.showDisasterNotification(
                context = getApplication(),
                notificationId = event.id.hashCode(),
                title = "เตือนภัยใกล้พื้นที่: ${event.category.labelTh}",
                body = "${event.title} • ${event.place}",
                severityScore = event.severityScore,
                distanceKm = distKm,
                eventId = event.id,
                latitude = event.latitude,
                longitude = event.longitude
            )
        }
        preferences.markEventsNotified(nearbyNewAlerts.map { it.first.id })
    }

    fun enablePushAlerts(latitude: Double? = null, longitude: Double? = null, locationLabel: String? = null) {
        val current = pushAlertConfig.value
        val targetLat = latitude ?: _selectedProvince.value.latitude
        val targetLon = longitude ?: _selectedProvince.value.longitude
        val targetLabel = locationLabel ?: current.locationLabel.ifBlank { _selectedProvince.value.nameTh }
        syncFcmSubscription(
            enabled = true,
            latitude = targetLat,
            longitude = targetLon,
            locationLabel = targetLabel,
            radiusKm = current.radiusKm
        )
    }

    fun disablePushAlerts() {
        val current = pushAlertConfig.value
        syncFcmSubscription(
            enabled = false,
            latitude = current.latitude,
            longitude = current.longitude,
            locationLabel = current.locationLabel,
            radiusKm = current.radiusKm
        )
    }

    fun updatePushAlertLocation(label: String, latitude: Double, longitude: Double) {
        val current = pushAlertConfig.value
        syncFcmSubscription(
            enabled = true,
            latitude = latitude,
            longitude = longitude,
            locationLabel = label,
            radiusKm = current.radiusKm
        )
    }

    fun updatePushAlertRadius(radiusKm: Double) {
        val current = pushAlertConfig.value
        if (current.enabled) {
            syncFcmSubscription(
                enabled = true,
                latitude = current.latitude,
                longitude = current.longitude,
                locationLabel = current.locationLabel,
                radiusKm = radiusKm
            )
        } else {
            viewModelScope.launch {
                preferences.savePushAlertConfig(current.copy(radiusKm = radiusKm))
            }
        }
    }

    fun refreshFcmToken() {
        val current = pushAlertConfig.value
        syncFcmSubscription(
            enabled = true,
            latitude = current.latitude,
            longitude = current.longitude,
            locationLabel = current.locationLabel,
            radiusKm = current.radiusKm
        )
    }

    fun saveFcmServerUrl(url: String) {
        val trimmed = url.trim()
        viewModelScope.launch {
            preferences.saveFcmServerUrl(trimmed)
            val current = pushAlertConfig.value
            if (current.enabled) {
                syncFcmSubscription(
                    enabled = true,
                    latitude = current.latitude,
                    longitude = current.longitude,
                    locationLabel = current.locationLabel,
                    radiusKm = current.radiusKm,
                    serverUrlOverride = trimmed
                )
            } else {
                _fcmSyncState.value = FcmSyncUiState.Synced(
                    if (trimmed.isBlank()) "ล้างการตั้งค่า URL เซิร์ฟเวอร์แล้ว"
                    else "บันทึก URL เซิร์ฟเวอร์ FCM แล้ว"
                )
            }
        }
    }

    fun sendTestPushViaServer(serverUrl: String) {
        viewModelScope.launch {
            _fcmSyncState.value = FcmSyncUiState.Syncing
            val result = pushManager.sendTestPushViaServer(serverUrl)
            _fcmSyncState.value = result.fold(
                onSuccess = {
                    FcmSyncUiState.Synced("เซิร์ฟเวอร์ส่งแจ้งเตือนทดสอบผ่าน FCM สำเร็จ")
                },
                onFailure = { err ->
                    FcmSyncUiState.Error(
                        err.localizedMessage ?: "ไม่สามารถติดต่อเซิร์ฟเวอร์ FCM ได้"
                    )
                }
            )
        }
    }

    private fun syncFcmSubscription(
        enabled: Boolean,
        latitude: Double,
        longitude: Double,
        locationLabel: String,
        radiusKm: Double,
        serverUrlOverride: String? = null
    ) {
        viewModelScope.launch {
            _fcmSyncState.value = FcmSyncUiState.Syncing
            val result = pushManager.syncSubscription(
                enabled = enabled,
                latitude = latitude,
                longitude = longitude,
                locationLabel = locationLabel,
                radiusKm = radiusKm,
                serverUrlOverride = serverUrlOverride
            )
            _fcmSyncState.value = result.fold(
                onSuccess = { cfg ->
                    if (cfg.enabled) {
                        val serverSuffix = if (DisasterPushManager.isValidHttpUrl(cfg.fcmServerUrl)) {
                            " และลงทะเบียนกับเซิร์ฟเวอร์แล้ว"
                        } else ""
                        FcmSyncUiState.Synced(
                            "เชื่อมต่อ FCM สำเร็จ • ติดตาม ${cfg.subscribedTopics.size} หัวข้อพื้นที่รอบ ${cfg.locationLabel}$serverSuffix"
                        )
                    } else {
                        FcmSyncUiState.Synced("ปิดรับการแจ้งเตือนแบบพุชแล้ว")
                    }
                },
                onFailure = { err ->
                    FcmSyncUiState.Error(
                        err.localizedMessage ?: "ไม่สามารถเชื่อมต่อบริการ Firebase Cloud Messaging ได้"
                    )
                }
            )
        }
    }

    fun openPushAlertCenter() {
        _isPushCenterOpen.value = true
        viewModelScope.launch {
            pushAlertDao.markAllRead()
        }
    }

    fun closePushAlertCenter() {
        _isPushCenterOpen.value = false
    }

    fun clearPushAlerts() {
        viewModelScope.launch {
            pushAlertDao.clearAll()
        }
    }

    fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val openInbox = intent.getBooleanExtra(DisasterNotificationHelper.EXTRA_OPEN_PUSH_INBOX, false)
        val eventId = intent.getStringExtra(DisasterNotificationHelper.EXTRA_ALERT_EVENT_ID)
        if (!openInbox && eventId == null) return

        if (eventId != null) {
            val matched = _rawEvents.value.firstOrNull { it.id == eventId }
            if (matched != null) {
                openEventDetail(matched)
            } else {
                openPushAlertCenter()
            }
        } else if (openInbox) {
            openPushAlertCenter()
        }
    }

    fun selectFilter(filter: FeedFilter) {
        _selectedFilter.value = filter
    }

    fun selectProvince(preset: ThaiProvincePreset) {
        _selectedProvince.value = preset
        _weatherAnalysisState.value = AiAnalysisUiState.Idle
        viewModelScope.launch {
            preferences.setSelectedProvince(preset.id)
            val currentPush = preferences.pushAlertConfig.first()
            if (currentPush.enabled) {
                syncFcmSubscription(
                    enabled = true,
                    latitude = preset.latitude,
                    longitude = preset.longitude,
                    locationLabel = preset.nameTh,
                    radiusKm = currentPush.radiusKm
                )
            }
        }
    }

    fun loadWeatherForCoordinates(label: String, lat: Double, lon: Double) {
        viewModelScope.launch {
            _weatherState.value = WeatherUiState.Loading
            try {
                val snapshot = repository.fetchLocalWeatherHazard(label, lat, lon)
                _weatherState.value = WeatherUiState.Success(snapshot)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load weather hazard", e)
                _weatherState.value = WeatherUiState.Error(
                    e.localizedMessage ?: "ไม่สามารถโหลดข้อมูลสภาพอากาศได้"
                )
            }
        }
    }

    fun openEventDetail(event: DisasterEvent) {
        _selectedEvent.value = event
        _eventAnalysisState.value = AiAnalysisUiState.Idle
    }

    fun closeEventDetail() {
        _selectedEvent.value = null
        _eventAnalysisState.value = AiAnalysisUiState.Idle
    }

    fun analyzeSelectedEventWithAi() {
        val event = _selectedEvent.value ?: return
        val weather = (_weatherState.value as? WeatherUiState.Success)?.snapshot
        viewModelScope.launch {
            _eventAnalysisState.value = AiAnalysisUiState.Loading
            try {
                val analysis = aiAnalyzer.analyzeDisasterEvent(event, weather)
                _eventAnalysisState.value = AiAnalysisUiState.Success(analysis)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Event AI analysis timed out", e)
                _eventAnalysisState.value = AiAnalysisUiState.Error(
                    "การวิเคราะห์ใช้เวลานานเกิน 60 วินาที กรุณาลองใหม่อีกครั้ง"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Event AI analysis failed", e)
                _eventAnalysisState.value = AiAnalysisUiState.Error(
                    e.localizedMessage ?: "ไม่สามารถวิเคราะห์เหตุการณ์ด้วย AI ได้"
                )
            }
        }
    }

    fun analyzeLocalWeatherWithAi() {
        val weather = (_weatherState.value as? WeatherUiState.Success)?.snapshot ?: return
        viewModelScope.launch {
            _weatherAnalysisState.value = AiAnalysisUiState.Loading
            try {
                val analysis = aiAnalyzer.analyzeLocalWeather(weather)
                _weatherAnalysisState.value = AiAnalysisUiState.Success(analysis)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Weather AI analysis timed out", e)
                _weatherAnalysisState.value = AiAnalysisUiState.Error(
                    "การวิเคราะห์ใช้เวลานานเกินกำหนด กรุณาลองใหม่"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Weather AI analysis failed", e)
                _weatherAnalysisState.value = AiAnalysisUiState.Error(
                    e.localizedMessage ?: "ไม่สามารถวิเคราะห์สภาพอากาศด้วย AI ได้"
                )
            }
        }
    }

    fun dismissWeatherAnalysis() {
        _weatherAnalysisState.value = AiAnalysisUiState.Idle
    }

    fun onPhotoSelected(uri: Uri?) {
        _selectedPhotoUri.value = uri
        _photoAnalysisState.value = AiAnalysisUiState.Idle
    }

    fun onCameraImageCaptured(bitmap: Bitmap) {
        _capturedBitmap.value = bitmap
        _cameraAnalysisState.value = AiAnalysisUiState.Idle
    }

    fun clearCapturedCameraImage() {
        _capturedBitmap.value = null
        _cameraAnalysisState.value = AiAnalysisUiState.Idle
    }

    fun analyzeCapturedCameraImage(userNote: String) {
        val bitmap = _capturedBitmap.value ?: return
        viewModelScope.launch {
            _cameraAnalysisState.value = AiAnalysisUiState.Loading
            try {
                val analysis = aiAnalyzer.analyzePhotoAndContext(bitmap, userNote)
                _cameraAnalysisState.value = AiAnalysisUiState.Success(analysis)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Camera AI analysis timed out", e)
                _cameraAnalysisState.value = AiAnalysisUiState.Error(
                    "การวิเคราะห์ภาพใช้เวลานานเกิน 60 วินาที กรุณาลองใหม่"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Camera AI analysis failed", e)
                _cameraAnalysisState.value = AiAnalysisUiState.Error(
                    e.localizedMessage ?: "ไม่สามารถวิเคราะห์ภาพถ่ายด้วย AI ได้"
                )
            }
        }
    }

    fun analyzeSelectedPhoto(userNote: String) {
        val uri = _selectedPhotoUri.value ?: return
        viewModelScope.launch {
            _photoAnalysisState.value = AiAnalysisUiState.Loading
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    decodeBitmapFromUri(uri)
                }
                val analysis = aiAnalyzer.analyzePhotoAndContext(bitmap, userNote)
                _photoAnalysisState.value = AiAnalysisUiState.Success(analysis)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Photo AI analysis timed out", e)
                _photoAnalysisState.value = AiAnalysisUiState.Error(
                    "การวิเคราะห์ภาพใช้เวลานานเกิน 60 วินาที กรุณาลองใหม่"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Photo AI analysis failed", e)
                _photoAnalysisState.value = AiAnalysisUiState.Error(
                    e.localizedMessage ?: "ไม่สามารถวิเคราะห์ภาพถ่ายด้วย AI ได้"
                )
            }
        }
    }

    fun askEmergencyAdvisor(question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return
        advisorJob?.cancel()
        advisorJob = viewModelScope.launch {
            _advisorStreamState.value = AdvisorStreamUiState.Loading
            val accumulated = StringBuilder()
            val weatherContext = (_weatherState.value as? WeatherUiState.Success)?.snapshot?.let {
                "พื้นที่ผู้ใช้: ${it.locationName}, ${it.weatherConditionTh}, ฝน ${it.precipitationMm} มม., ลมกระโชก ${it.windGustsKmh} กม./ชม."
            }.orEmpty()
            val topQuake = _rawEvents.value.firstOrNull { it.category == DisasterCategory.EARTHQUAKE }?.let {
                "แผ่นดินไหวล่าสุด: ${it.title} (${it.place})"
            }.orEmpty()
            val contextSummary = listOf(weatherContext, topQuake).filter { it.isNotBlank() }.joinToString("\n")

            try {
                withTimeout(60_000L) {
                    aiAnalyzer.streamEmergencyAdvice(trimmed, contextSummary).collect { chunk ->
                        accumulated.append(chunk)
                        _advisorStreamState.value = AdvisorStreamUiState.Streaming(
                            question = trimmed,
                            text = accumulated.toString()
                        )
                    }
                }
                if (accumulated.isNotBlank()) {
                    _advisorStreamState.value = AdvisorStreamUiState.Success(
                        question = trimmed,
                        text = accumulated.toString()
                    )
                } else {
                    _advisorStreamState.value = AdvisorStreamUiState.Error(
                        "AI ไม่ได้ส่งคืนคำตอบ กรุณาลองใหม่อีกครั้ง"
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Advisor stream timed out", e)
                _advisorStreamState.value = AdvisorStreamUiState.Error(
                    "การตอบกลับใช้เวลานานเกินกำหนด กรุณาลองใหม่"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Advisor stream failed", e)
                _advisorStreamState.value = AdvisorStreamUiState.Error(
                    e.localizedMessage ?: "ไม่สามารถเชื่อมต่อที่ปรึกษา AI ได้"
                )
            }
        }
    }

    fun toggleKitItem(itemId: String) {
        viewModelScope.launch {
            preferences.toggleKitItem(itemId)
        }
    }

    fun selectMapTimePeriod(period: MapTimePeriod) {
        if (_mapTimePeriod.value == period) return
        _mapTimePeriod.value = period
        refreshMapTelemetry()
    }

    fun selectMapTypeFilter(filter: MapDisasterTypeFilter) {
        _mapTypeFilter.value = filter
    }

    fun toggleCurrentLayer() {
        _showCurrentLayer.value = !_showCurrentLayer.value
    }

    fun toggleHistoricalLayer() {
        _showHistoricalLayer.value = !_showHistoricalLayer.value
    }

    fun toggleRiskZonesLayer() {
        _showRiskZonesLayer.value = !_showRiskZonesLayer.value
    }

    fun refreshMapTelemetry() {
        val period = _mapTimePeriod.value
        mapFetchJob?.cancel()
        mapFetchJob = viewModelScope.launch {
            _mapLoading.value = true
            _mapError.value = null
            _mapMeta.value = false to null
            try {
                val result = repository.fetchMapTelemetry(period)
                _mapMeta.value = result.isFromCache to result.warningMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch map telemetry", e)
                _mapError.value = e.localizedMessage ?: "ไม่สามารถโหลดข้อมูลแผนที่ภัยพิบัติได้"
            } finally {
                _mapLoading.value = false
            }
        }
    }

    private fun enrichHighRiskZones() {
        viewModelScope.launch {
            try {
                repository.fetchEnrichedHighRiskZones()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not enrich high risk zones with live climate", e)
            }
        }
    }

    fun openRiskZoneDetail(zone: HighRiskZone) {
        _selectedRiskZone.value = zone
        _riskZoneAnalysisState.value = AiAnalysisUiState.Idle
    }

    fun closeRiskZoneDetail() {
        _selectedRiskZone.value = null
        _riskZoneAnalysisState.value = AiAnalysisUiState.Idle
    }

    fun analyzeSelectedRiskZoneWithAi() {
        val zone = _selectedRiskZone.value ?: return
        viewModelScope.launch {
            _riskZoneAnalysisState.value = AiAnalysisUiState.Loading
            try {
                val analysis = aiAnalyzer.analyzeHighRiskZone(zone)
                _riskZoneAnalysisState.value = AiAnalysisUiState.Success(analysis)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Risk zone AI analysis timed out", e)
                _riskZoneAnalysisState.value = AiAnalysisUiState.Error(
                    "การวิเคราะห์ใช้เวลานานเกิน 60 วินาที กรุณาลองใหม่"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Risk zone AI analysis failed", e)
                _riskZoneAnalysisState.value = AiAnalysisUiState.Error(
                    e.localizedMessage ?: "ไม่สามารถวิเคราะห์พื้นที่เสี่ยงด้วย AI ได้"
                )
            }
        }
    }

    private fun matchesMapType(
        category: DisasterCategory,
        filter: MapDisasterTypeFilter
    ): Boolean {
        return when (filter) {
            MapDisasterTypeFilter.ALL -> true
            MapDisasterTypeFilter.EARTHQUAKE -> category == DisasterCategory.EARTHQUAKE
            MapDisasterTypeFilter.FLOOD -> category == DisasterCategory.FLOOD
            MapDisasterTypeFilter.STORM -> category == DisasterCategory.STORM
            MapDisasterTypeFilter.WILDFIRE -> category == DisasterCategory.WILDFIRE
            MapDisasterTypeFilter.VOLCANO_LANDSLIDE ->
                category == DisasterCategory.VOLCANO || category == DisasterCategory.LANDSLIDE
        }
    }

    private fun applyFilter(events: List<DisasterEvent>, filter: FeedFilter): List<DisasterEvent> {
        return when (filter) {
            FeedFilter.ALL -> events
            FeedFilter.SEA_THAILAND -> events.filter {
                it.latitude in -10.0..28.0 && it.longitude in 90.0..130.0
            }
            FeedFilter.EARTHQUAKE -> events.filter { it.category == DisasterCategory.EARTHQUAKE }
            FeedFilter.STORM_FLOOD -> events.filter {
                it.category == DisasterCategory.STORM ||
                    it.category == DisasterCategory.FLOOD ||
                    it.category == DisasterCategory.LANDSLIDE
            }
            FeedFilter.WILDFIRE -> events.filter {
                it.category == DisasterCategory.WILDFIRE || it.category == DisasterCategory.VOLCANO
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun decodeBitmapFromUri(uri: Uri): Bitmap {
        val resolver = getApplication<Application>().contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } else {
            MediaStore.Images.Media.getBitmap(resolver, uri)
        }
    }
}
