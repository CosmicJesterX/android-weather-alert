package dev.hossain.weatheralert.ui.alertslist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import com.slack.eithernet.ApiResult
import com.slack.eithernet.exceptionOrNull
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dev.hossain.weatheralert.R
import dev.hossain.weatheralert.data.AlertTileData
import dev.hossain.weatheralert.data.WeatherAlertCategory
import dev.hossain.weatheralert.data.WeatherRepository
import dev.hossain.weatheralert.data.icon
import dev.hossain.weatheralert.db.AlertDao
import dev.hossain.weatheralert.di.AppScope
import dev.hossain.weatheralert.ui.addalert.AlertSettingsScreen
import dev.hossain.weatheralert.ui.theme.WeatherAlertAppTheme
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.util.Locale

@Parcelize
data class CurrentWeatherAlertScreen(
    val id: String,
) : Screen {
    data class State(
        val tiles: List<AlertTileData>,
        val userMessage: String? = null,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed class Event : CircuitUiEvent {
        data class AlertRemoved(
            val item: AlertTileData,
        ) : Event()

        data object OnItemClicked : Event()

        data object AddNewAlertClicked : Event()

        data object MessageShown : Event()
    }
}

class CurrentWeatherAlertPresenter
    @AssistedInject
    constructor(
        @Assisted private val navigator: Navigator,
        @Assisted private val screen: CurrentWeatherAlertScreen,
        private val weatherRepository: WeatherRepository,
        private val alertDao: AlertDao,
    ) : Presenter<CurrentWeatherAlertScreen.State> {
        @Composable
        override fun present(): CurrentWeatherAlertScreen.State {
            val scope = rememberCoroutineScope()
            var weatherTiles by remember { mutableStateOf(emptyList<AlertTileData>()) }
            var userMessage by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                val userCityAlerts = alertDao.getAlertsWithCity()
                Timber.d("Found ${userCityAlerts.size} userCityAlerts.")

                val alertTileData = mutableListOf<AlertTileData>()
                userCityAlerts.forEach { alert ->
                    val apiResult =
                        weatherRepository.getDailyForecast(
                            latitude = alert.city.lat,
                            longitude = alert.city.lng,
                        )

                    when (apiResult) {
                        is ApiResult.Success -> {
                            val forecastData = apiResult.value
                            val snowStatus = forecastData.snow.nextDaySnow
                            val rainStatus = forecastData.rain.nextDayRain
                            alertTileData.add(
                                AlertTileData(
                                    alertId = alert.alert.id,
                                    cityInfo = alert.city.cityName,
                                    lat = alert.city.lat,
                                    lon = alert.city.lng,
                                    category = alert.alert.alertCategory,
                                    threshold =
                                        "%.2f %s".format(
                                            Locale.getDefault(),
                                            alert.alert.threshold,
                                            alert.alert.alertCategory.unit,
                                        ),
                                    currentStatus =
                                        "%.2f %s".format(
                                            Locale.getDefault(),
                                            if (alert.alert.alertCategory == WeatherAlertCategory.SNOW_FALL) {
                                                snowStatus
                                            } else {
                                                rainStatus
                                            },
                                            alert.alert.alertCategory.unit,
                                        ),
                                    // Wrong logic btw, fix later
                                    isAlertActive = alert.alert.threshold <= snowStatus || alert.alert.threshold <= rainStatus,
                                ),
                            )
                        }
                        is ApiResult.Failure -> {
                            Timber.d("Error fetching weather data: ${apiResult.exceptionOrNull()}")
                        }
                    }
                }
                weatherTiles = alertTileData
            }

            return CurrentWeatherAlertScreen.State(weatherTiles, userMessage) { event ->
                when (event) {
                    CurrentWeatherAlertScreen.Event.OnItemClicked -> TODO()
                    CurrentWeatherAlertScreen.Event.AddNewAlertClicked -> {
                        navigator.goTo(AlertSettingsScreen("add-new-alert"))
                    }

                    is CurrentWeatherAlertScreen.Event.AlertRemoved -> {
                        userMessage = "Alert removed."
                        val updatedTiles = weatherTiles.toMutableList()
                        updatedTiles.remove(event.item)
                        weatherTiles = updatedTiles
                        scope.launch {
                            alertDao.deleteAlertById(event.item.alertId)
                        }
                    }

                    CurrentWeatherAlertScreen.Event.MessageShown -> {
                        userMessage = null
                    }
                }
            }
        }

        @CircuitInject(CurrentWeatherAlertScreen::class, AppScope::class)
        @AssistedFactory
        fun interface Factory {
            fun create(
                navigator: Navigator,
                screen: CurrentWeatherAlertScreen,
            ): CurrentWeatherAlertPresenter
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@CircuitInject(CurrentWeatherAlertScreen::class, AppScope::class)
@Composable
fun CurrentWeatherAlerts(
    state: CurrentWeatherAlertScreen.State,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    WeatherAlertAppTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Weather Alerts") })
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {
                        state.eventSink(CurrentWeatherAlertScreen.Event.AddNewAlertClicked)
                    },
                    text = { Text("Add Alert") },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Add Alert") },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            content = { padding ->
                Column(
                    modifier =
                        modifier
                            .fillMaxSize()
                            .padding(padding),
                ) {
                    if (state.tiles.isEmpty()) {
                        EmptyAlertState()
                    } else {
                        AlertTileGrid(
                            tiles = state.tiles,
                            eventSink = state.eventSink,
                        )
                    }
                }
            },
        )
    }

    LaunchedEffect(state.userMessage) {
        state.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            state.eventSink(CurrentWeatherAlertScreen.Event.MessageShown)
        }
    }
}

