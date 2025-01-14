package io.tomorrow.api

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import dev.hossain.weatheralert.datamodel.AppForecastData
import dev.hossain.weatheralert.datamodel.WeatherApiServiceResponse
import io.tomorrow.api.model.WeatherResponse
import org.junit.Test

/**
 * Tests tomorrow.io [WeatherResponse] to [AppForecastData] converter.
 */
class WeatherResponseConverterTest {
    @Test
    fun convertsBostonWeatherResponseToAppForecastData() {
        val weatherResponse = loadWeatherResponseFromJson("tomorrow-io-boston-forecast-2025-01-10.json")

        val result = weatherResponse.convertToForecastData()

        assertThat(result.latitude).isEqualTo(42.3478)
        assertThat(result.longitude).isEqualTo(-71.0466)
        assertThat(result.snow.dailyCumulativeSnow).isEqualTo(26.6)
        assertThat(result.snow.nextDaySnow).isEqualTo(0.0)
        assertThat(result.snow.weeklyCumulativeSnow).isEqualTo(0.0)
        assertThat(result.rain.dailyCumulativeRain).isEqualTo(0.0)
        assertThat(result.rain.nextDayRain).isEqualTo(0.0)
        assertThat(result.rain.weeklyCumulativeRain).isEqualTo(0.0)
    }

    @Test
    fun convertsOshawaWeatherResponseToAppForecastData() {
        val weatherResponse = loadWeatherResponseFromJson("tomorrow-io-oshawa-forecast-2025-01-10.json")

        val result = weatherResponse.convertToForecastData()

        assertThat(result.latitude).isEqualTo(43.9)
        assertThat(result.longitude).isEqualTo(-78.85)
        assertThat(result.snow.dailyCumulativeSnow).isEqualTo(163.1)
        assertThat(result.snow.nextDaySnow).isEqualTo(0.0)
        assertThat(result.snow.weeklyCumulativeSnow).isEqualTo(0.0)
        assertThat(result.rain.dailyCumulativeRain).isEqualTo(0.0)
        assertThat(result.rain.nextDayRain).isEqualTo(0.0)
        assertThat(result.rain.weeklyCumulativeRain).isEqualTo(0.0)
    }

    @Test
    fun handlesEmptyHourlyAndDailyForecasts() {
        val weatherResponse = loadWeatherResponseFromJson("tomorrow-weather-empty-forecasts.json")

        val result = weatherResponse.convertToForecastData()

        assertThat(result.latitude).isEqualTo(40.7128)
        assertThat(result.longitude).isEqualTo(-74.0060)
        assertThat(result.snow.dailyCumulativeSnow).isEqualTo(0.0)
        assertThat(result.snow.nextDaySnow).isEqualTo(0.0)
        assertThat(result.snow.weeklyCumulativeSnow).isEqualTo(0.0)
        assertThat(result.rain.dailyCumulativeRain).isEqualTo(0.0)
        assertThat(result.rain.nextDayRain).isEqualTo(0.0)
        assertThat(result.rain.weeklyCumulativeRain).isEqualTo(0.0)
    }

    @Test
    fun handlesNullSnowAndRainVolumes() {
        val weatherResponse = loadWeatherResponseFromJson("tomorrow-weather-null-snow-rain.json")

        val result = weatherResponse.convertToForecastData()

        assertThat(result.latitude).isEqualTo(42.3478)
        assertThat(result.longitude).isEqualTo(-71.0466)
        assertThat(result.snow.dailyCumulativeSnow).isEqualTo(0.0)
        assertThat(result.snow.nextDaySnow).isEqualTo(0.0)
        assertThat(result.snow.weeklyCumulativeSnow).isEqualTo(0.0)
        assertThat(result.rain.dailyCumulativeRain).isEqualTo(0.0)
        assertThat(result.rain.nextDayRain).isEqualTo(0.0)
        assertThat(result.rain.weeklyCumulativeRain).isEqualTo(0.0)
    }

    // Helper method to load WeatherResponse from JSON
    private fun loadWeatherResponseFromJson(fileName: String): WeatherApiServiceResponse {
        val classLoader = javaClass.classLoader
        val inputStream = classLoader?.getResourceAsStream(fileName)
        val json = inputStream?.bufferedReader().use { it?.readText() } ?: throw IllegalArgumentException("File not found: $fileName")
        return Moshi
            .Builder()
            .build()
            .adapter(WeatherResponse::class.java)
            .fromJson(json)!!
    }
}
