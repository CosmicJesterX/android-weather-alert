package dev.hossain.weatheralert.ui.addalert

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType.Companion.PrimaryEditable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dev.hossain.weatheralert.data.ConfiguredAlerts
import dev.hossain.weatheralert.data.PreferencesManager
import dev.hossain.weatheralert.data.WeatherAlert
import dev.hossain.weatheralert.data.WeatherAlertCategory
import dev.hossain.weatheralert.data.icon
import dev.hossain.weatheralert.db.AppDatabase
import dev.hossain.weatheralert.db.City
import dev.hossain.weatheralert.di.AppScope
import dev.hossain.weatheralert.ui.theme.WeatherAlertAppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import timber.log.Timber

@Parcelize
data class AlertSettingsScreen(
    val requestId: String,
) : Screen {
    data class State(
        val citySuggestions: List<City>,
        val snowThreshold: Float,
        val rainThreshold: Float,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed class Event : CircuitUiEvent {
        data class SnowThresholdChanged(
            val value: Float,
        ) : Event()

        data class RainThresholdChanged(
            val value: Float,
        ) : Event()

        data class SaveSettingsClicked(
            val selectedAlertType: WeatherAlertCategory,
            val snowThreshold: Float,
            val rainThreshold: Float,
        ) : Event()

        data class SearchQueryChanged(
            val query: String,
        ) : Event()

        data class OnCitySelected(
            val city: City,
        ) : Event()
    }
}

class AlertSettingsPresenter
    @AssistedInject
    constructor(
        @Assisted private val navigator: Navigator,
        @Assisted private val screen: AlertSettingsScreen,
        private val preferencesManager: PreferencesManager,
        private val database: AppDatabase,
    ) : Presenter<AlertSettingsScreen.State> {
        @Composable
        override fun present(): AlertSettingsScreen.State {
            val scope = rememberCoroutineScope()
            var updatedSnowThreshold by remember { mutableFloatStateOf(0f) }
            var updatedRainThreshold by remember { mutableFloatStateOf(0f) }
            var suggestions: List<City> by remember { mutableStateOf(emptyList()) }
            val context = LocalContext.current

            return AlertSettingsScreen.State(
                citySuggestions = suggestions,
                snowThreshold = updatedSnowThreshold,
                rainThreshold = updatedRainThreshold,
            ) { event ->
                when (event) {
                    is AlertSettingsScreen.Event.RainThresholdChanged -> {
                        updatedRainThreshold = event.value
                    }

                    is AlertSettingsScreen.Event.SnowThresholdChanged -> {
                        updatedSnowThreshold = event.value
                    }

                    is AlertSettingsScreen.Event.SaveSettingsClicked -> {
                        Timber.d("Save settings clicked: snow=${event.snowThreshold}, rain=${event.rainThreshold}")
                        scope.launch {
                            val configuredAlerts: ConfiguredAlerts =
                                preferencesManager.userConfiguredAlerts.first()
                            Timber.d("Current alerts: ${configuredAlerts.alerts}")

                            preferencesManager.updateUserConfiguredAlerts(
                                ConfiguredAlerts(
                                    configuredAlerts.alerts +
                                        WeatherAlert(
                                            alertCategory = event.selectedAlertType,
                                            threshold =
                                                when (event.selectedAlertType) {
                                                    WeatherAlertCategory.SNOW_FALL -> event.snowThreshold
                                                    WeatherAlertCategory.RAIN_FALL -> event.rainThreshold
                                                },
                                            // Use Oshawa coordinates for now 43°55'24.0"N+78°53'49.9"W/@43.9233409,-78.899766
                                            // https://github.com/hossain-khan/android-weather-alert/issues/30
                                            lat = 43.9233409,
                                            lon = -78.899766,
                                        ),
                                ),
                            )

                            // Finally after saving, navigate back
                            navigator.pop()
                        }
                    }

                    is AlertSettingsScreen.Event.SearchQueryChanged -> {
                        scope.launch {
                            database.cityDao().searchCitiesByName(event.query, 20).collect {
                                suggestions = it
                            }
                        }
                    }

                    is AlertSettingsScreen.Event.OnCitySelected -> {
                        Timber.d("Selected city: ${event.city}")
                    }
                }
            }
        }

        @CircuitInject(AlertSettingsScreen::class, AppScope::class)
        @AssistedFactory
        fun interface Factory {
            fun create(
                navigator: Navigator,
                screen: AlertSettingsScreen,
            ): AlertSettingsPresenter
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@CircuitInject(AlertSettingsScreen::class, AppScope::class)
@Composable
fun AlertSettingsScreen(
    state: AlertSettingsScreen.State,
    modifier: Modifier = Modifier,
) {
    WeatherAlertAppTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Configure Alerts") })
            },
        ) { padding ->
            Column(
                modifier =
                    modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                val checkedList: SnapshotStateList<Int> = remember { mutableStateListOf<Int>() }
                var selectedIndex: Int by remember { mutableIntStateOf(0) }
                val selectedAlertCategory: WeatherAlertCategory by remember {
                    derivedStateOf { WeatherAlertCategory.entries[selectedIndex] }
                }

                EditableCityInputDropdownMenu(
                    onQueryChange = {
                        state.eventSink(AlertSettingsScreen.Event.SearchQueryChanged(it))
                    },
                    suggestions = state.citySuggestions,
                    onSuggestionClick = {
                        state.eventSink(AlertSettingsScreen.Event.OnCitySelected(it))
                    },
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    WeatherAlertCategory.entries.forEachIndexed { index, alertCategory ->
                        SegmentedButton(
                            shape =
                                SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = WeatherAlertCategory.entries.size,
                                ),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = index in checkedList) {
                                    Icon(
                                        imageVector = alertCategory.icon(),
                                        contentDescription = null,
                                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                    )
                                }
                            },
                            onClick = { selectedIndex = index },
                            selected = index == selectedIndex,
                        ) { Text(alertCategory.label) }
                    }
                }

                Crossfade(targetState = selectedIndex, label = "threshold-slider") { thresholdIndex ->
                    when (thresholdIndex) {
                        0 ->
                            Column {
                                // Snow Threshold Slider
                                Text(
                                    text = "Snowfall Threshold: ${"%.1f".format(
                                        state.snowThreshold,
                                    )} ${WeatherAlertCategory.SNOW_FALL.unit}",
                                )
                                Slider(
                                    value = state.snowThreshold,
                                    onValueChange = {
                                        state.eventSink(
                                            AlertSettingsScreen.Event.SnowThresholdChanged(
                                                it,
                                            ),
                                        )
                                    },
                                    valueRange = 1f..20f,
                                )
                            }
                        1 ->
                            Column {
                                // Rain Threshold Slider
                                Text(
                                    text = "Rainfall Threshold: ${"%.1f".format(
                                        state.rainThreshold,
                                    )} ${WeatherAlertCategory.RAIN_FALL.unit}",
                                )
                                Slider(
                                    value = state.rainThreshold,
                                    onValueChange = {
                                        state.eventSink(
                                            AlertSettingsScreen.Event.RainThresholdChanged(
                                                it,
                                            ),
                                        )
                                    },
                                    valueRange = 1f..20f,
                                )
                            }
                    }
                }

                NotificationPermissionStatusUi()

                Button(
                    onClick = {
                        state.eventSink(
                            AlertSettingsScreen.Event.SaveSettingsClicked(
                                selectedAlertType = selectedAlertCategory,
                                snowThreshold = state.snowThreshold,
                                rainThreshold = state.rainThreshold,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Add Alert Settings")
                }
            }
        }
    }
}

