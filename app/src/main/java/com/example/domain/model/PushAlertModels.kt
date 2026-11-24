package com.example.domain.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class PushAlertConfig(
    val enabled: Boolean = false,
    val fcmToken: String? = null,
    val latitude: Double = THAI_PROVINCE_PRESETS.first().latitude,
    val longitude: Double = THAI_PROVINCE_PRESETS.first().longitude,
    val locationLabel: String = THAI_PROVINCE_PRESETS.first().nameTh,
    val radiusKm: Double = 150.0,
    val subscribedTopics: Set<String> = emptySet(),
    val lastSyncedAtMs: Long = 0L,
    val fcmServerUrl: String = ""
)

data class PushAlertRecord(
    val id: String,
    val eventId: String?,
    val title: String,
    val body: String,
    val category: DisasterCategory,
    val severityScore: Int,
    val latitude: Double?,
    val longitude: Double?,
    val distanceKm: Double?,
    val place: String,
    val source: String,
    val receivedAtMs: Long,
    val withinImmediateRadius: Boolean,
    val isRead: Boolean
)

object GeoAlertUtils {
    private const val EARTH_RADIUS_KM = 6371.0

    fun haversineDistanceKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val radLat1 = Math.toRadians(lat1)
        val radLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(radLat1) * cos(radLat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    fun findNearestProvincePreset(latitude: Double, longitude: Double): ThaiProvincePreset {
        return THAI_PROVINCE_PRESETS.minByOrNull { preset ->
            haversineDistanceKm(latitude, longitude, preset.latitude, preset.longitude)
        } ?: THAI_PROVINCE_PRESETS.first()
    }

    /**
     * Generates FCM-valid topic names ([a-zA-Z0-9-_.~%]+) representing:
     * 1. National immediate emergency broadcast (`disaster_th_all`)
     * 2. Nearest Thai province / region zone (`disaster_zone_<id>`)
     * 3. 1-degree coarse spatial grid cell (`disaster_grid_<lat>_<lon>`)
     */
    fun buildLocationTopics(latitude: Double, longitude: Double): Set<String> {
        val nearestProvince = findNearestProvincePreset(latitude, longitude)
        val latCell = latitude.roundToInt().let { if (it < 0) "s${-it}" else "n$it" }
        val lonCell = longitude.roundToInt().let { if (it < 0) "w${-it}" else "e$it" }

        return setOf(
            "disaster_th_all",
            "disaster_zone_${nearestProvince.id}",
            "disaster_grid_${latCell}_${lonCell}"
        )
    }
}
