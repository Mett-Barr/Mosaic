package moozy.mosaic.data.weather

import java.time.Instant
import moozy.mosaic.domain.model.Weather

/** A reading, and the moment it was asked for -- which is what decides its age. */
internal data class CachedWeather(
    val weather: Weather,
    val askedAt: Instant,
)

/**
 * Somewhere to keep the last reading.
 *
 * Separate from the repository because the two answer different questions. The
 * freshness policy decides how long a reading is worth showing; this decides
 * whether the reading is still around to be shown after the system reclaims the
 * app. Answering the second by holding the reading in memory answers it as
 * "never", which is not a policy, just a consequence.
 */
internal interface WeatherCache {
    suspend fun read(): CachedWeather?
    suspend fun write(weather: CachedWeather)
}
