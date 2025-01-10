package dev.hossain.weatheralert.api

import com.slack.eithernet.ApiResult
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * OpenWeatherMap API for weather forecast.
 *
 * Free tier:
 * - 60 calls/minute
 * - 1,000 API calls/day
 * - 1,000,000 calls/month
 *
 * See:
 * - [Weather units](https://openweathermap.org/weather-data)
 * - [One Call API](https://openweathermap.org/api/one-call-3)
 */
interface WeatherApi {
    companion object {
        // Units of measurement - `standard`, `metric`, and `imperial` units are available.
        // However, `metric` as baseline and conversion will be used later if needed.
        internal const val UNIT_METRIC = "metric"

        /**
         * You can get the error 401 in the following cases:
         *
         * - You did not specify your API key in API request.
         * - Your API key is not activated yet. Within the next couple of hours, it will be activated and ready to use.
         * - You are using wrong API key in API request. Please, check your right API key in personal account.
         * - You are using a Free subscription and try requesting data available in other subscriptions .
         *   For example, 16 days/daily forecast API, any historical weather data, Weather maps 2.0, etc).
         *   Please, check your subscription in your personal account.
         */
        internal const val ERROR_HTTP_UNAUTHORIZED = 401

        /**
         * You can get this error when you specified the wrong city name, ZIP-code or city ID.
         * For your reference, this list contains City name, City ID, Geographical coordinates
         * of the city (lon, lat), Zoom, etc.
         *
         * You can also get the error 404 if the format of your API request is incorrect.
         * In this case, please review it and check for any mistakes. To see examples of
         * correct API requests, please visit the Documentation of a specific API and read
         * the examples of API calls there.
         */
        internal const val ERROR_HTTP_NOT_FOUND = 404

        /**
         * You can receive this error in the following cases:
         *
         * - If you have a Free plan of Professional subscriptions and make more than 60 API calls
         *   per minute (surpassing the limit of your subscription). To avoid this situation,
         *   please consider upgrading to a subscription plan that meets your needs or reduce the
         *   number of API calls in accordance with the limits.
         */
        internal const val ERROR_HTTP_TOO_MANY_REQUESTS = 429
    }

    @GET("data/3.0/onecall")
    suspend fun getDailyForecast(
        @Query("appid") apiKey: String,
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("exclude") exclude: String = "current,minutely",
        @Query("units") units: String = UNIT_METRIC,
    ): ApiResult<WeatherForecast, Unit>

    @GET("data/3.0/onecall/overview")
    suspend fun getWeatherOverview(
        @Query("appid") apiKey: String,
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
    ): ApiResult<WeatherOverview, Unit>
}
