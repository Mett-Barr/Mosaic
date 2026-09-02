package moozy.mosaic.domain.model

import java.time.LocalDate

/**
 * The identity of a film, held apart from the [Int] it wraps for the reason
 * [ArticleId] is held apart from its [String]: this feed carries three kinds of
 * thing now, and two of them have ids.
 *
 * An [Int] rather than a [String], because that is what the source gives. Turning
 * it into text here would be inventing a format, and the only code that would
 * ever have to parse it back is this app's.
 */
@JvmInline
value class MovieId(val value: Int) {
    init {
        require(value > 0) { "A film id from the source is a positive number: $value" }
    }
}

/**
 * One film in the strip.
 *
 * Three fields reach a reader and only two of them are in the response: the
 * poster arrives as a path, and the address it hangs off is built where the
 * response is read. By the time it is here it is an address, because "half a URL"
 * is not a thing the rest of the app should have to know how to finish.
 *
 * [rating] is nullable because the source's own zero is ambiguous: a film nobody
 * has voted on and a film everybody hated send the same number, and a film
 * trending on the day it opens is exactly the row that happens to. Nothing is a
 * truer answer than nought out of ten.
 *
 * It is a [Double] and not a rounded number for the reason [ArticleItem] holds an
 * [java.time.Instant] rather than "2 hours ago": how many decimals a reader sees
 * is a decision about a screen.
 */
data class Movie(
    val id: MovieId,
    val title: String,
    val rating: Double?,
    val posterUrl: String?,
) {
    init {
        require(title.isNotBlank()) { "A film cannot have a blank title: $id" }
        if (rating != null) {
            require(rating in WORST..BEST) {
                "A score outside $WORST..$BEST is not one this source gives: $rating"
            }
        }
    }
}

/** The bottom and the top of the scale the source rates on. */
private const val WORST = 0.0
private const val BEST = 10.0

/**
 * What was trending, and the day it was trending on.
 *
 * The day is the whole freshness policy. Unlike a weather reading, a trending
 * list carries no timestamp and no interval -- but the address it comes from is
 * `/trending/movie/day`, so the unit the source recomputes on is named, just in
 * the request rather than in the response. Holding the day beside the films is
 * what lets the question "is this still worth showing" be asked without a
 * network call.
 *
 * [forDay] is a [LocalDate] and not an instant because a day is what the source
 * deals in. Which zone's day it is belongs to whoever reads the clock; nothing
 * here can know it.
 */
data class TrendingMovies(
    val movies: List<Movie>,
    val forDay: LocalDate,
) {

    /**
     * Whether asking again could produce anything this does not already have.
     *
     * Not `day == forDay`. A clock that goes backwards -- a phone flown west over
     * a date line, or one corrected after running ahead -- would otherwise make a
     * list computed for a *later* day look stale, and re-asking cannot improve on
     * a day that has not happened yet. This is the same shape as the weather's
     * refusal to treat a reading stamped in the future as overdue.
     */
    fun stillCurrentOn(day: LocalDate): Boolean = !day.isAfter(forDay)
}
