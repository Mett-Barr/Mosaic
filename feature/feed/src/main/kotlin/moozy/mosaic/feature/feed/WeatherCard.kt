package moozy.mosaic.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = weather.sky.icon(),
                // No description, and that is the point rather than an omission:
                // the line beside it opens with the same word. A description here
                // would have a screen reader say "Cloudy, Cloudy · 31° / 24°".
                // The columns below are the opposite case -- nothing there says
                // it -- so that is where the description goes.
                contentDescription = null,
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(HERO_ICON),
            )
            Text(
                text = weather.conditions,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
        }
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
        Icon(
            imageVector = day.sky.icon(),
            // The only thing in this column that says what the sky is doing --
            // the label is a weekday and the number is a temperature. Without a
            // description a screen reader would read "Wed, 29°" and leave out
            // the half of the column a reader is looking at it for.
            contentDescription = day.sky.readable(),
            tint = scheme.onPrimaryContainer,
            modifier = Modifier.size(DAY_ICON),
        )
        Text(
            text = day.temperature,
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onPrimaryContainer,
        )
    }
}

/** Beside a line of body text, not above a number: smaller than the day icons. */
private val HERO_ICON = 20.dp

/** Between the weekday and the temperature, and the widest thing in the column. */
private val DAY_ICON = 24.dp

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
    sky = Sky.CLOUDY,
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

/**
 * Every sky the domain can name, with the picture this screen gives it.
 *
 * All eight rather than the ones a fixture happens to contain. The mapping is
 * exhaustive by the compiler, but the compiler has no opinion about two skies
 * that draw the same thing, or about the one that has to draw *something* --
 * and the only way to find either out is to see them side by side.
 */
@Preview
@Composable
private fun EverySkyPreview() {
    MosaicTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Sky.entries.forEach { sky ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = sky.icon(),
                            contentDescription = sky.readable(),
                            modifier = Modifier.size(DAY_ICON),
                        )
                        Text(sky.readable(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
