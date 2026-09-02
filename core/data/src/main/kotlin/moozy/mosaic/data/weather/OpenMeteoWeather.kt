package moozy.mosaic.data.weather

import io.ktor.client.call.NoTransformationFoundException
import io.ktor.serialization.ContentConvertException
import java.io.IOException
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.SerializationException
import moozy.mosaic.domain.model.Clock
import moozy.mosaic.domain.model.Weather
import moozy.mosaic.domain.repository.WeatherRepository

/**
 * The current weather for one place, as a stream that keeps itself current.
 *
 * A stream rather than a question, because the weather changes whether or not
 * anybody asks. Making it something to ask for pushed the decision of when to
 * ask onto every caller, and a caller that forgets is a card frozen at whatever
 * it said when the app started.
 *
 * While somebody is watching, this asks again at the source's next step and no
 * sooner: every reading carries the interval Open-Meteo produces them on, so
 * asking earlier cannot return a value that does not exist yet. While nobody is
 * watching, it holds the last reading and makes no requests at all -- an
 * invisible screen is not worth anybody's data.
 *
 * The value is a [Weather] or nothing. A failure is not a state this app shows:
 * the reader came for the articles, and a card that cannot be filled is better
 * absent than apologising. So a failed request leaves whatever was there.
 *
 * Two sources sit behind it and neither is reachable from outside: [api] for
 * what the weather is, [store] for what it last was. Nothing here builds a
 * request or knows an address.
 */
internal class OpenMeteoWeather(
    private val api: OpenMeteoApi,
    private val clock: Clock,
    private val store: WeatherStore,
    scope: CoroutineScope,
) : WeatherRepository {

    /**
     * Why the last request did not produce a reading, if it did not.
     *
     * Nothing is shown to the reader about it -- a missing card is the whole of
     * what a failure means here. Keeping the reason still beats discarding it: a
     * card that never appears is worth being able to find out about.
     */
    internal var lastProblem: String? = null
        private set

    override val current: StateFlow<Weather?> =
        readings().stateIn(scope, SharingStarted.WhileSubscribed(WATCHING_GRACE), null)

    private fun readings(): Flow<Weather?> = flow {
        var held = store.read()
        if (held != null) emit(held.weather)
        while (true) {
            val fetched = fetch()
            if (fetched != null) {
                held = fetched
                store.write(fetched)
                emit(fetched.weather)
            } else if (held == null) {
                // Nothing has ever arrived, so there is nothing to leave alone.
                emit(null)
            }
            delay(untilTheSourceHasMore(held))
        }
    }

    /**
     * How long to wait before asking again.
     *
     * The source's next step when there is a reading. A shorter fixed wait when
     * there is not: the failure might be a moment of no signal, and fifteen
     * minutes of an empty card is a long time to punish somebody for that.
     */
    private fun untilTheSourceHasMore(held: StoredReading?): Long {
        val reading = held ?: return AFTER_A_FAILURE_MILLIS
        val wait = Duration.between(clock.now(), reading.askAgainAt).toMillis()
        return wait.coerceAtLeast(AFTER_A_FAILURE_MILLIS)
    }

    /**
     * One attempt, and what it cost if it failed.
     *
     * The catches stay here rather than moving down with the request, because
     * turning a failure into "no card, try again in a minute" is this class's
     * decision and not something the source could make on its behalf.
     */
    @Suppress("TooGenericExceptionCaught", "RethrowCaughtException")
    private suspend fun fetch(): StoredReading? =
        try {
            val weather = api.forecast()
            lastProblem = null
            StoredReading(weather, clock.now())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unreadable: ContentConvertException) {
            failed("the forecast could not be read", unreadable)
        } catch (unreadable: NoTransformationFoundException) {
            failed("the forecast arrived in a shape this app cannot read", unreadable)
        } catch (unreadable: SerializationException) {
            failed("the forecast could not be read", unreadable)
        } catch (unreachable: IOException) {
            failed("the forecast could not be reached", unreachable)
        } catch (unexpected: Exception) {
            failed("asking for the forecast went wrong in a way nobody expected", unexpected)
        }

    private fun failed(what: String, cause: Throwable): StoredReading? {
        lastProblem = "$what: ${cause.message}"
        return null
    }
}

/** Long enough to survive a rotation, short enough not to outlive the screen. */
private const val WATCHING_GRACE = 5_000L

/** Also the floor on any wait, so a clock that jumps cannot become a busy loop. */
private const val AFTER_A_FAILURE_MILLIS = 60_000L
