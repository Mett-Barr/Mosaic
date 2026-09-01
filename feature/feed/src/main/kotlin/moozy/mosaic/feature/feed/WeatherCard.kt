package moozy.mosaic.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The other kind of cell.
 *
 * Deliberately unlike an article: no image, no summary, one number the size of a
 * headline, and a green gradient rather than the near-white every story sits on.
 * A reader scrolling past should not have to read it to know it is not a story --
 * and on this screen they do not have to, because nothing else in the app is
 * green edge to edge.
 *
 * The temperature is centred and enormous on purpose. Everything else on the card
 * is a caption to it.
 */
@Composable
internal fun WeatherCard(weather: WeatherHeadline, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.linearGradient(
                    colors = listOf(scheme.primaryContainer, scheme.secondaryContainer),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                )
            )
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = weather.place,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onPrimaryContainer,
        )
        Text(
            text = weather.temperature,
            style = MaterialTheme.typography.displayLarge,
            color = scheme.onPrimaryContainer,
        )
        Text(
            text = weather.conditions,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
        )
    }
}