@Composable
fun EmptyAlertState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(id = R.drawable.hiking_direction),
            contentDescription = "No alerts configured.",
        )
        Text(
            text = "No custom weather alerts configured.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun AlertTileGrid(
    tiles: List<AlertTileData>,
    eventSink: (CurrentWeatherAlertScreen.Event) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        itemsIndexed(
            items = tiles,
            key = { _, item -> item.alertId },
        ) { _, alertTileData ->
            AlertTileItem(
                alertTileData = alertTileData,
                eventSink = eventSink,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
fun AlertTileItem(
    alertTileData: AlertTileData,
    modifier: Modifier = Modifier,
    eventSink: (CurrentWeatherAlertScreen.Event) -> Unit,
) {
    val context = LocalContext.current
    val currentItem by rememberUpdatedState(alertTileData)
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = {
                when (it) {
                    SwipeToDismissBoxValue.StartToEnd,
                    SwipeToDismissBoxValue.EndToStart,
                    -> {
                        eventSink(CurrentWeatherAlertScreen.Event.AlertRemoved(currentItem))
                    }
                    SwipeToDismissBoxValue.Settled -> return@rememberSwipeToDismissBoxState false
                }
                return@rememberSwipeToDismissBoxState true
            },
            // positional threshold of 25%
            positionalThreshold = { it * .25f },
        )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = { DismissBackground(dismissState) },
        content = {
            val cardElevation: Dp =
                animateDpAsState(
                    if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) 8.dp else 4.dp,
                    label = "card-elevation",
                ).value
            AlertListItem(
                data = alertTileData,
                cardElevation = cardElevation,
                icon = alertTileData.category.icon(),
            )
            // AlertTile(data = alertTileData, cardElevation, modifier = Modifier.fillMaxWidth())
        },
    )
}

@Composable
fun DismissBackground(dismissState: SwipeToDismissBoxState) {
    val color =
        when (dismissState.dismissDirection) {
            SwipeToDismissBoxValue.StartToEnd -> Color.Red
            SwipeToDismissBoxValue.EndToStart -> Color.Red
            SwipeToDismissBoxValue.Settled -> Color.Transparent
        }

    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .background(color)
                .padding(12.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "delete",
        )
        Spacer(modifier = Modifier)
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "delete",
        )
    }
}

