package moozy.mosaic.feature.feed

import androidx.compose.runtime.Immutable
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
