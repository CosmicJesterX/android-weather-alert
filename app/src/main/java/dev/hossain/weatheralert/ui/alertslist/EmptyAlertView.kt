package dev.hossain.weatheralert.ui.alertslist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hossain.weatheralert.R
import dev.hossain.weatheralert.ui.theme.WeatherAlertAppTheme

@Composable
fun EmptyAlertState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
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
        Column(modifier = Modifier.padding(vertical = 120.dp)) {
            Text(
                text = "Powered by,",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.tertiary),
                fontStyle = FontStyle.Italic,
            )
            Image(
                painter = painterResource(id = R.drawable.openweather_logo),
                contentDescription = "Open Weather Map Logo",
                modifier =
                    Modifier
                        // Original: width="176dp" height="79dp"
                        .padding(top = 16.dp, start = 56.dp)
                        // Reduces intensity by a bit
                        .alpha(0.9f),
            )
            Image(
                painter = painterResource(id = R.drawable.tomorrow_io_logo),
                modifier =
                    Modifier
                        .padding(top = 24.dp, start = 56.dp)
                        // Original: width="134dp" height="25dp"
                        // Increase by 30% to match the OpenWeather logo
                        .size(174.dp, 32.dp)
                        // Reduces intensity by a bit
                        .alpha(0.9f),
                contentDescription = "Tomorrow.io Logo",
            )
        }
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun EmptyStatePreview() {
    WeatherAlertAppTheme {
        EmptyAlertState(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        )
    }
}
