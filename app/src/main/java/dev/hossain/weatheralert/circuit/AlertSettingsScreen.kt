package dev.hossain.weatheralert.circuit

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dev.hossain.weatheralert.data.PreferencesManager
import dev.hossain.weatheralert.di.AppScope
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import timber.log.Timber

@Parcelize
data class AlertSettingsScreen(
    val requestId: String,
) : Screen {
    data class State(
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
            val snowThreshold: Float,
            val rainThreshold: Float,
        ) : Event()
    }
}

class AlertSettingsPresenter
    @AssistedInject
    constructor(
        @Assisted private val navigator: Navigator,
        @Assisted private val screen: AlertSettingsScreen,
        private val preferencesManager: PreferencesManager,
    ) : Presenter<AlertSettingsScreen.State> {
        @Composable
        override fun present(): AlertSettingsScreen.State {
            val scope = rememberCoroutineScope()
            var updatedSnowThreshold by remember { mutableFloatStateOf(0f) }
            var updatedRainThreshold by remember { mutableFloatStateOf(0f) }

            LaunchedEffect(Unit) {
                updatedSnowThreshold = preferencesManager.currentSnowThreshold()
                updatedRainThreshold = preferencesManager.currentRainThreshold()

                preferencesManager.snowThreshold.collect { updatedSnowThreshold = it }
                preferencesManager.rainThreshold.collect { updatedRainThreshold = it }
            }

            return AlertSettingsScreen.State(updatedSnowThreshold, updatedRainThreshold) { event ->
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
                            preferencesManager.updateRainThreshold(event.rainThreshold)
                            preferencesManager.updateSnowThreshold(event.snowThreshold)
                        }
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
            // Snow Threshold Slider
            Text(text = "Snowfall Threshold: ${"%.1f".format(state.snowThreshold)} cm")
            Slider(
                value = state.snowThreshold,
                onValueChange = {
                    state.eventSink(AlertSettingsScreen.Event.SnowThresholdChanged(it))
                },
                valueRange = 1f..20f,
                steps = 20,
            )

            // Rain Threshold Slider
            Text(text = "Rainfall Threshold: ${"%.1f".format(state.rainThreshold)} mm")
            Slider(
                value = state.rainThreshold,
                onValueChange = {
                    state.eventSink(AlertSettingsScreen.Event.RainThresholdChanged(it))
                },
                valueRange = 1f..20f,
                steps = 20,
            )

            Button(
                onClick = {
                    state.eventSink(
                        AlertSettingsScreen.Event.SaveSettingsClicked(
                            state.snowThreshold,
                            state.rainThreshold,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save Settings")
            }
        }
    }
}

/**
 * Interactive Alert Threshold Adjustments
 *
 *     Purpose: Allow users to adjust alert thresholds in a fun and intuitive way.
 *     Implementation: Use a slider or a rotary dial for thresholds.
 *         Add haptic feedback for user interaction.
 *         Animate the slider's color based on the value range (e.g., blue for low, red for high).
 */
@Composable
fun ThresholdSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    max: Float,
) {
    val color by animateColorAsState(
        if (value < max / 2) Color.Blue else Color.Red,
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "$label: ${value.toInt()} cm", color = color)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..max,
            colors =
                SliderDefaults.colors(
                    thumbColor = color,
                    activeTrackColor = color,
                ),
        )
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun SettingsScreenPreview() {
    AlertSettingsScreen(
        AlertSettingsScreen.State(snowThreshold = 5.0f, rainThreshold = 10.0f) {},
    )
}
