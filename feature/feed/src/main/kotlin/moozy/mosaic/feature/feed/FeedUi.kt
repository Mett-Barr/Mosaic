package moozy.mosaic.feature.feed

import androidx.compose.runtime.Immutable
import moozy.mosaic.core.ui.readableTime
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.FeedFailure
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