/**
 * Composable function to display the UI for notification permission status.
 * It handles the permission request and updates the UI based on the permission status.
 */
@Composable
fun NotificationPermissionStatusUi() {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var labelText by remember { mutableStateOf("") }

    LaunchedEffect(permissionGranted) {
        labelText = if (permissionGranted) "Notification status" else "Turn on notification?"
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                // Permission granted, proceed with notifications
                permissionGranted = true
                labelText = "Notification status"
            } else {
                // Permission denied, handle accordingly
                permissionGranted = false
                labelText = "Denied notification permission"
            }
        }

    if (requiresNotificationPermission()) {
        when {
            hasNotificationPermission(context) -> {
                // Permission already granted, proceed with notifications
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = labelText)
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        tint = MaterialTheme.colorScheme.secondary,
                        contentDescription = null,
                    )
                }
            }
            else -> {
                // Request the permission
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = labelText)
                    Switch(
                        checked = permissionGranted,
                        onCheckedChange = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditableCityInputDropdownMenu(
    onQueryChange: (String) -> Unit,
    suggestions: List<City>,
    onSuggestionClick: (City) -> Unit,
) {
    val textFieldState = rememberTextFieldState()

    // The text that the user inputs into the text field can be used to filter the options.
    // This sample uses string subsequence matching.
    val filteredOptions = suggestions

    val (allowExpanded, setExpanded) = remember { mutableStateOf(false) }
    val expanded = allowExpanded && filteredOptions.isNotEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = setExpanded,
    ) {
        OutlinedTextField(
            value = textFieldState.text.toString(),
            onValueChange = { newValue ->
                textFieldState.setTextAndPlaceCursorAtEnd(newValue)
                onQueryChange(newValue)
                setExpanded(newValue.isNotEmpty())
            },
            // The `menuAnchor` modifier must be passed to the text field to handle
            // expanding/collapsing the menu on click. An editable text field has
            // the anchor type `PrimaryEditable`.
            modifier = Modifier.fillMaxWidth().menuAnchor(PrimaryEditable),
            label = { Text("City") },
            placeholder = { Text("Search...") },
            singleLine = true,
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
        )
        ExposedDropdownMenu(
            modifier = Modifier.heightIn(max = 280.dp),
            expanded = expanded,
            onDismissRequest = { setExpanded(false) },
        ) {
            filteredOptions.forEach { city ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(text = city.city_ascii, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(text = "${city.city}, ${city.admin_name}, ${city.country}", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick = {
                        textFieldState.setTextAndPlaceCursorAtEnd(city.city_ascii) // city.text?
                        setExpanded(false)
                        onSuggestionClick(city)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

private fun requiresNotificationPermission() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

private fun hasNotificationPermission(context: Context) =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun SettingsScreenPreview() {
    AlertSettingsScreen(
        AlertSettingsScreen.State(
            citySuggestions = emptyList(),
            snowThreshold = 5.0f,
            rainThreshold = 10.0f,
        ) {},
    )
}
