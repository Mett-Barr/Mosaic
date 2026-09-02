package moozy.mosaic.data.movie

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import moozy.mosaic.domain.model.Movie
import moozy.mosaic.domain.model.MovieId

/**
 * Unknown keys are ignored: a trending row carries a backdrop, a popularity
 * score, genre ids and the original title in its own alphabet, none of which this
 * app shows -- and the day the API grows one more field should not be the day the
 * strip goes empty.
 */
internal val TmdbJson = Json { ignoreUnknownKeys = true }

/**
 * A day of trending films.
 *
 * [results] has no default for the same reason the article page's list has none:
 * a response that does not carry the list at all is a broken response, and
 * letting it decode into an empty day would write "nothing is trending" down as
 * this day's answer and stop asking until tomorrow.
 *
 * The rows stay undecoded so that one bad row stays one bad row. Decoding them as
 * `List<MovieDto>` would let a single wrong type anywhere in twenty rows take the
 * other nineteen with it.
 */
@Serializable
internal data class TrendingPageDto(val results: List<JsonElement>)

/**
 * [id] and [title] have no defaults -- a row without either is not a film this
 * app can put on screen. The rest do: a poster that has not been uploaded yet is
 * ordinary, and a film released today has no votes.
 */
@Serializable
internal data class MovieDto(
    val id: Int,
    val title: String,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
)

internal fun TrendingPageDto.toMovies(): List<Movie> = results.mapNotNull { it.toMovieOrNull() }

/**
 * Null for a row that cannot become a film.
 *
 * No reason is carried out of here, unlike the article mapper's dropped rows.
 * There is nowhere for one to go: the strip has no failure state to put it in --
 * it is simply shorter -- and inventing a channel for a message nothing reads
 * would be the speculation the deferred list already argues against.
 */
@Suppress("SwallowedException")
private fun JsonElement.toMovieOrNull(): Movie? =
    try {
        TmdbJson.decodeFromJsonElement(MovieDto.serializer(), this).toMovie()
    } catch (unusable: IllegalArgumentException) {
        // Both failures land here and that is deliberate: SerializationException
        // *is* an IllegalArgumentException, so a row shaped wrong and a row the
        // domain refuses -- a blank title, a score off the scale -- are one
        // branch. The domain's rules are not restated above, which is what makes
        // an invariant added there later show up as a dropped row rather than as
        // a crash in the middle of a day's list.
        null
    }

private fun MovieDto.toMovie() = Movie(
    id = MovieId(id),
    title = title,
    // vote_average is 0 for a film nobody has rated, and nought out of ten is a
    // sentence about the film. The count is the only field that tells the two
    // apart, which is the whole reason it is asked for.
    rating = voteAverage.takeIf { voteCount > 0 },
    posterUrl = posterPath?.takeIf { it.isNotBlank() }?.let { POSTER_BASE + it },
)

/**
 * Where a poster path becomes an address.
 *
 * TMDB documents this properly discovered from `/3/configuration`, which returns
 * the base and the list of widths the CDN has. It is hardcoded here instead, and
 * that is a decision rather than an oversight (`DECISIONS.md` 40): discovering it
 * costs a second request before the first poster can be drawn, on every launch
 * that has no configuration cached -- to learn a string that has not changed in
 * the life of the API. `w342` is the size a poster this app draws at ~116dp needs
 * on a 3x display; the next one down would be soft on a phone.
 */
private const val POSTER_BASE = "https://image.tmdb.org/t/p/w342"
