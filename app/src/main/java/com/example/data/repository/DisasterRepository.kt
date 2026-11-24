package com.example.data.repository

import android.util.Log
import com.example.data.local.DisasterEventDao
import com.example.data.local.DisasterEventEntity
import com.example.data.local.HighRiskZoneCacheDao
import com.example.data.local.HighRiskZoneCacheEntity
import com.example.data.local.MapDisasterCacheDao
import com.example.data.local.MapHistoricalEventEntity
import com.example.data.remote.DisasterNetworkModule
import com.example.data.remote.EonetEventDto
import com.example.data.remote.UsgsFeatureDto
import com.example.domain.model.DEFAULT_HIGH_RISK_ZONES
import com.example.domain.model.DisasterCategory
import com.example.domain.model.DisasterEvent
import com.example.domain.model.HighRiskZone
import com.example.domain.model.MapTimePeriod
import com.example.domain.model.WeatherHazardSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

private const val TAG = "DisasterRepository"

data class FeedFetchResult(
    val events: List<DisasterEvent>,
    val isFromCache: Boolean,
    val warningMessage: String? = null
)

class DisasterRepository(
    private val eventDao: DisasterEventDao,
    private val mapCacheDao: MapDisasterCacheDao,
    private val highRiskZoneDao: HighRiskZoneCacheDao
) {
    fun observeCachedLiveEvents(): Flow<List<DisasterEvent>> =
        eventDao.observeAllEvents().map { entities -> entities.map { it.toDomain() } }

    fun observeMapEvents(period: MapTimePeriod): Flow<List<DisasterEvent>> =
        mapCacheDao.observeEventsByPeriod(period.name).map { periodEntities ->
            if (periodEntities.isNotEmpty()) {
                periodEntities.map { it.toDomain() }
            } else {
                // Fallback to any cached map events within the requested window, or live cache
                val cutoffMs = System.currentTimeMillis() - period.days * 86_400_000L
                val allMapCached = mapCacheDao.getAllMapEvents()
                    .distinctBy { it.eventId }
                    .filter { it.timestampMs >= cutoffMs }
                    .map { it.toDomain() }
                if (allMapCached.isNotEmpty()) {
                    allMapCached
                } else {
                    eventDao.getAllEvents()
                        .filter { it.timestampMs >= cutoffMs }
                        .map { it.toDomain() }
                }
            }
        }

    fun observeHighRiskZones(): Flow<List<HighRiskZone>> =
        highRiskZoneDao.observeZoneUpdates().map { cachedUpdates ->
            val byId = cachedUpdates.associateBy { it.id }
            DEFAULT_HIGH_RISK_ZONES.map { zone ->
                val cached = byId[zone.id]
                if (cached != null) {
                    zone.copy(
                        compositeRiskScore = maxOf(zone.compositeRiskScore, cached.compositeRiskScore),
                        liveClimateSummary = cached.liveClimateSummary ?: zone.liveClimateSummary
                    )
                } else {
                    zone
                }
            }
        }
    suspend fun fetchLiveDisasters(): FeedFetchResult = coroutineScope {
        val usgsDeferred = async {
            runCatching { DisasterNetworkModule.usgsApi.getRecentEarthquakes() }
        }
        val eonetDeferred = async {
            runCatching { DisasterNetworkModule.eonetApi.getOpenEvents() }
        }

        val usgsResult = usgsDeferred.await()
        val eonetResult = eonetDeferred.await()

        val combined = mutableListOf<DisasterEvent>()
        usgsResult.getOrNull()?.features?.mapNotNull { mapUsgsFeature(it) }?.let {
            combined.addAll(it)
        }
        eonetResult.getOrNull()?.events?.mapNotNull { mapEonetEvent(it) }?.let {
            combined.addAll(it)
        }

        if (combined.isNotEmpty()) {
            val sorted = combined.sortedByDescending { it.timestampMs }
            runCatching {
                eventDao.replaceAll(sorted.map { DisasterEventEntity.fromDomain(it) })
            }.onFailure { Log.w(TAG, "Failed to update local Room cache", it) }

            val warning = when {
                usgsResult.isFailure -> "เชื่อมต่อข้อมูลแผ่นดินไหว USGS ไม่สำเร็จ แสดงข้อมูลจาก NASA EONET"
                eonetResult.isFailure -> "เชื่อมต่อ NASA EONET ไม่สำเร็จ แสดงข้อมูลแผ่นดินไหว USGS"
                else -> null
            }
            FeedFetchResult(events = sorted, isFromCache = false, warningMessage = warning)
        } else {
            val cached = eventDao.getAllEvents().map { it.toDomain() }
            if (cached.isNotEmpty()) {
                FeedFetchResult(
                    events = cached,
                    isFromCache = true,
                    warningMessage = "ไม่สามารถเชื่อมต่ออินเทอร์เน็ต กำลังแสดงข้อมูลล่าสุดที่บันทึกไว้"
                )
            } else {
                val err = usgsResult.exceptionOrNull() ?: eonetResult.exceptionOrNull()
                if (err is CancellationException) throw err
                throw IllegalStateException(
                    err?.localizedMessage ?: "ไม่สามารถดึงข้อมูลภัยพิบัติแบบเรียลไทม์ได้"
                )
            }
        }
    }

    suspend fun fetchMapTelemetry(timePeriod: MapTimePeriod): FeedFetchResult = coroutineScope {
        val nowMs = System.currentTimeMillis()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startIso = isoFormat.format(Date(nowMs - timePeriod.days * 86_400_000L))
        val endIso = isoFormat.format(Date(nowMs))
        val minMag = when (timePeriod) {
            MapTimePeriod.LAST_24_HOURS -> 2.5
            MapTimePeriod.LAST_7_DAYS -> 3.5
            MapTimePeriod.LAST_30_DAYS -> 4.5
            MapTimePeriod.PAST_YEAR -> 5.5
        }
        val usgsLimit = if (timePeriod.includesHistorical) 75 else 45

        val currentUsgsDeferred = async {
            runCatching {
                DisasterNetworkModule.usgsApi.getRecentEarthquakes(
                    limit = 35,
                    minMagnitude = 2.5,
                    startTime = isoFormat.format(Date(nowMs - 7L * 86_400_000L)),
                    endTime = endIso
                )
            }
        }
        val currentEonetDeferred = async {
            runCatching {
                DisasterNetworkModule.eonetApi.getOpenEvents(status = "open", limit = 25)
            }
        }

        val historicalUsgsDeferred = if (timePeriod.includesHistorical) {
            async {
                runCatching {
                    DisasterNetworkModule.usgsApi.getRecentEarthquakes(
                        limit = usgsLimit,
                        minMagnitude = minMag,
                        startTime = startIso,
                        endTime = isoFormat.format(Date(nowMs - 7L * 86_400_000L))
                    )
                }
            }
        } else null

        val historicalEonetDeferred = if (timePeriod.includesHistorical) {
            async {
                runCatching {
                    DisasterNetworkModule.eonetApi.getOpenEvents(
                        status = "closed",
                        limit = 35,
                        days = timePeriod.days
                    )
                }
            }
        } else null

        val currentUsgsResult = currentUsgsDeferred.await()
        val currentEonetResult = currentEonetDeferred.await()
        val historicalUsgsResult = historicalUsgsDeferred?.await()
        val historicalEonetResult = historicalEonetDeferred?.await()

        val combined = mutableListOf<DisasterEvent>()
        val cutoffCurrentMs = nowMs - 7L * 86_400_000L

        currentUsgsResult.getOrNull()?.features
            ?.mapNotNull { mapUsgsFeature(it, isHistorical = false) }
            ?.filter { it.timestampMs >= nowMs - timePeriod.days * 86_400_000L }
            ?.let { combined.addAll(it) }

        currentEonetResult.getOrNull()?.events
            ?.mapNotNull { mapEonetEvent(it, isHistorical = false) }
            ?.let { combined.addAll(it) }

        historicalUsgsResult?.getOrNull()?.features
            ?.mapNotNull { mapUsgsFeature(it, isHistorical = true) }
            ?.filter { it.timestampMs < cutoffCurrentMs }
            ?.let { combined.addAll(it) }

        historicalEonetResult?.getOrNull()?.events
            ?.mapNotNull { mapEonetEvent(it, isHistorical = true) }
            ?.let { combined.addAll(it) }

        val deduped = combined
            .distinctBy { it.id }
            .sortedByDescending { it.timestampMs }

        if (deduped.isNotEmpty()) {
            val nowCachedAt = System.currentTimeMillis()
            runCatching {
                mapCacheDao.replaceEventsForPeriod(
                    periodName = timePeriod.name,
                    events = deduped.map {
                        MapHistoricalEventEntity.fromDomain(
                            event = it,
                            timePeriodName = timePeriod.name,
                            cachedAtMs = nowCachedAt
                        )
                    }
                )
            }.onFailure { Log.w(TAG, "Failed to cache map historical data in Room", it) }

            val partialWarning = when {
                currentUsgsResult.isFailure && (historicalUsgsResult?.isFailure ?: true) && currentEonetResult.isSuccess ->
                    "ดึงข้อมูลแผ่นดินไหวจาก USGS ไม่สำเร็จ แสดงข้อมูลจาก NASA EONET"
                else -> null
            }
            FeedFetchResult(events = deduped, isFromCache = false, warningMessage = partialWarning)
        } else {
            val periodCached = mapCacheDao.getEventsByPeriod(timePeriod.name).map { it.toDomain() }
            val fallbackCached = if (periodCached.isNotEmpty()) {
                periodCached
            } else {
                val cutoffMs = nowMs - timePeriod.days * 86_400_000L
                val allMapCached = mapCacheDao.getAllMapEvents()
                    .distinctBy { it.eventId }
                    .filter { it.timestampMs >= cutoffMs }
                    .map { it.toDomain() }
                if (allMapCached.isNotEmpty()) allMapCached
                else eventDao.getAllEvents()
                    .filter { it.timestampMs >= cutoffMs }
                    .map { it.toDomain() }
            }

            if (fallbackCached.isNotEmpty()) {
                FeedFetchResult(
                    events = fallbackCached,
                    isFromCache = true,
                    warningMessage = "โหมดออฟไลน์ • กำลังแสดงข้อมูลภัยพิบัติที่บันทึกไว้ใน Room (${fallbackCached.size} รายการ)"
                )
            } else {
                val err = currentUsgsResult.exceptionOrNull()
                    ?: currentEonetResult.exceptionOrNull()
                    ?: historicalUsgsResult?.exceptionOrNull()
                    ?: historicalEonetResult?.exceptionOrNull()
                if (err is CancellationException) throw err
                FeedFetchResult(
                    events = emptyList(),
                    isFromCache = false,
                    warningMessage = "ไม่พบข้อมูลเหตุการณ์ในแคชออฟไลน์สำหรับช่วงเวลาที่เลือก"
                )
            }
        }
    }

    suspend fun fetchEnrichedHighRiskZones(): List<HighRiskZone> = coroutineScope {
        val enriched = DEFAULT_HIGH_RISK_ZONES.map { zone ->
            async {
                val weather = runCatching {
                    fetchLocalWeatherHazard(zone.nameTh, zone.latitude, zone.longitude)
                }.getOrNull()

                if (weather != null) {
                    val elevatedScore = maxOf(zone.compositeRiskScore, weather.riskScore)
                    val summary = "${weather.weatherConditionTh} • อุณหภูมิ ${weather.temperatureC.roundToInt()}°C • ฝน ${"%.1f".format(weather.precipitationMm)} มม./ชม. • ลมกระโชก ${weather.windGustsKmh.roundToInt()} กม./ชม."
                    zone.copy(
                        compositeRiskScore = elevatedScore,
                        liveClimateSummary = summary
                    )
                } else {
                    zone
                }
            }
        }.awaitAll()

        val nowMs = System.currentTimeMillis()
        runCatching {
            highRiskZoneDao.upsertAll(
                enriched.map { zone ->
                    HighRiskZoneCacheEntity(
                        id = zone.id,
                        compositeRiskScore = zone.compositeRiskScore,
                        liveClimateSummary = zone.liveClimateSummary,
                        updatedAtMs = nowMs
                    )
                }
            )
        }.onFailure { Log.w(TAG, "Failed to cache high risk zones in Room", it) }

        enriched
    }

    suspend fun fetchLocalWeatherHazard(
        locationName: String,
        latitude: Double,
        longitude: Double
    ): WeatherHazardSnapshot {
        val response = DisasterNetworkModule.openMeteoApi.getCurrentWeather(
            latitude = latitude,
            longitude = longitude
        )
        val current = response.current
            ?: throw IllegalStateException("ไม่พบข้อมูลสภาพอากาศสำหรับพื้นที่นี้")

        val temp = current.temperature2m ?: 0.0
        val humidity = current.relativeHumidity2m ?: 0
        val windSpeed = current.windSpeed10m ?: 0.0
        val windGusts = current.windGusts10m ?: windSpeed
        val precip = current.precipitation ?: (current.rain ?: 0.0)
        val pressure = current.surfacePressure ?: 1013.0
        val code = current.weatherCode ?: 0

        val (conditionTh, codeRisk) = interpretWmoWeatherCode(code)
        var riskScore = codeRisk
        if (precip >= 30.0 || windGusts >= 70.0) {
            riskScore = maxOf(riskScore, 5)
        } else if (precip >= 15.0 || windGusts >= 50.0) {
            riskScore = maxOf(riskScore, 4)
        } else if (precip >= 5.0 || windGusts >= 35.0 || temp >= 40.0) {
            riskScore = maxOf(riskScore, 3)
        } else if (precip > 0.5 || temp >= 37.0) {
            riskScore = maxOf(riskScore, 2)
        }

        val riskLabel = when (riskScore) {
            5 -> "วิกฤต"
            4 -> "เสี่ยงสูง"
            3 -> "เฝ้าระวัง"
            2 -> "ปานกลาง"
            else -> "ปกติ"
        }

        val riskReason = buildString {
            when {
                precip >= 15.0 -> append("ฝนตกหนัก ${"%.1f".format(precip)} มม./ชม. เสี่ยงน้ำท่วมขังและน้ำป่าไหลหลาก")
                windGusts >= 50.0 -> append("ลมกระโชกแรง ${windGusts.roundToInt()} กม./ชม. เสี่ยงต้นไม้และป้ายโฆษณาหักโค่น")
                code >= 95 -> append("ตรวจพบกลุ่มพายุฝนฟ้าคะนอง ควรหลีกเลี่ยงพื้นที่โล่งแจ้ง")
                temp >= 39.0 -> append("อุณหภูมิสูงจัด ${"%.1f".format(temp)}°C เสี่ยงโรคลมแดดและไฟป่า")
                precip > 0.5 -> append("มีฝนตก ${"%.1f".format(precip)} มม./ชม. สภาพถนนอาจลื่น")
                else -> append("สภาพอากาศโดยรวมทรงตัว ความกดอากาศ ${pressure.roundToInt()} hPa")
            }
        }

        return WeatherHazardSnapshot(
            locationName = locationName,
            latitude = latitude,
            longitude = longitude,
            temperatureC = temp,
            humidityPercent = humidity,
            windSpeedKmh = windSpeed,
            windGustsKmh = windGusts,
            precipitationMm = precip,
            pressureHpa = pressure,
            weatherCode = code,
            weatherConditionTh = conditionTh,
            riskScore = riskScore,
            riskLabelTh = riskLabel,
            riskReasonTh = riskReason,
            observedAtIso = current.time.orEmpty()
        )
    }

    private fun mapUsgsFeature(
        feature: UsgsFeatureDto,
        isHistorical: Boolean = false
    ): DisasterEvent? {
        val coords = feature.geometry.coordinates
        if (coords.size < 2) return null
        val lon = coords[0]
        val lat = coords[1]
        val depth = coords.getOrNull(2)
        val mag = feature.properties.mag ?: 0.0
        val severity = when {
            mag >= 6.5 -> 5
            mag >= 5.5 -> 4
            mag >= 4.5 -> 3
            mag >= 3.5 -> 2
            else -> 1
        }
        val place = feature.properties.place ?: "ไม่ทราบพิกัดแน่ชัด"
        return DisasterEvent(
            id = "usgs_${feature.id}",
            title = feature.properties.title ?: "แผ่นดินไหวขนาด M${"%.1f".format(mag)}",
            category = DisasterCategory.EARTHQUAKE,
            severityScore = severity,
            magnitude = mag,
            depthKm = depth,
            latitude = lat,
            longitude = lon,
            place = place,
            timestampMs = feature.properties.time ?: System.currentTimeMillis(),
            source = "USGS",
            externalUrl = feature.properties.url,
            tsunamiAlert = (feature.properties.tsunami ?: 0) == 1,
            isHistorical = isHistorical
        )
    }

    private fun mapEonetEvent(
        event: EonetEventDto,
        isHistorical: Boolean = false
    ): DisasterEvent? {
        val lastGeom = event.geometry.lastOrNull() ?: return null
        val pair = extractLatLon(lastGeom.coordinates) ?: return null
        val (lat, lon) = pair
        val catTitle = event.categories.firstOrNull()?.title?.lowercase(Locale.ROOT).orEmpty()
        val category = when {
            catTitle.contains("storm") -> DisasterCategory.STORM
            catTitle.contains("flood") -> DisasterCategory.FLOOD
            catTitle.contains("wildfire") || catTitle.contains("fire") -> DisasterCategory.WILDFIRE
            catTitle.contains("volcan") -> DisasterCategory.VOLCANO
            catTitle.contains("landslide") -> DisasterCategory.LANDSLIDE
            catTitle.contains("earthquake") -> DisasterCategory.EARTHQUAKE
            else -> DisasterCategory.OTHER
        }
        val severity = when (category) {
            DisasterCategory.VOLCANO, DisasterCategory.STORM -> 4
            DisasterCategory.FLOOD, DisasterCategory.WILDFIRE, DisasterCategory.LANDSLIDE -> 3
            else -> 2
        }
        val timeMs = parseIsoTime(lastGeom.date) ?: System.currentTimeMillis()
        return DisasterEvent(
            id = "eonet_${event.id}",
            title = event.title,
            category = category,
            severityScore = severity,
            magnitude = null,
            depthKm = null,
            latitude = lat,
            longitude = lon,
            place = "${"%.2f".format(lat)}, ${"%.2f".format(lon)}",
            timestampMs = timeMs,
            source = "NASA EONET",
            externalUrl = event.link,
            isHistorical = isHistorical
        )
    }

    private fun extractLatLon(raw: Any?): Pair<Double, Double>? {
        if (raw !is List<*> || raw.isEmpty()) return null
        val first = raw.firstOrNull()
        if (first is Number && raw.size >= 2 && raw[1] is Number) {
            val lon = first.toDouble()
            val lat = (raw[1] as Number).toDouble()
            return lat to lon
        }
        if (first is List<*>) {
            return extractLatLon(first)
        }
        return null
    }

    private fun parseIsoTime(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = sdf.parse(iso)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return null
    }

    private fun interpretWmoWeatherCode(code: Int): Pair<String, Int> {
        return when (code) {
            0 -> "ท้องฟ้าแจ่มใส" to 1
            1, 2, 3 -> "มีเมฆบางส่วนถึงเมฆมาก" to 1
            45, 48 -> "หมอกหนา/ทัศนวิสัยต่ำ" to 2
            51, 53, 55 -> "ฝนปรอยถึงฝนปานกลาง" to 2
            61, 63, 80, 81 -> "ฝนตกปานกลาง" to 2
            65, 82 -> "ฝนตกหนัก เสี่ยงน้ำท่วมขัง" to 4
            95 -> "พายุฝนฟ้าคะนอง" to 4
            96, 99 -> "พายุฝนฟ้าคะนองรุนแรงและลูกเห็บ" to 5
            else -> "สภาพอากาศแปรปรวน" to 2
        }
    }
}
