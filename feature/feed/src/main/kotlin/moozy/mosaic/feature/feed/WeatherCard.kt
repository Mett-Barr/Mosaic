package moozy.mosaic.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moozy.mosaic.domain.model.Sky
import moozy.mosaic.domain.model.Weather

/**
 * The other kind of cell.
 *
 * Deliberately unlike an article: no image, no summary, one number the size of a
 * headline, and the container's own colour rather than the surface every article
 * sits on. A reader scrolling past should not have to read it to know it is not
 * a story.
 */
@Composable
internal fun WeatherCard(weather: Weather, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(weather.place, style = MaterialTheme.typography.titleMedium)
            Text("${weather.temperature}°", style = MaterialTheme.typography.displayMedium)
            Text(
                "${weather.sky.readable()} · ${weather.high}° / ${weather.low}°",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun Sky.readable(): String = when (this) {
    Sky.CLEAR -> "Clear"
    Sky.CLOUDY -> "Cloudy"
    Sky.FOG -> "Fog"
    Sky.DRIZZLE -> "Drizzle"
    Sky.RAIN -> "Rain"
    Sky.SNOW -> "Snow"
    Sky.THUNDERSTORM -> "Thunderstorms"
    Sky.UNKNOWN -> "Weather"
}
