package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DisasterDatabase
import com.example.data.local.DisasterEventEntity
import com.example.data.local.HighRiskZoneCacheEntity
import com.example.data.local.MapHistoricalEventEntity
import com.example.data.repository.DisasterRepository
import com.example.domain.model.DEFAULT_HIGH_RISK_ZONES
import com.example.domain.model.DisasterCategory
import com.example.domain.model.DisasterEvent
import com.example.domain.model.MapTimePeriod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DisasterDatabaseRobolectricTest {

    private lateinit var db: DisasterDatabase
    private lateinit var repository: DisasterRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DisasterDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DisasterRepository(
            eventDao = db.disasterEventDao(),
            mapCacheDao = db.mapDisasterCacheDao(),
            highRiskZoneDao = db.highRiskZoneCacheDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `map cache isolates periods and preserves historical flags`() = runTest {
        val now = System.currentTimeMillis()
        val currentEvent = DisasterEvent(
            id = "usgs_1",
            title = "M5.2 Northern Thailand",
            category = DisasterCategory.EARTHQUAKE,
            severityScore = 3,
            magnitude = 5.2,
            depthKm = 10.0,
            latitude = 19.5,
            longitude = 99.8,
            place = "Chiang Rai",
            timestampMs = now - 3_600_000L,
            source = "USGS",
            externalUrl = null,
            isHistorical = false
        )
        val historicalEvent = DisasterEvent(
            id = "eonet_old_1",
            title = "Chiang Mai Wildfire History",
            category = DisasterCategory.WILDFIRE,
            severityScore = 4,
            magnitude = null,
            depthKm = null,
            latitude = 18.8,
            longitude = 98.9,
            place = "Chiang Mai",
            timestampMs = now - 15L * 86_400_000L,
            source = "NASA EONET",
            externalUrl = null,
            isHistorical = true
        )

        db.mapDisasterCacheDao().replaceEventsForPeriod(
            periodName = MapTimePeriod.LAST_30_DAYS.name,
            events = listOf(
                MapHistoricalEventEntity.fromDomain(currentEvent, MapTimePeriod.LAST_30_DAYS.name, now),
                MapHistoricalEventEntity.fromDomain(historicalEvent, MapTimePeriod.LAST_30_DAYS.name, now)
            )
        )
        db.mapDisasterCacheDao().replaceEventsForPeriod(
            periodName = MapTimePeriod.LAST_7_DAYS.name,
            events = listOf(
                MapHistoricalEventEntity.fromDomain(currentEvent, MapTimePeriod.LAST_7_DAYS.name, now)
            )
        )

        val thirtyDays = repository.observeMapEvents(MapTimePeriod.LAST_30_DAYS).first()
        val sevenDays = repository.observeMapEvents(MapTimePeriod.LAST_7_DAYS).first()
        val twentyFourHoursFallback = repository.observeMapEvents(MapTimePeriod.LAST_24_HOURS).first()

        assertEquals(2, thirtyDays.size)
        assertTrue(thirtyDays.any { it.id == "eonet_old_1" && it.isHistorical })
        assertEquals(1, sevenDays.size)
        assertEquals("usgs_1", sevenDays.first().id)
        // LAST_24_HOURS wasn't cached directly, so fallback finds currentEvent within 24h window
        assertEquals(1, twentyFourHoursFallback.size)
        assertEquals("usgs_1", twentyFourHoursFallback.first().id)
    }

    @Test
    fun `observeMapEvents falls back to live event cache when map cache is empty`() = runTest {
        val now = System.currentTimeMillis()
        val liveOnlyEvent = DisasterEvent(
            id = "usgs_live_only",
            title = "M4.8 Andaman Sea",
            category = DisasterCategory.EARTHQUAKE,
            severityScore = 3,
            magnitude = 4.8,
            depthKm = 12.0,
            latitude = 8.1,
            longitude = 97.4,
            place = "Andaman Sea",
            timestampMs = now - 2_000_000L,
            source = "USGS",
            externalUrl = null,
            isHistorical = false
        )

        db.disasterEventDao().replaceAll(listOf(DisasterEventEntity.fromDomain(liveOnlyEvent)))

        val mapFallback = repository.observeMapEvents(MapTimePeriod.LAST_7_DAYS).first()
        assertEquals(1, mapFallback.size)
        assertEquals("usgs_live_only", mapFallback.first().id)
    }

    @Test
    fun `observeHighRiskZones merges Room cached climate enrichment`() = runTest {
        val firstZone = DEFAULT_HIGH_RISK_ZONES.first()
        val now = System.currentTimeMillis()
        db.highRiskZoneCacheDao().upsertAll(
            listOf(
                HighRiskZoneCacheEntity(
                    id = firstZone.id,
                    compositeRiskScore = 5,
                    liveClimateSummary = "พายุฝนฟ้าคะนอง • ฝน 24.0 มม./ชม.",
                    updatedAtMs = now
                )
            )
        )

        val zones = repository.observeHighRiskZones().first()
        val updated = zones.first { it.id == firstZone.id }
        assertEquals(5, updated.compositeRiskScore)
        assertEquals("พายุฝนฟ้าคะนอง • ฝน 24.0 มม./ชม.", updated.liveClimateSummary)
    }
}
