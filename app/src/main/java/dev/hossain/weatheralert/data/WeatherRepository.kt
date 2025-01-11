package dev.hossain.weatheralert.data
import com.slack.eithernet.ApiResult
import com.squareup.anvil.annotations.ContributesBinding
import dev.hossain.weatheralert.db.CityForecast
import dev.hossain.weatheralert.db.CityForecastDao
import dev.hossain.weatheralert.di.AppScope
import dev.hossain.weatheralert.util.TimeUtil
import io.tomorrow.api.TomorrowIoService
import io.tomorrow.api.model.WeatherResponse
import org.openweathermap.api.OpenWeatherService
import org.openweathermap.api.model.ErrorResponse
import org.openweathermap.api.model.WeatherForecast
import timber.log.Timber
import javax.inject.Inject

/**
 * Repository for weather data that caches and provides weather forecast data.
 */
interface WeatherRepository {
    /**
     * Provides the daily weather forecast for a given city either from
     * cache or network based on data freshness or [skipCache] value.
     *
     * @param cityId The ID of the city from the database.
     * @param latitude The latitude of the city.
     * @param longitude The longitude of the city.
     * @param skipCache Whether to skip the cache and fetch fresh data.
     * @return An [ApiResult] containing the [ForecastData] or an error.
     */
    suspend fun getDailyForecast(
        cityId: Int,
        latitude: Double,
        longitude: Double,
        skipCache: Boolean = false,
    ): ApiResult<ForecastData, Unit>

    /**
     * Validates the given API key by sending a basic API request.
     */
    suspend fun isValidApiKey(apiKey: String): ApiResult<Boolean, ErrorResponse>
}

/**
 * Implementation of [WeatherRepository] that uses [OpenWeatherService] to fetch weather data.
 */
