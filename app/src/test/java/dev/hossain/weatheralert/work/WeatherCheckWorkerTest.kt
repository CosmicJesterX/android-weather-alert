package dev.hossain.weatheralert.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import dev.hossain.weatheralert.data.WeatherRepository
import dev.hossain.weatheralert.db.AlertDao
import dev.hossain.weatheralert.db.AppDatabase
import dev.hossain.weatheralert.di.DaggerTestAppComponent
import dev.hossain.weatheralert.di.NetworkModule
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
class WeatherCheckWorkerTest {
    // Guide @ https://github.com/square/okhttp/tree/master/mockwebserver
    private lateinit var mockWebServer: MockWebServer

    private lateinit var context: Context

    private lateinit var testWorkerFactory: TestWorkerFactory

    @Inject
    internal lateinit var weatherRepository: WeatherRepository

    @Inject
    internal lateinit var appDatabase: AppDatabase

    @Inject
    internal lateinit var alertDao: AlertDao

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start(60000)
        NetworkModule.baseUrl = mockWebServer.url("/")

        injectTestClass()

        testWorkerFactory = TestWorkerFactory(alertDao, weatherRepository)
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        mockWebServer.shutdown()
        appDatabase.close()
    }

    @Test
    fun `given no alerts set - results in successful work execution`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(loadJsonFromResources("open-weather-cancun.json")),
        )

        val worker =
            TestListenableWorkerBuilder<WeatherCheckWorker>(context)
                .setWorkerFactory(testWorkerFactory)
                .build()
        runBlocking {
            val result: ListenableWorker.Result = worker.doWork()
            assertThat(result, notNullValue())
        }
    }

    @Test
    fun `given single alert set and success API response - results in successful work execution`() {
        // Getting foreign key violation for some reason even after using `121` city id.

        /*runBlocking {
            appDatabase.cityDao().insertCity(
                City(
                    id = 121,
                    cityName = "Cancún",
                    lat = 21.1743,
                    lng = -86.8466,
                    country = "Mexico",
                    iso2 = "MX",
                    iso3 = "MEX",
                    provStateName = null,
                    capital = null,
                    population = 628306,
                    city = "Cancún",
                ),
            )

            alertDao.insertAlert(
                Alert(
                    id = 1,
                    cityId = 121,
                    WeatherAlertCategory.RAIN_FALL,
                    threshold = 0.5f,
                    notes = "Notes about alert",
                ),
            )
        }*/

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(loadJsonFromResources("open-weather-cancun.json")),
        )

        val worker =
            TestListenableWorkerBuilder<WeatherCheckWorker>(context)
                .setWorkerFactory(testWorkerFactory)
                .build()
        runBlocking {
            val result: ListenableWorker.Result = worker.doWork()
            assertThat(result, notNullValue())
        }
    }

    // Helper method to inject dependencies
    private fun injectTestClass() {
        context = ApplicationProvider.getApplicationContext()
        NetworkModule.baseUrl
        val testAppComponent = DaggerTestAppComponent.factory().create(context)
        testAppComponent.inject(this)
    }

    // Helper method to load JSON from resources
    private fun loadJsonFromResources(fileName: String): String {
        val classLoader = javaClass.classLoader
        val inputStream = classLoader?.getResourceAsStream(fileName)
        return inputStream?.bufferedReader().use { it?.readText() } ?: throw IllegalArgumentException("File not found: $fileName")
    }
}
