package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- USGS Earthquake API ---
data class UsgsGeoJsonResponse(
    val features: List<UsgsFeatureDto> = emptyList()
)

data class UsgsFeatureDto(
    val id: String = "",
    val properties: UsgsPropertiesDto = UsgsPropertiesDto(),
    val geometry: UsgsGeometryDto = UsgsGeometryDto()
)

data class UsgsPropertiesDto(
    val mag: Double? = null,
    val place: String? = null,
    val time: Long? = null,
    val url: String? = null,
    val title: String? = null,
    val tsunami: Int? = null
)

data class UsgsGeometryDto(
    val coordinates: List<Double> = emptyList()
)

interface UsgsEarthquakeApi {
    @GET("fdsnws/event/1/query")
    suspend fun getRecentEarthquakes(
        @Query("format") format: String = "geojson",
        @Query("limit") limit: Int = 40,
        @Query("minmagnitude") minMagnitude: Double = 2.5,
        @Query("orderby") orderBy: String = "time",
        @Query("starttime") startTime: String? = null,
        @Query("endtime") endTime: String? = null
    ): UsgsGeoJsonResponse
}

// --- NASA EONET v3 API ---
data class EonetResponseDto(
    val events: List<EonetEventDto> = emptyList()
)

data class EonetEventDto(
    val id: String = "",
    val title: String = "",
    val link: String? = null,
    val categories: List<EonetCategoryDto> = emptyList(),
    val geometry: List<EonetGeometryDto> = emptyList()
)

data class EonetCategoryDto(
    val id: String = "",
    val title: String = ""
)

data class EonetGeometryDto(
    val date: String? = null,
    val type: String? = null,
    val coordinates: Any? = null
)

interface NasaEonetApi {
    @GET("api/v3/events")
    suspend fun getOpenEvents(
        @Query("status") status: String = "open",
        @Query("limit") limit: Int = 25,
        @Query("days") days: Int? = null
    ): EonetResponseDto
}

// --- Open-Meteo Weather Hazard API ---
data class OpenMeteoResponseDto(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val current: OpenMeteoCurrentDto? = null
)

data class OpenMeteoCurrentDto(
    val time: String? = null,
    @Json(name = "temperature_2m") val temperature2m: Double? = null,
    @Json(name = "relative_humidity_2m") val relativeHumidity2m: Int? = null,
    val precipitation: Double? = null,
    val rain: Double? = null,
    @Json(name = "weather_code") val weatherCode: Int? = null,
    @Json(name = "surface_pressure") val surfacePressure: Double? = null,
    @Json(name = "wind_speed_10m") val windSpeed10m: Double? = null,
    @Json(name = "wind_gusts_10m") val windGusts10m: Double? = null
)

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,precipitation,rain,weather_code,surface_pressure,wind_speed_10m,wind_gusts_10m",
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoResponseDto
}

object DisasterNetworkModule {
    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    val usgsApi: UsgsEarthquakeApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://earthquake.usgs.gov/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(UsgsEarthquakeApi::class.java)
    }

    val eonetApi: NasaEonetApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://eonet.gsfc.nasa.gov/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(NasaEonetApi::class.java)
    }

    val openMeteoApi: OpenMeteoApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenMeteoApi::class.java)
    }

    fun createFcmServerApi(baseUrl: String): FcmServerApi {
        val normalized = baseUrl.trim().let { if (it.endsWith("/")) it else "$it/" }
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FcmServerApi::class.java)
    }
}

// --- Custom DisasterPulse FCM Push Server API (fcm-server/server.js) ---
data class RegisterPushLocationRequest(
    val fcmToken: String,
    val latitude: Double,
    val longitude: Double,
    val locationLabel: String,
    val radiusKm: Double,
    val topics: List<String>
)

data class SendTestAlertRequest(
    val fcmToken: String?,
    val latitude: Double,
    val longitude: Double,
    val locationLabel: String
)

data class FcmServerResponse(
    val ok: Boolean = false,
    val mode: String? = null,
    val messageId: String? = null,
    val registeredCount: Int? = null,
    val scannedEvents: Int? = null,
    val newlyDispatched: Int? = null,
    val error: String? = null
)

interface FcmServerApi {
    @POST("api/register-location")
    suspend fun registerLocation(@Body request: RegisterPushLocationRequest): FcmServerResponse

    @POST("api/send-test-alert")
    suspend fun sendTestAlert(@Body request: SendTestAlertRequest): FcmServerResponse

    @POST("api/poll-now")
    suspend fun pollNow(): FcmServerResponse
}
