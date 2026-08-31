package moozy.mosaic.domain.model

import java.time.Instant

/**
 * The state of the sky, in the words a person would use.
 *
 * The source gives a number from the WMO's table, which has more distinctions in
 * it than anybody reading a feed wants: light, moderate and dense freezing
 * drizzle are three codes and one sentence. [UNKNOWN] exists because the table
 * can grow and a wrong name is worse than no name.
 */
enum class Sky {
    CLEAR,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    SNOW,
    THUNDERSTORM,
    UNKNOWN,
}

/**
 * One weather reading, for one place.
 *
 * Nothing about it resembles an article, which is the point of it being in this
 * app: a feed of one shape is a list, and this is the thing that makes it a feed.
 *
 * [measuredAt] is an instant rather than the local time the source sends, because
 * how old a reading is has to be answerable by subtraction.
 */
data class Weather(
    val place: String,
    val temperature: Int,
    val high: Int,
    val low: Int,
    val sky: Sky,
    val measuredAt: Instant,
) {
    init {
        require(place.isNotBlank()) { "A reading with no place is not a reading." }
        require(high >= low) { "A day cannot be colder at its warmest than at its coldest." }
    }
}

/**
 * What asking for the weather produced.
 *
 * Failing to get the weather is not the same kind of event as failing to get the
 * feed: the feed is why the reader opened the app, and the weather is a card at
 * the top of it. So the failure travels, but nothing above here is obliged to
 * turn it into a screen.
 */
sealed interface WeatherResult {

    data class Loaded(val weather: Weather) : WeatherResult

    data class Failed(val reason: FeedFailure) : WeatherResult
}
