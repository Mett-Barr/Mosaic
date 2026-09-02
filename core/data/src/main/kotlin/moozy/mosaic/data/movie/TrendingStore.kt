package moozy.mosaic.data.movie

import moozy.mosaic.domain.model.TrendingMovies

/**
 * Where the last day's list is kept.
 *
 * Separate from the repository because the two answer different questions. When a
 * list stops being worth reusing is the source's answer -- tomorrow -- and
 * whether it is still around to be reused after the app was closed is this one's.
 *
 * Without it "once a day" would quietly mean "once a launch", which is the
 * articles' rule wearing the films' name: a reader who opens this app five times
 * before lunch would pay for five copies of the same list.
 *
 * No `fetchedAt` beside it, unlike the weather's [moozy.mosaic.data.weather.StoredReading].
 * The weather has to work out its next step from when it asked; a day already
 * knows which day it is.
 */
internal interface TrendingStore {
    suspend fun read(): TrendingMovies?
    suspend fun write(trending: TrendingMovies)
}
