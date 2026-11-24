package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.example.domain.model.DisasterCategory
import com.example.domain.model.DisasterEvent
import com.example.domain.model.PushAlertRecord
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cached_disaster_events")
data class DisasterEventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val categoryName: String,
    val severityScore: Int,
    val magnitude: Double?,
    val depthKm: Double?,
    val latitude: Double,
    val longitude: Double,
    val place: String,
    val timestampMs: Long,
    val source: String,
    val externalUrl: String?,
    val tsunamiAlert: Boolean,
    val isHistorical: Boolean = false
) {
    fun toDomain(): DisasterEvent {
        val cat = runCatching { DisasterCategory.valueOf(categoryName) }
            .getOrDefault(DisasterCategory.OTHER)
        return DisasterEvent(
            id = id,
            title = title,
            category = cat,
            severityScore = severityScore,
            magnitude = magnitude,
            depthKm = depthKm,
            latitude = latitude,
            longitude = longitude,
            place = place,
            timestampMs = timestampMs,
            source = source,
            externalUrl = externalUrl,
            tsunamiAlert = tsunamiAlert,
            isHistorical = isHistorical
        )
    }

    companion object {
        fun fromDomain(event: DisasterEvent) = DisasterEventEntity(
            id = event.id,
            title = event.title,
            categoryName = event.category.name,
            severityScore = event.severityScore,
            magnitude = event.magnitude,
            depthKm = event.depthKm,
            latitude = event.latitude,
            longitude = event.longitude,
            place = event.place,
            timestampMs = event.timestampMs,
            source = event.source,
            externalUrl = event.externalUrl,
            tsunamiAlert = event.tsunamiAlert,
            isHistorical = event.isHistorical
        )
    }
}

@Entity(tableName = "cached_map_disaster_events")
data class MapHistoricalEventEntity(
    @PrimaryKey val cacheKey: String,
    val eventId: String,
    val timePeriodName: String,
    val title: String,
    val categoryName: String,
    val severityScore: Int,
    val magnitude: Double?,
    val depthKm: Double?,
    val latitude: Double,
    val longitude: Double,
    val place: String,
    val timestampMs: Long,
    val source: String,
    val externalUrl: String?,
    val tsunamiAlert: Boolean,
    val isHistorical: Boolean,
    val cachedAtMs: Long
) {
    fun toDomain(): DisasterEvent {
        val cat = runCatching { DisasterCategory.valueOf(categoryName) }
            .getOrDefault(DisasterCategory.OTHER)
        return DisasterEvent(
            id = eventId,
            title = title,
            category = cat,
            severityScore = severityScore,
            magnitude = magnitude,
            depthKm = depthKm,
            latitude = latitude,
            longitude = longitude,
            place = place,
            timestampMs = timestampMs,
            source = source,
            externalUrl = externalUrl,
            tsunamiAlert = tsunamiAlert,
            isHistorical = isHistorical
        )
    }

    companion object {
        fun fromDomain(
            event: DisasterEvent,
            timePeriodName: String,
            cachedAtMs: Long = System.currentTimeMillis()
        ) = MapHistoricalEventEntity(
            cacheKey = "${timePeriodName}_${event.id}",
            eventId = event.id,
            timePeriodName = timePeriodName,
            title = event.title,
            categoryName = event.category.name,
            severityScore = event.severityScore,
            magnitude = event.magnitude,
            depthKm = event.depthKm,
            latitude = event.latitude,
            longitude = event.longitude,
            place = event.place,
            timestampMs = event.timestampMs,
            source = event.source,
            externalUrl = event.externalUrl,
            tsunamiAlert = event.tsunamiAlert,
            isHistorical = event.isHistorical,
            cachedAtMs = cachedAtMs
        )
    }
}

@Entity(tableName = "cached_high_risk_zones")
data class HighRiskZoneCacheEntity(
    @PrimaryKey val id: String,
    val compositeRiskScore: Int,
    val liveClimateSummary: String?,
    val updatedAtMs: Long
)

