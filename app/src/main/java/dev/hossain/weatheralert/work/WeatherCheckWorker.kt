package dev.hossain.weatheralert.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.slack.eithernet.ApiResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.hossain.weatheralert.data.PreferencesManager
import dev.hossain.weatheralert.data.WeatherRepository
import dev.hossain.weatheralert.datamodel.WeatherAlertCategory
import dev.hossain.weatheralert.db.AlertDao
import dev.hossain.weatheralert.db.UserCityAlert
import dev.hossain.weatheralert.notification.triggerNotification
import dev.hossain.weatheralert.util.Analytics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Worker to check weather forecast and trigger notification if thresholds are exceeded.
 *
 * See additional details on Worker class:
 * - https://developer.android.com/topic/libraries/architecture/workmanager
 * - https://developer.android.com/reference/androidx/work/Worker
 * - https://developer.android.com/reference/kotlin/androidx/work/WorkManager
 */
class WeatherCheckWorker
    @AssistedInject
    constructor(
        @Assisted private val context: Context,
        @Assisted params: WorkerParameters,
        private val alertDao: AlertDao,
        private val weatherRepository: WeatherRepository,
        private val analytics: Analytics,
        private val preferencesManager: PreferencesManager,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            Timber.d("WeatherCheckWorker: Checking weather forecast")
            // Fetch thresholds from DataStore
            val userConfiguredAlerts = alertDao.getAllAlertsWithCities()

            if (userConfiguredAlerts.isEmpty()) {
                Timber.d("No user configured alerts found.")
                return Result.success()
            }

            logWorkerStarted(userConfiguredAlerts)

            userConfiguredAlerts.forEach { configuredAlert ->
                // Fetch forecast
                val forecastApiResult =
                    weatherRepository.getDailyForecast(
                        cityId = configuredAlert.city.id,
                        latitude = configuredAlert.city.lat,
                        longitude = configuredAlert.city.lng,
                        skipCache = true,
                    )

                when (forecastApiResult) {
                    is ApiResult.Success -> {
                        // Check if thresholds are exceeded
                        val snowTomorrow =
                            when {
                                forecastApiResult.value.snow.dailyCumulativeSnow > 0.0 -> {
                                    forecastApiResult.value.snow.dailyCumulativeSnow
                                }

                                else -> {
                                    forecastApiResult.value.snow.nextDaySnow
                                }
                            }
                        val rainTomorrow =
                            when {
                                forecastApiResult.value.rain.dailyCumulativeRain > 0.0 -> {
                                    forecastApiResult.value.rain.dailyCumulativeRain
                                }

                                else -> {
                                    forecastApiResult.value.rain.nextDayRain
                                }
                            }

                        Timber.d("Snow: $snowTomorrow, Rain: $rainTomorrow")

                        when (configuredAlert.alert.alertCategory) {
                            WeatherAlertCategory.SNOW_FALL -> {
                                if (snowTomorrow > configuredAlert.alert.threshold) {
                                    triggerNotification(
                                        context = context,
                                        notificationId = configuredAlert.alert.id.toInt(), // Precision loss?
                                        notificationTag = configuredAlert.toNotificationTag(),
                                        alertCategory = configuredAlert.alert.alertCategory,
                                        currentValue = snowTomorrow,
                                        thresholdValue = configuredAlert.alert.threshold,
                                        cityName = configuredAlert.city.city,
                                        reminderNotes = configuredAlert.alert.notes,
                                    )
                                }
                            }
                            WeatherAlertCategory.RAIN_FALL -> {
                                if (rainTomorrow > configuredAlert.alert.threshold) {
                                    triggerNotification(
                                        context = context,
                                        notificationId = configuredAlert.alert.id.toInt(), // Precision loss?
                                        notificationTag = configuredAlert.toNotificationTag(),
                                        alertCategory = configuredAlert.alert.alertCategory,
                                        currentValue = rainTomorrow,
                                        thresholdValue = configuredAlert.alert.threshold,
                                        cityName = configuredAlert.city.city,
                                        reminderNotes = configuredAlert.alert.notes,
                                    )
                                }
                            }
                        }
                    }

                    is ApiResult.Failure.HttpFailure -> {
                        return Result.retry()
                    }

                    is ApiResult.Failure.ApiFailure -> {
                        return Result.failure()
                    }

                    is ApiResult.Failure.NetworkFailure -> {
                        return Result.retry()
                    }

                    is ApiResult.Failure.UnknownFailure -> {
                        return Result.failure()
                    }
                }

                // Before checking next city, delay for 1 second to avoid rate limiting
                delay(1_000)
            }

            logWorkerCompleted()
            return Result.success()
        }

        private suspend fun logWorkerStarted(userConfiguredAlerts: List<UserCityAlert>) {
            // Log worker initiative to ensure the worker is working fine
            analytics.logWorkerJob(
                interval = preferencesManager.preferredUpdateInterval.first(),
                alertsCount = userConfiguredAlerts.size.toLong(),
            )
        }

        private suspend fun logWorkerCompleted() {
            analytics.logWorkSuccess()
        }
    }
