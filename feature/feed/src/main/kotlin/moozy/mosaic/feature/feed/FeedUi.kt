package moozy.mosaic.feature.feed

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import moozy.mosaic.core.ui.readableTime
import moozy.mosaic.core.ui.readableWeekday
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.FeedFailure
import moozy.mosaic.domain.model.ForecastDay
import moozy.mosaic.domain.model.Sky
import moozy.mosaic.domain.model.Weather

/**
 * What the feed shows, in the words it shows them in.
 *
 * The layer between the domain and the screen. An [ArticleItem] is what the app
 * knows; an [ArticleRow] is what a reader sees -- and the difference is every
 * decision about how the first becomes the second: which fields appear, in what
 * order, joined by what.
 *
 * Those decisions used to live inside composables, where nothing this project can
 * run was able to check them. Here they are ordinary functions returning ordinary
 * strings, so the ViewModel's own tests assert on the words themselves.
 *
 * [id] stays the domain's type rather than becoming a String: it is not shown, it
 * is what the screen hands back when the reader taps.
 */
@Immutable
data class ArticleRow(
    val id: ArticleId,
    val title: String,
    val summary: String,
    /** Source and time as one line, because that is one line on the card. */
    val attribution: String,
    val imageUrl: String?,
)

/** The other kind of cell, as words. */
@Immutable
data class WeatherHeadline(
    val place: String,
    val temperature: String,
    val conditions: String,
    /**
     * The sky on its own, so the card can draw it.
     *
     * Kept beside [conditions] rather than instead of it: the words in that line
     * are a sentence a reader reads, and this is what a picture is chosen by.
     */
    val sky: Sky,
    /**
     * The days the card draws a strip from, today first.
     *
     * Empty when the reading carries no forecast, which is a card without a
     * strip rather than no card: the three lines above this one are made of
     * today and do not need it.
     */
    val days: ImmutableList<DayHeadline>,
)

/** One column of the strip: which day, and how warm it is expected to get. */
@Immutable
data class DayHeadline(
    /** The reader's own weekday name, in the language the rest of the card is in. */
    val day: String,
    /**
     * The day's high, and only the high. Two numbers in a column this narrow
     * would be a table, and the strip is here to say which way the week is
     * going rather than to be read off precisely.
     */
    val temperature: String,
    val sky: Sky,
)

internal fun ArticleItem.row() = ArticleRow(
    id = id,
    title = title,
    summary = summary,
    attribution = "$source · ${readableTime(publishedAt)}",
    imageUrl = imageUrl,
)

internal fun Weather.headline() = WeatherHeadline(
    place = place,
    temperature = "$temperature°",
    conditions = "${sky.readable()} · $high° / $low°",
    sky = sky,
    days = days.map { it.headline() }.toImmutableList(),
)

private fun ForecastDay.headline() = DayHeadline(
    day = readableWeekday(date),
    temperature = "$high°",
    sky = sky,
)

/**
 * What to tell a reader about a failure.
 *
 * The failure's own detail is deliberately left out: it is an exception message,
 * written for whoever is debugging this.
 */
internal fun FeedFailure.hint(): String = when (this) {
    is FeedFailure.Offline -> "There is no connection right now."
    is FeedFailure.Timeout -> "The feed took too long to answer."
    is FeedFailure.Missing -> "The feed is not where it used to be."
    is FeedFailure.Server -> "The feed is having trouble (error $status)."
    is FeedFailure.Unreadable -> "The feed sent something this app could not read."
    is FeedFailure.Unexpected -> "Something unexpected happened."
}

internal fun Sky.readable(): String = when (this) {
    Sky.CLEAR -> "Clear"
    Sky.CLOUDY -> "Cloudy"
    Sky.FOG -> "Fog"
    Sky.DRIZZLE -> "Drizzle"
    Sky.RAIN -> "Rain"
    Sky.SNOW -> "Snow"
    Sky.THUNDERSTORM -> "Thunderstorms"
    Sky.UNKNOWN -> "Weather"
}

/**
 * The same eight distinctions, as a picture.
 *
 * Beside [readable] and not in the domain, for the reason the words are here:
 * `Sky.CLOUDY` is a fact about the weather, and that a cloud stands for it is a
 * decision about this screen. The domain would have to know about a drawing
 * library to hold the second one.
 *
 * All eight have one, [Sky.UNKNOWN] included. A column that drew nothing where
 * its neighbours draw something reads as a card that failed rather than as a
 * sky this app has no name for -- and a question mark says the second, which is
 * the true one.
 */
internal fun Sky.icon(): ImageVector = when (this) {
    Sky.CLEAR -> Icons.Outlined.WbSunny
    Sky.CLOUDY -> Icons.Outlined.Cloud
    // There is no fog in the icon set. Blur is the one that reads as air you
    // cannot see through, which is the whole of what this has to say.
    Sky.FOG -> Icons.Outlined.BlurOn
    // Scattered specks beside one falling drop. Drizzle and rain are kept apart
    // in the domain because one changes whether you take a coat, and these two
    // pictures are the smallest pair that keeps saying so.
    Sky.DRIZZLE -> Icons.Outlined.Grain
    Sky.RAIN -> Icons.Outlined.WaterDrop
    Sky.SNOW -> Icons.Outlined.AcUnit
    Sky.THUNDERSTORM -> Icons.Outlined.Thunderstorm
    Sky.UNKNOWN -> Icons.Outlined.QuestionMark
}
