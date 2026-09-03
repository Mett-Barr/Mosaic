package moozy.mosaic.data.movie

import app.cash.turbine.test
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import moozy.mosaic.domain.model.Clock
import moozy.mosaic.domain.model.Movie
import moozy.mosaic.domain.model.MovieId
import moozy.mosaic.domain.model.TrendingMovies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The freshness rule for films, as the behaviour it is meant to be rather than as
 * the arithmetic it is made of.
 *
 * The weather is asked again on the grid its own response names; the articles are
 * asked again only when a person asks. Films get the third answer: **once for the
 * day the source computes them for**, which the app knows because the address it
 * asks -- `/trending/movie/day` -- says so. A list held for today is therefore not
 * re-asked by a launch, by coming back to the screen, or by a pull; only the day
 * turning over makes a request worth making.
 *
 * That is the part worth a test. Nothing here would notice a policy that quietly
 * became "ask every time", because a card would still be full -- only the counter
 * would move.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TmdbTrendingTest {

    private var requests = 0
    private var now: Instant = Instant.parse("2026-09-02T12:00:00Z")

    /** UTC, so that "the day" in the assertions is the day in the fixtures. */
    private val zone: ZoneId = ZoneId.of("UTC")

    @Test
    fun `nobody watching is nobody's data spent`() = runTest {
        trending(alwaysAnswering())

        runCurrent()

        assertEquals("a repository is not a reason to make a request", 0, requests)
    }

    @Test
    fun `the first watcher gets the day's films`() = runTest {
        val films = trending(alwaysAnswering())

        films.trending.test {
            // `stateIn`'s declared initial value, which is the literal
            // `emptyList()` written three lines into the repository. Asserting
            // it back would be asserting that literal, so it is only consumed.
            awaitItem()
            assertEquals(listOf("How to Train Your Dragon", "Sinners"), awaitItem().map { it.title })
            cancelAndIgnoreRemainingEvents()
        }

        // The other half of what this test is named for. `nobody watching is
        // nobody's data spent` proves the zero; without this nothing proved the
        // one, and the counter is the only thing a policy that quietly became
        // "ask every time" would move -- the card would still be full.
        assertEquals("one watcher, one request", 1, requests)
    }

    @Test
    fun `it asks the day's trending films, and says who is asking`() = runTest {
        val asked = mutableListOf<HttpRequestData>()
        val films = trending(
            answering = { request ->
                asked += request
                requests++
                respond(page(), HttpStatusCode.OK, json)
            },
        )

        films.trending.test {
            awaitItem()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        val request = asked.first()
        assertEquals("api.themoviedb.org", request.url.host)
        assertEquals("/3/trending/movie/day", request.url.encodedPath)
        // TMDB's v4 read access token is a bearer token, not a query parameter --
        // which is also why it never reaches a URL anything might log.
        assertEquals("Bearer a-token-for-testing", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `the list written down last time is shown before anything is asked`() = runTest {
        val store = InMemoryStore()
        store.write(TrendingMovies(listOf(yesterdaysFilm()), LocalDate.parse("2026-09-01")))
        val films = trending(alwaysAnswering(), store)

        films.trending.test {
            assertEquals(emptyList<Movie>(), awaitItem())
            assertEquals(
                "the last run's answer arrives before the network's",
                listOf("A Film From Yesterday"),
                awaitItem().map { it.title },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a list already held for today is not asked for again`() = runTest {
        val store = InMemoryStore()
        store.write(TrendingMovies(listOf(yesterdaysFilm()), LocalDate.parse("2026-09-02")))
        val films = trending(alwaysAnswering(), store)

        films.trending.test {
            assertEquals(emptyList<Movie>(), awaitItem())
            assertEquals(listOf("A Film From Yesterday"), awaitItem().map { it.title })
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        // This is the whole policy: the source recomputes once a day, so a list
        // stamped with today cannot be improved by asking for it again.
        assertEquals("today's list is today's list", 0, requests)
    }

    @Test
    fun `when the day turns over there is a new list to be had`() = runTest {
        val store = InMemoryStore()
        store.write(TrendingMovies(listOf(yesterdaysFilm()), LocalDate.parse("2026-09-02")))
        val films = trending(alwaysAnswering(), store)

        films.trending.test {
            assertEquals(emptyList<Movie>(), awaitItem())
            assertEquals(listOf("A Film From Yesterday"), awaitItem().map { it.title })

            // Midnight in the zone the app counts days in.
            now = Instant.parse("2026-09-03T00:00:01Z")
            assertEquals(
                "a new day is a new list",
                listOf("How to Train Your Dragon", "Sinners"),
                awaitItem().map { it.title },
            )
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, requests)
    }

    @Test
    fun `a list survives a failure rather than being replaced by one`() = runTest {
        val store = InMemoryStore()
        store.write(TrendingMovies(listOf(yesterdaysFilm()), LocalDate.parse("2026-09-01")))
        val films = trending(refusing(), store)

        films.trending.test {
            assertEquals(emptyList<Movie>(), awaitItem())
            assertEquals(listOf("A Film From Yesterday"), awaitItem().map { it.title })
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(
            "a failed request is not a reason to empty the strip",
            listOf("A Film From Yesterday"),
            films.trending.value.map { it.title },
        )
    }

    @Test
    fun `nothing ever arriving is no strip rather than a guess`() = runTest {
        val films = trending(refusing())

        films.trending.test {
            assertEquals(emptyList<Movie>(), awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue("a card that cannot be filled is absent", films.trending.value.isEmpty())
    }

    @Test
    fun `the minute a refusal buys is not spent by the reader coming back`() = runTest {
        // Reading -> Saved -> Reading, or a trip out of the app and back: the
        // strip loses its watcher for longer than the sharing grace, so the flow
        // is torn down and the next subscriber starts it again from the top.
        // Every other test here subscribes once, which is why none of them has
        // anything to say about what the second subscription costs.
        val films = trending(refusing())

        val watching = launch { films.trending.collect {} }
        // [runCurrent] runs what is due without moving the virtual clock, so a
        // request counted after it is a request made at virtual time zero.
        // "Straight away" is that, and it needs no separate assertion: an
        // implementation that waited before its first attempt would leave the
        // count at nought here rather than leave the clock somewhere else.
        runCurrent()
        assertEquals("the first reader pays for one attempt, and pays now", 1, requests)
        assertNotNull("and a refusal is what it paid for", films.lastProblem)

        // Both clocks move, because on a device they are the same clock: the
        // scheduler's, which decides when the flow wakes, and the reader's,
        // which decides whether the wait it wakes into has been served. Leaving
        // the reader's frozen for the time away is what made this test unable
        // to see the bug it is named for -- with `now` still standing at the
        // refusal, the returning reader was charged a whole fresh minute, and
        // the remainder that is the entire point of the function was never
        // computed at all.
        watching.cancel()
        runCurrent()
        now = now.plusMillis(WATCHING_GRACE_LAPSED)
        advanceTimeBy(WATCHING_GRACE_LAPSED)

        // Somebody opens the feed again. The flow starts over from the top and
        // reads nothing back, because a refusal writes nothing down. Whether
        // that costs a request is the whole question.
        val watchingAgain = launch { films.trending.collect {} }
        runCurrent()

        // The wait belongs to the refusal, not to whoever happened to be
        // watching when it was bought. Otherwise a revoked or mistyped token is
        // a 401 on every visit to the feed for as long as the app is installed
        // -- and nobody is ever told, because a failure here means a shorter
        // strip and nothing else.
        assertEquals("coming back to the feed is not a reason to ask again", 1, requests)

        // A wait and not a stop, which is the other half of the same policy --
        // and what is left of the wait, not another whole one. Six of the sixty
        // seconds were served with nobody watching, so only the remainder is
        // advanced here: an implementation that started the minute over for the
        // returning reader would still be six seconds short of asking, and this
        // is the only assertion in the file that would notice.
        now = now.plusMillis(WHAT_IS_LEFT_OF_THE_MINUTE)
        advanceTimeBy(WHAT_IS_LEFT_OF_THE_MINUTE)
        assertEquals("but the minute does run out", 2, requests)
        watchingAgain.cancel()
    }

    @Test
    fun `a clock corrected backwards under the wait is not a wait that long`() = runTest {
        val films = trending(refusing())

        val watching = launch { films.trending.collect {} }
        runCurrent()
        assertEquals("the first reader pays for one attempt", 1, requests)
        assertNotNull("and a refusal is what starts the wait", films.lastProblem)

        // The device had been running three days fast and has just reached a
        // time server for the first time. Nothing about the refusal changed --
        // only the calendar the wait is being measured against did.
        now = now.minus(Duration.ofDays(A_CORRECTION_IN_DAYS))

        // A minute is a minute. Read as "how long since an instant that is now
        // in the future", it is three days: three days in which no request is
        // ever made, on a `@Singleton` that survives every teardown, with
        // nothing on screen that would ever say so.
        advanceTimeBy(AFTER_A_FAILURE + 1)
        assertEquals("the wait is a minute, not the size of the jump", 2, requests)
        watching.cancel()
    }

    @Test
    fun `a day that arrived is written down, so the next launch does not ask`() = runTest {
        val store = InMemoryStore()
        val films = trending(alwaysAnswering(), store)

        films.trending.test {
            awaitItem()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(LocalDate.parse("2026-09-02"), store.read()?.forDay)
        assertEquals(2, store.read()?.movies?.size)
    }

    /**
     * The repository, wired to a mock that answers **on this test's own
     * scheduler**.
     *
     * That last part is the whole of why [requests] can be read straight after
     * a [runCurrent] rather than waited for. `MockEngineConfig` extends
     * `HttpClientEngineConfig`, and `HttpClientEngineBase` builds the engine's
     * context out of it -- `config.dispatcher ?: ioDispatcher()` -- so handing
     * it the test's own dispatcher puts the handler, and the continuation that
     * resumes the flow behind it, on the scheduler this test drives. Left at
     * the default the engine answers on Ktor's IO pool instead, and "the
     * request has been made" becomes a fact about another thread that the
     * virtual clock has no way to observe.
     */
    private fun TestScope.trending(
        answering: MockRequestHandler,
        store: TrendingStore = InMemoryStore(),
    ) = TmdbTrending(
        api = TmdbApi(
            client = tmdbClient(
                MockEngine(
                    MockEngineConfig().apply {
                        dispatcher = UnconfinedTestDispatcher(testScheduler)
                        addHandler(answering)
                    },
                ),
            ),
            token = "a-token-for-testing",
        ),
        clock = Clock { now },
        zone = zone,
        store = store,
        scope = backgroundScope,
    )

    private fun alwaysAnswering(): MockRequestHandler = {
        requests++
        respond(page(), HttpStatusCode.OK, json)
    }

    /** A source that says no. */
    private fun refusing(): MockRequestHandler = {
        requests++
        respondError(HttpStatusCode.InternalServerError)
    }

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun page() = """
        {"page": 1, "total_pages": 1000, "total_results": 20000,
         "results": [
           {"id": 1087192, "title": "How to Train Your Dragon",
            "poster_path": "/q5pXRYTycaeW6dEgsCrd4mYPmxM.jpg",
            "vote_average": 8.117, "vote_count": 1243},
           {"id": 1233413, "title": "Sinners", "poster_path": "/yqz9Ngb7Bo1F0MbrPzMd9OJBu6Q.jpg",
            "vote_average": 7.542, "vote_count": 2210}
         ]}
    """.trimIndent()

    private fun yesterdaysFilm() = Movie(
        id = MovieId(555),
        title = "A Film From Yesterday",
        rating = 6.0,
        posterUrl = null,
    )

    private class InMemoryStore : TrendingStore {
        private var held: TrendingMovies? = null
        override suspend fun read(): TrendingMovies? = held
        override suspend fun write(trending: TrendingMovies) {
            held = trending
        }
    }
}

/**
 * Past `SharingStarted.WhileSubscribed`'s five seconds, so that the next
 * subscriber really does start the flow again rather than joining the old one.
 */
private const val WATCHING_GRACE_LAPSED = 6_000L

/** The wait a refused day buys, which the repository states as a minute. */
private const val AFTER_A_FAILURE = 60_000L

/**
 * What the reader who comes back still owes: the minute less the part of it
 * that was served while nobody was watching, and one millisecond to land past
 * the end of it rather than exactly on it.
 *
 * Advancing a whole minute here instead would pass whether the wait survived
 * the watcher or started again with the next one.
 */
private const val WHAT_IS_LEFT_OF_THE_MINUTE = AFTER_A_FAILURE - WATCHING_GRACE_LAPSED + 1

/**
 * A backwards correction large enough that serving it as a wait would be a
 * silence measured in days rather than in minutes.
 */
private const val A_CORRECTION_IN_DAYS = 3L
