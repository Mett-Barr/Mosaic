package moozy.mosaic.data.movie

import io.ktor.client.call.NoTransformationFoundException
import io.ktor.serialization.ContentConvertException
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
import moozy.mosaic.domain.model.Movie
import moozy.mosaic.domain.model.TrendingMovies
import moozy.mosaic.domain.repository.MovieRepository

/**
 * What is trending today, as a stream that keeps itself current -- on a day, not
 * on a timer.
 *
 * This is the third freshness rule in the app and it had to be a third one. The
 * weather is asked again on the grid its own response names, which films have no
 * equivalent of: nothing in a TMDB response says when the list will change. The
 * articles are asked again whenever a person asks, which does not transfer
 * either, because a pull means "show me newer stories" and cannot make TMDB
 * compute a new day. What is left is the one thing the source does state -- the
 * address is `/trending/movie/day` -- so the unit is a day, and this asks once
 * per day the reader has and not once more.
 *
 * A pull therefore does not reach here at all, and neither does a relaunch: the
 * day that arrived is written down, so the count survives the process. While
 * nobody is watching, nothing is asked. That leaves exactly one thing that makes
 * a request worth making, which is the day turning over.
 *
 * The value is a list, and a failure is not a state this app shows: the reader
 * came for the articles, so a failed request leaves whatever was there and the
 * strip is simply shorter or absent. **Which is exactly why the minute a refusal
 * buys has to outlive the watcher who paid for it** ([refusedAt]): nothing on
 * screen would ever show that the same request is being made and refused on
 * every visit to the feed.
 *
 * [zone] is handed in rather than read here for the same reason [clock] is. Whose
 * midnight the day turns at is a question about the device, and a class that
 * answered it for itself could only ever be tested at whatever midnight the
 * machine running the tests happens to have.
 */
