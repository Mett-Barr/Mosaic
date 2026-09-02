package moozy.mosaic.data.movie

import app.cash.turbine.test
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import moozy.mosaic.domain.model.Clock
import moozy.mosaic.domain.model.Movie
import moozy.mosaic.domain.model.MovieId
import moozy.mosaic.domain.model.TrendingMovies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
            assertEquals("nothing is known before the first answer", emptyList<Movie>(), awaitItem())
            assertEquals(listOf("How to Train Your Dragon", "Sinners"), awaitItem().map { it.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `it asks the day's trending films, and says who is asking`() = runTest {
        val asked = mutableListOf<HttpRequestData>()
        val films = trending(
            MockEngine { request ->
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
        val films = trending(
            MockEngine {
                requests++
                respondError(HttpStatusCode.InternalServerError)
            },
            store,
        )

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
        val films = trending(
            MockEngine {
                requests++
                respondError(HttpStatusCode.InternalServerError)
            },
        )

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
        val asked = Channel<Unit>(Channel.UNLIMITED)
        val films = trending(refusing(asked))

        val watching = launch { films.trending.collect {} }
        assertTrue("the first reader pays for one attempt", within(A_MOMENT, asked))
        settleUntil { films.lastProblem != null }
        assertEquals("and pays for it straight away", 0L, currentTime)

        watching.cancel()
        runCurrent()
        advanceTimeBy(WATCHING_GRACE_LAPSED)

        // Somebody opens the feed again. The flow starts over from the top and
        // reads nothing back, because a refusal writes nothing down. Whether
        // that costs a request is the whole question.
        val watchingAgain = launch { films.trending.collect {} }

        // The wait belongs to the refusal, not to whoever happened to be
        // watching when it was bought. Otherwise a revoked or mistyped token is
        // a 401 on every visit to the feed for as long as the app is installed
        // -- and nobody is ever told, because a failure here means a shorter
        // strip and nothing else.
        assertFalse(
            "coming back to the feed is not a reason to ask again",
            within(A_MOMENT, asked),
        )

        // A wait and not a stop, which is the other half of the same policy.
        // Both clocks move, because on a device they are the same clock: the
        // scheduler's, which decides when the flow wakes, and the reader's,
        // which decides whether the wait it wakes into has been served.
        now = now.plusMillis(AFTER_A_FAILURE + 1)
        advanceTimeBy(AFTER_A_FAILURE + 1)
        assertTrue("but the minute does run out", within(A_MOMENT, asked))
        watchingAgain.cancel()
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

    private fun TestScope.trending(engine: MockEngine, store: TrendingStore = InMemoryStore()) =
        TmdbTrending(
            api = TmdbApi(client = tmdbClient(engine), token = "a-token-for-testing"),
            clock = Clock { now },
            zone = zone,
            store = store,
            scope = backgroundScope,
        )

    private fun alwaysAnswering() = MockEngine {
        requests++
        respond(page(), HttpStatusCode.OK, json)
    }

    /**
     * Let work that is happening on another thread land on this one.
     *
     * `MockEngine` answers on its own dispatcher, so a failure travels back
     * through a continuation the test scheduler has not been handed yet.
     * [runCurrent] runs whatever has arrived **without moving the virtual
     * clock**, which is the part that matters: the assertions below are about
     * what the clock says, so nothing here may advance it. The pause is the
     * only way to give the other thread a turn -- there is nothing on this
     * scheduler to wait for, and waiting on the virtual clock would run the
     * very retry the test is measuring.
     */
    private fun TestScope.settleUntil(landed: () -> Boolean) {
        repeat(SETTLING_TRIES) {
            runCurrent()
            if (landed()) return
            @Suppress("ForbiddenMethodCall")
            Thread.sleep(1)
        }
        fail("the repository never settled")
    }

    /**
     * Whether a request turns up in the next [tries] milliseconds of real time.
     *
     * The negative is the assertion that matters here, and a negative needs a
     * bound that is real rather than virtual: waiting on the virtual clock
     * would run the very retry the test is trying to prove has not happened
     * yet. So this gives the flow and the engine's own dispatcher a fixed
     * number of turns and reports what they did with them, moving the virtual
     * clock not at all.
     */
    private fun TestScope.within(tries: Int, asked: Channel<Unit>): Boolean {
        repeat(tries) {
            runCurrent()
            if (asked.tryReceive().isSuccess) return true
            @Suppress("ForbiddenMethodCall")
            Thread.sleep(1)
        }
        return false
    }

    /**
     * A source that says no, and says so out loud.
     *
     * The counter alone cannot be read from the test: `MockEngine` answers on
     * its own dispatcher rather than on the test scheduler, so "the request has
     * been made" is not something the virtual clock knows. [asked] is how the
     * test waits for it instead of guessing.
     */
    private fun refusing(asked: Channel<Unit>) = MockEngine {
        requests++
        asked.trySend(Unit)
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

/** A second of real milliseconds, which is a very long time for a mock to answer in. */
private const val SETTLING_TRIES = 1_000

/**
 * Long enough that a mock answering on another thread has certainly had its
 * turn, short enough that spending it twice is not felt.
 */
private const val A_MOMENT = 300
