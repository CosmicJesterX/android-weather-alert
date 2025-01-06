package dev.hossain.weatheralert.data

import android.os.Parcelable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.ui.graphics.vector.ImageVector
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

const val DEFAULT_SNOW_THRESHOLD = 5.0f // cm
const val DEFAULT_RAIN_THRESHOLD = 10.0f // mm

/**
 * Weather forecast data.
 *
 * Sample JSON:
 * ```json
 * {
 *   "lat": 38.4685,
 *   "lon": -100.9596,
 *   "timezone": "America/Chicago",
 *   "timezone_offset": -21600,
 *   "current": {},
 *   "hourly": [],
 *   "daily": [],
 *   "alerts": []
 * }
 * ```
 */
@JsonClass(generateAdapter = true)
data class WeatherForecast(
    val lat: Double,
    val lon: Double,
    val timezone: String,
    @Json(name = "timezone_offset") val timezoneOffset: Int,
    val hourly: List<HourlyForecast> = emptyList(),
    val daily: List<DailyForecast> = emptyList(),
) {
    val totalHourlySnowVolume: Double
        get() = hourly.sumOf { it.snow?.snowVolumeInAnHour ?: 0.0 }

    val totalHourlyRainVolume: Double
        get() = hourly.sumOf { it.rain?.rainVolumeInAnHour ?: 0.0 }
}

@JsonClass(generateAdapter = true)
data class DailyForecast(
    @Json(name = "dt") val date: Long, // Unix timestamp
    @Json(name = "temp") val temperature: Temperature,
    @Json(name = "weather") val weather: List<WeatherDescription>,
    @Json(name = "rain") val rainVolume: Double?, // mm (nullable)
    @Json(name = "snow") val snowVolume: Double?, // cm (nullable)
)

/**
 * Hourly forecast data.
 *
 * Sample JSON:
 * ```json
 * {
 *   "dt": 1736024400,
 *   "temp": -3.8,
 *   "feels_like": -10.8,
 *   "pressure": 1012,
 *   "humidity": 86,
 *   "dew_point": -6.03,
 *   "uvi": 0.09,
 *   "clouds": 100,
 *   "visibility": 3002,
 *   "wind_speed": 8.17,
 *   "wind_deg": 274,
 *   "wind_gust": 13.8,
 *   "weather": [
 *     {
 *       "id": 600,
 *       "main": "Snow",
 *       "description": "light snow",
 *       "icon": "13d"
 *     }
 *   ],
 *   "pop": 1,
 *   "snow": {
 *     "1h": 0.35
 *   },
 *   "rain": {
 *     "1h": 4.86
 *   }
 * }
 * ```
 */
@JsonClass(generateAdapter = true)
data class HourlyForecast(
    @Json(name = "dt") val date: Long, // Unix timestamp
    @Json(name = "temp") val temperature: Double,
    @Json(name = "feels_like") val feelsLike: Double,
    @Json(name = "pressure") val pressure: Int,
    @Json(name = "humidity") val humidity: Int,
    @Json(name = "dew_point") val dewPoint: Double,
    @Json(name = "uvi") val uvi: Double,
    @Json(name = "clouds") val clouds: Int,
    @Json(name = "visibility") val visibility: Int? = null,
    @Json(name = "wind_speed") val windSpeed: Double,
    @Json(name = "wind_deg") val windDeg: Int,
    @Json(name = "wind_gust") val windGust: Double,
    @Json(name = "weather") val weather: List<WeatherDescription>,
    @Json(name = "pop") val pop: Double,
    @Json(name = "snow") val snow: SnowVolume? = null,
    @Json(name = "rain") val rain: RainVolume? = null,
)

/**
 * Snow volume data.
 *
 * Sample JSON:
 * ```json
 * "snow":
 * {
 *   "1h": 0.35
 * }
 * ```
 */
@JsonClass(generateAdapter = true)
data class SnowVolume(
    @Json(name = "1h") val snowVolumeInAnHour: Double,
)

/**
 * Rain volume data.
 *
 * Sample JSON:
 * ```json
 * "rain":
 * {
 *   "1h": 2.29
 * }
 * ```
 */
@JsonClass(generateAdapter = true)
data class RainVolume(
    @Json(name = "1h") val rainVolumeInAnHour: Double,
)

@JsonClass(generateAdapter = true)
data class Temperature(
    val min: Double,
    val max: Double,
)

/**
 * Weather description data.
 *
 * Sample JSON:
 * ```json
 * {
 *   "id": 600,
 *   "main": "Snow",
 *   "description": "light snow",
 *   "icon": "13n"
 * }
 * ```
 *
 * ```json
 * {
 *   "id": 803,
 *   "main": "Clouds",
 *   "description": "broken clouds",
 *   "icon": "04n"
 * }
 * ```
 */
@JsonClass(generateAdapter = true)
data class WeatherDescription(
    val main: String,
    val description: String,
    val icon: String,
)

@Parcelize
data class AlertTileData constructor(
    val lat: Double,
    val lon: Double,
    /**
     * e.g., "Snowfall", "Rainfall"
     */
    val category: WeatherAlertCategory,
    /**
     * e.g., "5 cm", "10 mm"
     */
    val threshold: String,
    /**
     * e.g., "Tomorrow: 7 cm", "Tomorrow: 15 mm"
     */
    val currentStatus: String,
    val isAlertActive: Boolean,
    val uuid: String =
        java.util.UUID
            .randomUUID()
            .toString(),
) : Parcelable

enum class WeatherAlertCategory(
    val label: String,
    val unit: String,
) {
    SNOW_FALL("Snow", "cm"),
    RAIN_FALL("Rain", "mm"),
}

internal fun WeatherAlertCategory.icon(): ImageVector =
    when (this) {
        WeatherAlertCategory.SNOW_FALL -> Icons.Outlined.AcUnit
        WeatherAlertCategory.RAIN_FALL -> Icons.Outlined.Umbrella
    }

@JsonClass(generateAdapter = true)
data class WeatherAlert(
    val alertCategory: WeatherAlertCategory,
    val threshold: Float,
    val lat: Double,
    val lon: Double,
    val cityName: String = "",
)

@JsonClass(generateAdapter = true)
data class ConfiguredAlerts(
    val alerts: List<WeatherAlert>,
)

data class ForecastData(
    val cityName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val snow: Snow = Snow(),
    val rain: Rain = Rain(),
)

data class Snow(
    val dailyCumulativeSnow: Double = 0.0,
    val nextDaySnow: Double = 0.0,
    val weeklyCumulativeSnow: Double = 0.0,
)

data class Rain(
    val dailyCumulativeRain: Double = 0.0,
    val nextDayRain: Double = 0.0,
    val weeklyCumulativeRain: Double = 0.0,
)
