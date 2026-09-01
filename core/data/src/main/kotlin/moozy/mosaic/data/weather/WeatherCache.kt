package moozy.mosaic.data.weather

import java.time.Instant
import moozy.mosaic.domain.model.Weather

/**
 * A reading, and the moment the source will next have something it does not
 * already say. Worked out once, when the reading arrives, from the reading's
 * own stamp and the source's own step.
 */
internal data class CachedWeather(
    val weather: Weather,
    val askAgainAt: Instant,
)

/**
 * Somewhere to keep the last reading.
 *
 * Separate from the repository because the two answer different questions. When
 * a reading stops being worth reusing is the source's answer; whether it is
 * still around to be reused after the system reclaims the app is this one's.
 * Holding it in memory answered the second as "never", which is not a policy,
 * just a consequence.
 */
internal interface WeatherCache {
    suspend fun read(): CachedWeather?
    suspend fun write(weather: CachedWeather)
}
