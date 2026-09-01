package moozy.mosaic.data.weather

import java.io.File
import java.io.IOException
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import moozy.mosaic.domain.model.Sky
import moozy.mosaic.domain.model.Weather

/**
 * The last reading, written down.
 *
 * One small file read whole and written whole, the same shape as the article
 * cache and the saved list. Losing it costs one request, so a file that will not
 * parse reads as nothing at all and the next reading writes over it.
 */
internal class FileWeatherCache(
    private val file: File,
    private val io: CoroutineDispatcher,
) : WeatherCache {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Why the reading could not be read back, if it could not be.
     *
     * Nothing is shown to the reader about it -- a lost reading costs one
     * request. Keeping the reason still beats discarding it: a cache that never
     * loads makes every launch pay, and that is worth being able to find out.
     */
    internal var lastProblem: String? = null
        private set

    override suspend fun read(): CachedWeather? = withContext(io) {
        try {
            file.takeIf { it.exists() }
                ?.readText()
                ?.takeIf { it.isNotBlank() }
                ?.let { json.decodeFromString(StoredWeather.serializer(), it) }
                ?.toCached()
        } catch (unreadable: SerializationException) {
            // First, because SerializationException *is* an
            // IllegalArgumentException: catching the general one above this would
            // leave this branch unreachable and its message unwritten.
            lastProblem = "the stored reading could not be read: ${unreadable.message}"
            file.delete()
            null
        } catch (refused: IllegalArgumentException) {
            // Valid JSON whose values the domain will not hold: a blank id, a day
            // colder at its warmest than at its coldest. Parsing succeeded, so
            // neither catch around it sees these.
            lastProblem = "the stored reading held values this app cannot use: ${refused.message}"
            file.delete()
            null
        } catch (unreadable: DateTimeException) {
            // A stored time that will not parse. Not an IllegalArgumentException
            // -- which is the whole reason this branch had to be written: without
            // it the throw left read() entirely and took the caller with it.
            lastProblem = "the stored reading had a time that is not one: ${unreadable.message}"
            file.delete()
            null
        } catch (unreachable: IOException) {
            lastProblem = "the stored reading could not be opened: ${unreachable.message}"
            null
        }
    }

    /**
     * Best effort, like the article cache. A reading that arrived must reach the
     * reader whether or not it can also be written down.
     */
    override suspend fun write(weather: CachedWeather) {
        withContext(io) {
            try {
                file.parentFile?.mkdirs()
                // Through a second file and a rename: writeText empties the
                // destination before it fills it, and a process killed in
                // between would leave a file that is neither the old reading nor
                // the new one. A rename cannot be half-done.
                val writing = File(file.parentFile, file.name + ".writing")
                writing.writeText(json.encodeToString(StoredWeather.serializer(), weather.stored()))
                if (!writing.renameTo(file)) {
                    file.delete()
                    writing.renameTo(file)
                }
            } catch (unwritable: IOException) {
                lastProblem = "the reading could not be written down: ${unwritable.message}"
            }
        }
    }
}

@Serializable
private data class StoredWeather(
    val place: String,
    val temperature: Int,
    val high: Int,
    val low: Int,
    val sky: String,
    @SerialName("measured_at") val measuredAt: String,
    @SerialName("steps_every_seconds") val stepsEverySeconds: Long,
    @SerialName("ask_again_at") val askAgainAt: String,
)

private fun CachedWeather.stored() = StoredWeather(
    place = weather.place,
    temperature = weather.temperature,
    high = weather.high,
    low = weather.low,
    sky = weather.sky.name,
    measuredAt = weather.measuredAt.toString(),
    stepsEverySeconds = weather.stepsEvery.seconds,
    askAgainAt = askAgainAt.toString(),
)

private fun StoredWeather.toCached() = CachedWeather(
    weather = Weather(
        place = place,
        temperature = temperature,
        high = high,
        low = low,
        sky = Sky.valueOf(sky),
        measuredAt = Instant.parse(measuredAt),
        stepsEvery = Duration.ofSeconds(stepsEverySeconds),
    ),
    askAgainAt = Instant.parse(askAgainAt),
)
