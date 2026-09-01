package moozy.mosaic.data.weather

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.roundToInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moozy.mosaic.domain.model.Sky
import moozy.mosaic.domain.model.Weather

/**
 * Unknown keys are ignored: the response carries units, elevation and a
 * generation time this app has no use for, and a forecast growing a field should
 * not be the thing that empties the card.
 */
internal val OpenMeteoJson = Json { ignoreUnknownKeys = true }

/**
 * Every field here is required. Unlike an article -- where a missing summary
 * still leaves something to read -- a reading without a temperature is not a
 * reading, and there is no half of it worth showing.
 */
@Serializable
internal data class ForecastDto(
    val current: CurrentDto,
    val daily: DailyDto,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int,
)

@Serializable
internal data class CurrentDto(
    val time: String,
    /** Seconds between one reading and the next, as the source reports it. */
    val interval: Int,
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("weather_code") val weatherCode: Int,
)

@Serializable
internal data class DailyDto(
    @SerialName("temperature_2m_max") val high: List<Double>,
    @SerialName("temperature_2m_min") val low: List<Double>,
)

/**
 * [place] is passed in rather than read from the response. The source answers
 * with a time zone, not a city, and "Asia/Taipei" is not what anybody calls where
 * they live.
 */
internal fun ForecastDto.toWeather(place: String): Weather = Weather(
    place = place,
    temperature = current.temperature.roundToInt(),
    high = daily.high.first().roundToInt(),
    low = daily.low.first().roundToInt(),
    sky = current.weatherCode.toSky(),
    // The source reports in the location's own time and tells us its offset
    // separately; neither is an instant until they are put together.
    measuredAt = LocalDateTime.parse(current.time)
        .toInstant(ZoneOffset.ofTotalSeconds(utcOffsetSeconds)),
    // How often the source produces a new one, as the source reports it.
    // This is what makes the freshness policy a fact rather than a guess.
    stepsEvery = Duration.ofSeconds(current.interval.toLong()),
)

/**
 * The WMO's table, collapsed into the distinctions a reader acts on. Drizzle and
 * rain stay apart because one changes whether you take a coat; the three
 * intensities of each do not.
 */
@Suppress("MagicNumber")
private fun Int.toSky(): Sky = when (this) {
    0 -> Sky.CLEAR
    1, 2, 3 -> Sky.CLOUDY
    45, 48 -> Sky.FOG
    51, 53, 55, 56, 57 -> Sky.DRIZZLE
    61, 63, 65, 66, 67, 80, 81, 82 -> Sky.RAIN
    71, 73, 75, 77, 85, 86 -> Sky.SNOW
    95, 96, 99 -> Sky.THUNDERSTORM
    else -> Sky.UNKNOWN
}
