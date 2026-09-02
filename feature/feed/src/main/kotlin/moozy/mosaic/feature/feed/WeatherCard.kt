package moozy.mosaic.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import moozy.mosaic.core.ui.MosaicTheme
import moozy.mosaic.domain.model.Sky

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
 * is a caption to it, including the strip: three days is enough to say which way
 * the week is going, which is a different question from the one the big number
 * answers.
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
        // Only when there is a forecast to draw. An empty strip would still take
        // its own space, and a card holding a gap open for something absent reads
        // as broken rather than as short.
        if (weather.days.isNotEmpty()) DaysAhead(weather.days)
    }
}

/**
 * The days after this one, a column each.
 *
 * Spread across the card rather than spaced by a fixed gap, so three days and
 * two days both look deliberate -- the source decides how many there are, and a
 * row that only lines up at one count would be a layout with an opinion about
 * the weather.
 */
@Composable
private fun DaysAhead(days: ImmutableList<DayHeadline>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        days.forEach { day -> DayAhead(day) }
    }
}

/** One day: what it is called, and how warm it is expected to get. */
@Composable
private fun DayAhead(day: DayHeadline, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = day.day,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onPrimaryContainer,
        )
        Text(
            text = day.temperature,
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onPrimaryContainer,
        )
    }
}

/**
 * Three days that disagree with each other.
 *
 * A strip whose columns all said the same thing would look right whether or not
 * each day was read out of its own place in the response.
 */
private val PreviewDays = persistentListOf(
    DayHeadline(day = "Tue", temperature = "32°", sky = Sky.CLOUDY),
    DayHeadline(day = "Wed", temperature = "29°", sky = Sky.RAIN),
    DayHeadline(day = "Thu", temperature = "33°", sky = Sky.CLEAR),
)

private val PreviewReading = WeatherHeadline(
    place = "Taipei",
    temperature = "28°",
    conditions = "Cloudy · 31° / 24°",
    days = PreviewDays,
)

/**
 * The whole card in both themes.
 *
 * This is the one surface in the app with a gradient on it, and the two schemes
 * put `primaryContainer` and `secondaryContainer` in very different places --
 * so it is also the one place where a colour chosen for the light theme can
 * quietly stop being readable in the dark one.
 */
@PreviewLightDark
@Composable
private fun WeatherCardPreviews() {
    MosaicTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            WeatherCard(PreviewReading, Modifier.padding(16.dp))
        }
    }
}

/**
 * The same card with no forecast behind it.
 *
 * Worth its own render because it is what a reader sees when the source answers
 * with today and nothing else, and what one sees on the first launch after this
 * app is updated -- the reading on disk was written before there was a strip.
 * The card has to end cleanly under the conditions line, not leave a gap.
 */
@Preview
@Composable
private fun WeatherCardWithoutDaysPreview() {
    MosaicTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            WeatherCard(PreviewReading.copy(days = persistentListOf()), Modifier.padding(16.dp))
        }
    }
}