internal class TmdbTrending(
    private val api: TmdbApi,
    private val clock: Clock,
    private val zone: ZoneId,
    private val store: TrendingStore,
    scope: CoroutineScope,
) : MovieRepository {

    /**
     * Why the last request did not produce a list, if it did not.
     *
     * Nothing is shown to the reader about it -- a missing strip is the whole of
     * what a failure means here. Keeping the reason still beats discarding it: a
     * strip that never appears is worth being able to find out about.
     */
    internal var lastProblem: String? = null
        private set

    /**
     * When the last attempt failed, if the last one did.
     *
     * **On the repository and not in the flow, which is the whole point of it.**
     * The wait after a failure used to be a `delay` in the flow's body -- and
     * the flow is the thing [SharingStarted.WhileSubscribed] tears down five
     * seconds after the last watcher leaves, then starts again from the top for
     * the next one. So the wait was thrown away by exactly the event it exists
     * to survive: Reading to Saved and back, or out of the app and back, asked
     * again immediately. A token that has been revoked is a 401 on every visit
     * to the feed, and nothing ever says so, because a failure here means a
     * shorter strip and nothing else.
     *
     * The day that arrived is written to a file and the day that did not is
     * kept here, and the asymmetry is deliberate. What a file buys is surviving
     * the process, and a fresh process is a reader who has been away long
     * enough for the system to reclaim the app -- which is far longer than a
     * minute, so writing this down would only ever be read back after it had
     * expired. `DECISIONS.md` 41 draws the same line for the articles the feed
     * showed: a cold start is worth a fresh request.
     *
     * `@Volatile` because the boundary it exists to survive is also a thread
     * boundary: the flow is torn down and restarted on `Dispatchers.Default`,
     * so the write and the read that follows it need not land on the same
     * thread. The dispatch between them already establishes happens-before, so
     * this is not a live bug being fixed -- it is the one field whose whole
     * purpose is to be read by a coroutine other than the one that wrote it,
     * and saying so costs nothing.
     */
    @Volatile
    private var refusedAt: Instant? = null

    override val trending: StateFlow<List<Movie>> =
        films().stateIn(scope, SharingStarted.WhileSubscribed(WATCHING_GRACE), emptyList())

    private fun films(): Flow<List<Movie>> = flow {
        var held = store.read()
        if (held != null) emit(held.movies)
        while (true) {
            val today = clock.now().atZone(zone).toLocalDate()
            val stillOwed = whatIsLeftOfTheWait()
            val wait = if (held?.stillCurrentOn(today) == true) {
                // Today's list is today's list. There is nothing to be had by
                // asking, so the only thing worth waiting for is tomorrow.
                untilTheDayTurns(today)
            } else if (stillOwed > 0) {
                // A refusal already bought a wait and it has not run out. Who
                // was watching when it was bought is not part of the deal.
                stillOwed
            } else {
                val arrived = fetch(today)
                if (arrived == null) {
                    refusedAt = clock.now()
                    AFTER_A_FAILURE_MILLIS
                } else {
                    refusedAt = null
                    held = arrived
                    store.write(arrived)
                    emit(arrived.movies)
                    untilTheDayTurns(today)
                }
            }
            delay(wait)
        }
    }

    /**
     * How much of the minute a refusal bought has not been served yet.
     *
     * This is a stopwatch question put to a calendar, and there is exactly one
     * thing a calendar cannot do with it: a wall clock is allowed to move
     * backwards. A device that had been running fast moves backwards by however
     * far it was wrong the moment it first reaches a time server. `served` is
     * then negative and the subtraction hands back **more** than the minute it
     * started from, so a three-day correction would be three days of a strip
     * that is never asked for again -- on a `@Singleton` no teardown clears,
     * with nothing on screen that would ever say so.
     *
     * A negative reading is therefore read for what it is: not a wait that has
     * hardly begun, but a wait whose beginning is no longer anywhere the clock
     * can reach. The refusal is let go and the next attempt is made now. That
     * costs one request -- the same price a clock that jumps *forward* already
     * pays here, and the price this app puts on losing a day everywhere else
     * (`DECISIONS.md` 40, 41, 51).
     *
     * Past that guard the reading cannot exceed the minute, so the floor is the
     * only end left worth clamping.
     */
    private fun whatIsLeftOfTheWait(): Long {
        val refused = refusedAt ?: return 0
        val served = Duration.between(refused, clock.now()).toMillis()
        if (served < 0) {
            refusedAt = null
            return 0
        }
        return (AFTER_A_FAILURE_MILLIS - served).coerceAtLeast(0)
    }

    /**
     * How long until the source has a different day to give.
     *
     * Midnight in the reader's own zone rather than a fixed number of hours after
     * the last answer, because the thing being waited for is a date changing.
     * TMDB does not document which hour it rolls the day at, so this can be off
     * by that lag -- and being off costs at most one request that comes back with
     * yesterday's list, once a day. The weather had the opposite problem and a
     * worse one: a fixed ten-minute window there guaranteed a third of requests
     * returned an identical value.
     */
    private fun untilTheDayTurns(today: LocalDate): Long {
        val turnsOver = today.plusDays(1).atStartOfDay(zone).toInstant()
        return Duration.between(clock.now(), turnsOver)
            .toMillis()
            // A floor under *this* wait, so a clock that lands on or past the
            // midnight it is waiting for cannot turn it into a busy loop. It is
            // not a floor under the other two waits, and does not need to be.
            .coerceAtLeast(AFTER_A_FAILURE_MILLIS)
    }

    /**
     * One attempt, and what it cost if it failed.
     *
     * An empty answer counts as an answer and is written down. That looks wrong
     * and is deliberate: treating "no rows" as a failure would retry it every
     * minute for as long as anybody watched, which is precisely the spending this
     * app's freshness policy exists to avoid. A response that does not carry the
     * list at all is a different thing and throws before it gets here.
     */
    @Suppress("TooGenericExceptionCaught", "RethrowCaughtException")
    private suspend fun fetch(day: LocalDate): TrendingMovies? =
        try {
            val films = api.trending()
            lastProblem = null
            TrendingMovies(movies = films, forDay = day)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unreadable: ContentConvertException) {
            failed("the day's films could not be read", unreadable)
        } catch (unreadable: NoTransformationFoundException) {
            failed("the day's films arrived in a shape this app cannot read", unreadable)
        } catch (unreadable: SerializationException) {
            failed("the day's films could not be read", unreadable)
        } catch (unreachable: IOException) {
            failed("the day's films could not be reached", unreachable)
        } catch (unexpected: Exception) {
            failed("asking for the day's films went wrong in a way nobody expected", unexpected)
        }

    private fun failed(what: String, cause: Throwable): TrendingMovies? {
        lastProblem = "$what: ${cause.message}"
        return null
    }
}

/** Long enough to survive a rotation, short enough not to outlive the screen. */
private const val WATCHING_GRACE = 5_000L

/**
 * The wait a refusal buys, and the floor under the wait for the next day.
 *
 * Not the floor under *any* wait, which is what this used to claim. What is
 * left of a refusal's minute is served as whatever is left of it, down to a
 * millisecond -- and it does not need a floor, because the wait it ends is the
 * one that set it: the next turn of the loop asks rather than waits again.
 */
private const val AFTER_A_FAILURE_MILLIS = 60_000L
