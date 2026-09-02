package moozy.mosaic.domain.repository

import kotlinx.coroutines.flow.StateFlow
import moozy.mosaic.domain.model.Movie

/**
 * What is trending today, as it changes.
 *
 * A stream and not a question, for the reason [WeatherRepository] is one: the
 * answer changes whether or not anybody asks, and making it something to ask for
 * hands every caller the job of deciding when. What differs is how often it
 * changes -- the source recomputes this once a day, so the stream behind this
 * makes at most one request a day and the screen above it never has to know that.
 *
 * Nothing here says when to ask. That belongs to whatever knows what the source's
 * unit of change is, and the address the source is asked at is the only thing
 * that says.
 *
 * The value is a list, and empty is an ordinary answer rather than a failure. It
 * is also the answer when this app was built without a key to ask with: the
 * reader came for the articles, and a strip that cannot be filled is better
 * absent than apologising -- the same rule the weather card follows.
 */
interface MovieRepository {

    val trending: StateFlow<List<Movie>>
}