@Entity(tableName = "push_alert_history")
data class PushAlertEntity(
    @PrimaryKey val id: String,
    val eventId: String?,
    val title: String,
    val body: String,
    val categoryName: String,
    val severityScore: Int,
    val latitude: Double?,
    val longitude: Double?,
    val distanceKm: Double?,
    val place: String,
    val source: String,
    val receivedAtMs: Long,
    val withinImmediateRadius: Boolean,
    val isRead: Boolean
) {
    fun toDomain(): PushAlertRecord {
        val cat = runCatching { DisasterCategory.valueOf(categoryName) }
            .getOrDefault(DisasterCategory.OTHER)
        return PushAlertRecord(
            id = id,
            eventId = eventId,
            title = title,
            body = body,
            category = cat,
            severityScore = severityScore,
            latitude = latitude,
            longitude = longitude,
            distanceKm = distanceKm,
            place = place,
            source = source,
            receivedAtMs = receivedAtMs,
            withinImmediateRadius = withinImmediateRadius,
            isRead = isRead
        )
    }
}

@Dao
interface DisasterEventDao {
    @Query("SELECT * FROM cached_disaster_events ORDER BY timestampMs DESC")
    fun observeAllEvents(): Flow<List<DisasterEventEntity>>

    @Query("SELECT * FROM cached_disaster_events ORDER BY timestampMs DESC")
    suspend fun getAllEvents(): List<DisasterEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<DisasterEventEntity>)

    @Query("DELETE FROM cached_disaster_events")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(events: List<DisasterEventEntity>) {
        clearAll()
        insertAll(events)
    }
}

@Dao
interface MapDisasterCacheDao {
    @Query("SELECT * FROM cached_map_disaster_events WHERE timePeriodName = :periodName ORDER BY timestampMs DESC")
    fun observeEventsByPeriod(periodName: String): Flow<List<MapHistoricalEventEntity>>

    @Query("SELECT * FROM cached_map_disaster_events ORDER BY timestampMs DESC")
    fun observeAllMapEvents(): Flow<List<MapHistoricalEventEntity>>

    @Query("SELECT * FROM cached_map_disaster_events WHERE timePeriodName = :periodName ORDER BY timestampMs DESC")
    suspend fun getEventsByPeriod(periodName: String): List<MapHistoricalEventEntity>

    @Query("SELECT * FROM cached_map_disaster_events ORDER BY timestampMs DESC")
    suspend fun getAllMapEvents(): List<MapHistoricalEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<MapHistoricalEventEntity>)

    @Query("DELETE FROM cached_map_disaster_events WHERE timePeriodName = :periodName")
    suspend fun deleteByPeriod(periodName: String)

    @Transaction
    suspend fun replaceEventsForPeriod(periodName: String, events: List<MapHistoricalEventEntity>) {
        deleteByPeriod(periodName)
        insertAll(events)
    }
}

@Dao
interface HighRiskZoneCacheDao {
    @Query("SELECT * FROM cached_high_risk_zones")
    fun observeZoneUpdates(): Flow<List<HighRiskZoneCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(zones: List<HighRiskZoneCacheEntity>)
}

@Dao
interface PushAlertDao {
    @Query("SELECT * FROM push_alert_history ORDER BY receivedAtMs DESC LIMIT 50")
    fun observeAlerts(): Flow<List<PushAlertEntity>>

    @Query("SELECT COUNT(*) FROM push_alert_history WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alert: PushAlertEntity)

    @Query("UPDATE push_alert_history SET isRead = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM push_alert_history")
    suspend fun clearAll()
}

@Database(
    entities = [
        DisasterEventEntity::class,
        MapHistoricalEventEntity::class,
        HighRiskZoneCacheEntity::class,
        PushAlertEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class DisasterDatabase : RoomDatabase() {
    abstract fun disasterEventDao(): DisasterEventDao
    abstract fun mapDisasterCacheDao(): MapDisasterCacheDao
    abstract fun highRiskZoneCacheDao(): HighRiskZoneCacheDao
    abstract fun pushAlertDao(): PushAlertDao

    companion object {
        @Volatile
        private var INSTANCE: DisasterDatabase? = null

        fun getInstance(context: Context): DisasterDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DisasterDatabase::class.java,
                    "disaster_telemetry_cache.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