// Older card, evaluating if this should be used or `ListItem`
@Composable
fun AlertTile(
    data: AlertTileData,
    cardElevation: Dp,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(cardElevation),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text(
                text = data.category.name,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Threshold: ${data.threshold}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = data.currentStatus,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ChatGPT suggested item - evaluating if this should be used or `ListItem`
@Composable
fun AlertItem(
    data: AlertTileData,
    cardElevation: Dp,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(8.dp),
        elevation = CardDefaults.cardElevation(cardElevation),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = data.category.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Threshold: ${data.threshold}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Tomorrow: ${data.currentStatus}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun AlertListItem(
    data: AlertTileData,
    cardElevation: Dp,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp),
        elevation = CardDefaults.cardElevation(cardElevation),
        shape = RoundedCornerShape(12.dp),
    ) {
        val colors: ListItemColors =
            if (data.isAlertActive) {
                ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                )
            } else {
                ListItemDefaults.colors()
            }
        ListItem(
            headlineContent = {
                Text(
                    text =
                        when (data.category) {
                            WeatherAlertCategory.SNOW_FALL -> "Snowfall"
                            WeatherAlertCategory.RAIN_FALL -> "Rainfall"
                        },
                    style = MaterialTheme.typography.headlineMedium,
                )
            },
            supportingContent = {
                Column {
                    Text(text = "Threshold: ${data.threshold}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "Tomorrow: ${data.currentStatus}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            colors = colors,
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
            },
            modifier = Modifier.padding(0.dp),
        )
    }
}

@Composable
fun AnimatedAlertItemBackground(isAlertActive: Boolean) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isAlertActive) Color.Red else Color.White,
        animationSpec = tween(durationMillis = 500),
        label = "alert-pulse-animation",
    )
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(backgroundColor),
    ) {
        // Content here
    }
}

/**
 * 1. Smooth Animations for Alerts
 *
 *     Purpose: Draw attention to new alerts or updates in the app.
 *     Implementation:
 *         Use animated transitions in Jetpack Compose when new data arrives.
 *         Use a pulse effect or fade-in animation for alert tiles.
 */
@Composable
fun AlertTileEnhanced(
    category: String,
    threshold: String,
    status: String,
    isUpdated: Boolean,
) {
    val scale = remember { Animatable(1f) }

    LaunchedEffect(isUpdated) {
        if (isUpdated) {
            scale.animateTo(
                targetValue = 1.1f,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
            )
            scale.animateTo(1f, animationSpec = tween(200))
        }
    }

    Card(
        modifier =
            Modifier
                .scale(scale.value)
                .padding(8.dp),
        elevation = CardDefaults.cardElevation(4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = category, style = MaterialTheme.typography.headlineSmall)
            Text(text = "Threshold: $threshold", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Status: $status", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun CurrentWeatherAlertsPreview() {
    val sampleTiles =
        listOf(
            AlertTileData(
                alertId = 1,
                cityInfo = "Dhaka, Bangladesh",
                lat = 0.0,
                lon = 0.0,
                category = WeatherAlertCategory.SNOW_FALL,
                threshold = "5 cm",
                currentStatus = "Tomorrow: 7 cm",
                isAlertActive = false,
            ),
            AlertTileData(
                alertId = 2,
                cityInfo = "New York, USA",
                lat = 0.0,
                lon = 0.0,
                category = WeatherAlertCategory.RAIN_FALL,
                threshold = "10 mm",
                currentStatus = "Tomorrow: 12 mm",
                isAlertActive = true,
            ),
        )
    CurrentWeatherAlerts(CurrentWeatherAlertScreen.State(sampleTiles) {})
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun EmptyStatePreview() {
    WeatherAlertAppTheme {
        EmptyAlertState()
    }
}
