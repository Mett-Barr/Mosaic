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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import moozy.mosaic.domain.model.Clock
import moozy.mosaic.domain.model.Movie
import moozy.mosaic.domain.model.MovieId
import moozy.mosaic.domain.model.TrendingMovies
import org.junit.Assert.assertEquals
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