@ContributesBinding(AppScope::class)
class WeatherRepositoryImpl
    @Inject
    constructor(
        private val apiKey: ApiKey,
        private val openWeatherService: OpenWeatherService,
        private val tomorrowIoService: TomorrowIoService,
        private val cityForecastDao: CityForecastDao,
        private val timeUtil: TimeUtil,
        private val activeWeatherService: ActiveWeatherService,
    ) : WeatherRepository {
        override suspend fun getDailyForecast(
            cityId: Int,
            latitude: Double,
            longitude: Double,
            skipCache: Boolean,
        ): ApiResult<ForecastData, Unit> {
            val cityForecast = cityForecastDao.getCityForecastsByCityId(cityId).firstOrNull()

            return if (skipCache.not() &&
                cityForecast != null &&
                !timeUtil.isOlderThan24Hours(
                    cityForecast.createdAt,
                )
            ) {
                Timber.d("Using cached forecast data for cityId: %s, skipCache: %s", cityId, skipCache)
                ApiResult.success(convertToForecastData(cityForecast))
            } else {
                Timber.d(
                    "Fetching forecast data from network for cityId %s, skipCache: %s",
                    cityId,
                    skipCache,
                )
                loadForecastFromNetwork(latitude, longitude, cityId)
            }
        }

        override suspend fun isValidApiKey(apiKey: String): ApiResult<Boolean, ErrorResponse> {
            openWeatherService
                .getWeatherOverview(
                    apiKey = apiKey,
                    // Use New York City coordinates for basic API key validation.
                    latitude = 40.7235827,
                    longitude = -73.985626,
                ).let { apiResult ->
                    return when (apiResult) {
                        is ApiResult.Success -> ApiResult.success(true)
                        is ApiResult.Failure.ApiFailure -> ApiResult.apiFailure(apiResult.error)
                        is ApiResult.Failure.HttpFailure ->
                            ApiResult.httpFailure(
                                apiResult.code,
                                apiResult.error,
                            )

                        is ApiResult.Failure.NetworkFailure -> ApiResult.networkFailure(apiResult.error)
                        is ApiResult.Failure.UnknownFailure -> ApiResult.unknownFailure(apiResult.error)
                    }
                }
        }

        private suspend fun loadForecastFromNetwork(
            latitude: Double,
            longitude: Double,
            cityId: Int,
        ): ApiResult<ForecastData, Unit> {
            val selectedService = activeWeatherService.selectedService()
            return when (selectedService) {
                WeatherService.OPEN_WEATHER_MAP ->
                    loadForecastUseOpenWeather(
                        latitude,
                        longitude,
                        cityId,
                    )

                WeatherService.TOMORROW_IO -> loadForecastUseTomorrowIo(latitude, longitude, cityId)
            }
        }

        private suspend fun WeatherRepositoryImpl.loadForecastUseOpenWeather(
            latitude: Double,
            longitude: Double,
            cityId: Int,
        ): ApiResult<ForecastData, Unit> {
            val apiResult =
                openWeatherService.getDailyForecast(
                    apiKey = apiKey.key,
                    latitude = latitude,
                    longitude = longitude,
                )
            return when (apiResult) {
                is ApiResult.Success -> {
                    val convertToForecastData = apiResult.value.toForecastData()
                    cacheCityForecastData(cityId, convertToForecastData)
                    ApiResult.success(convertToForecastData)
                }

                is ApiResult.Failure -> {
                    apiResult
                }
            }
        }

        private suspend fun WeatherRepositoryImpl.loadForecastUseTomorrowIo(
            latitude: Double,
            longitude: Double,
            cityId: Int,
        ): ApiResult<ForecastData, Unit> {
            val apiResult =
                tomorrowIoService.getWeatherForecast(
                    location = "$latitude,$longitude",
                    apiKey = apiKey.key,
                )
            return when (apiResult) {
                is ApiResult.Success -> {
                    val convertToForecastData = apiResult.value.toForecastData()
                    cacheCityForecastData(cityId, convertToForecastData)
                    ApiResult.success(convertToForecastData)
                }

                is ApiResult.Failure -> {
                    apiResult
                }
            }
        }

        private suspend fun cacheCityForecastData(
            cityId: Int,
            convertToForecastData: ForecastData,
        ) {
            cityForecastDao.insertCityForecast(
                CityForecast(
                    cityId = cityId,
                    latitude = convertToForecastData.latitude,
                    longitude = convertToForecastData.longitude,
                    dailyCumulativeSnow = convertToForecastData.snow.dailyCumulativeSnow,
                    nextDaySnow = convertToForecastData.snow.nextDaySnow,
                    dailyCumulativeRain = convertToForecastData.rain.dailyCumulativeRain,
                    nextDayRain = convertToForecastData.rain.nextDayRain,
                ),
            )
        }

        private fun WeatherForecast.toForecastData(): ForecastData =
            ForecastData(
                latitude = lat,
                longitude = lon,
                snow =
                    Snow(
                        dailyCumulativeSnow =
                            hourly
                                .sumOf { it.snow?.snowVolumeInAnHour ?: 0.0 },
                        nextDaySnow =
                            daily
                                .firstOrNull()
                                ?.snowVolume ?: 0.0,
                        weeklyCumulativeSnow = 0.0,
                    ),
                rain =
                    Rain(
                        dailyCumulativeRain =
                            hourly
                                .sumOf { it.rain?.rainVolumeInAnHour ?: 0.0 },
                        nextDayRain =
                            daily
                                .firstOrNull()
                                ?.rainVolume ?: 0.0,
                        weeklyCumulativeRain = 0.0,
                    ),
            )

        private fun convertToForecastData(cityForecast: CityForecast) =
            ForecastData(
                latitude = cityForecast.latitude,
                longitude = cityForecast.longitude,
                snow =
                    Snow(
                        dailyCumulativeSnow = cityForecast.dailyCumulativeSnow,
                        nextDaySnow = cityForecast.nextDaySnow,
                        weeklyCumulativeSnow = 0.0,
                    ),
                rain =
                    Rain(
                        dailyCumulativeRain = cityForecast.dailyCumulativeRain,
                        nextDayRain = cityForecast.nextDayRain,
                        weeklyCumulativeRain = 0.0,
                    ),
            )

        private fun WeatherResponse.toForecastData(): ForecastData {
            // Convert `WeatherResponse` to `ForecastData`
            return ForecastData(
                latitude = location.latitude,
                longitude = location.longitude,
                snow =
                    Snow(
                        dailyCumulativeSnow =
                            timelines.hourly
                                .sumOf { it.values.snowDepth ?: 0.0 },
                        nextDaySnow =
                            timelines.daily
                                .firstOrNull()
                                ?.values
                                ?.snowAccumulation ?: 0.0,
                        weeklyCumulativeSnow = 0.0,
                    ),
                rain =
                    Rain(
                        dailyCumulativeRain =
                            timelines.hourly
                                .sumOf { it.values.rainAccumulation ?: 0.0 },
                        nextDayRain =
                            timelines.daily
                                .firstOrNull()
                                ?.values
                                ?.rainAccumulation ?: 0.0,
                        weeklyCumulativeRain = 0.0,
                    ),
            )
        }
    }
