package moozy.mosaic.data.weather

import java.time.Instant
import moozy.mosaic.domain.model.Weather

/**
 * A reading, and the moment it was fetched.
 *
 * The moment is kept rather than the conclusion drawn from it. When the source
 * will next have something new is worked out from this and the reading's own
 * step, so a change to that rule reaches readings already on disk instead of
 * waiting for them to be replaced.
 *
 * It is here rather than on [Weather] because it is not a fact about the sky.
 * Two readings describing the same weather should be equal, and they would not
 * be if each carried the moment somebody happened to ask.
 */
internal data class StoredReading(
    val weather: Weather,
    val fetchedAt: Instant,
) {
    /** When the source will next have a reading this one does not already give. */
    val askAgainAt: Instant get() = weather.askAgainAfter(fetchedAt)
}

/**
 * Where the last reading is kept.
 *
 * Separate from the repository because the two answer different questions. When
 * a reading stops being worth reusing is the source's answer; whether it is
 * still around to be reused after the system reclaims the app is this one's.
 * Holding it in a field answered the second as "never", which is not a policy,
 * just a consequence.
 */
internal interface WeatherStore {
    suspend fun read(): StoredReading?
    suspend fun write(reading: StoredReading)
}
