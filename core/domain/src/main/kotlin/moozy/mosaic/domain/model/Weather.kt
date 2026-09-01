package moozy.mosaic.domain.model

import java.time.Duration
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
 *
 * [stepsEvery] is how often the source produces a new one. It comes from the
 * source rather than from this app, which is the whole point: asking more often
 * than a new value exists cannot produce a new value.
 */
data class Weather(
    val place: String,
    val temperature: Int,
    val high: Int,
    val low: Int,
    val sky: Sky,
    val measuredAt: Instant,
    val stepsEvery: Duration,
) {
    init {
        require(place.isNotBlank()) { "A reading with no place is not a reading." }
        require(high >= low) { "A day cannot be colder at its warmest than at its coldest." }
        require(!stepsEvery.isNegative && !stepsEvery.isZero) {
            "A source that produces a new reading every no time at all cannot be asked politely."
        }
    }

    /**
     * When the source will next have something this does not already say.
     *
     * The first step strictly after [fetchedAt], counted from [measuredAt]. Not
     * simply [measuredAt] plus one step: the source publishes behind its own
     * grid -- asked at 12:52, it answered with the 12:30 reading -- so that
     * form lands in the past, is stale on arrival, and asks again for the same
     * value forever. Counting past [fetchedAt] absorbs whatever the lag is
     * instead of arguing with it.
     */
    fun askAgainAfter(fetchedAt: Instant): Instant {
        val step = stepsEvery.toMillis()
        val elapsed = Duration.between(measuredAt, fetchedAt).toMillis()
        // At least one step, so a reading stamped in the future -- a clock that
        // ran ahead -- is still worth a step rather than an immediate re-ask.
        val steps = if (elapsed < 0) 1 else elapsed / step + 1
        return measuredAt.plusMillis(step * steps)
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
