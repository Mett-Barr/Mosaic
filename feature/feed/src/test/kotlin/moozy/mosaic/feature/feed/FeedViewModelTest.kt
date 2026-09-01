package moozy.mosaic.feature.feed

import app.cash.turbine.test
import java.time.Duration
import java.time.Instant
import java.util.TimeZone
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.ArticleResult
import moozy.mosaic.domain.model.ArticlesResult
import moozy.mosaic.domain.model.FeedFailure
import moozy.mosaic.domain.model.PageCursor
import moozy.mosaic.domain.model.Sky
import moozy.mosaic.domain.model.Weather
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import moozy.mosaic.domain.repository.ArticleRepository
import moozy.mosaic.domain.repository.WeatherRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The assignment asks for loading, empty, error and offline to be handled
 * explicitly. "Explicitly" is the part worth testing: a feed that shows an empty
 * list when the phone has no network has technically handled the case, and has
 * told the reader something untrue.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var deviceZone: TimeZone

    @Before
    fun useTestDispatcher() {
        Dispatchers.setMain(dispatcher)
        // The times this screen shows are the reader's own, so a test that
        // asserts one has to say whose clock it is reading.
        deviceZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Taipei"))
    }

    @After
    fun releaseDispatcher() {
        TimeZone.setDefault(deviceZone)
        Dispatchers.resetMain()
    }

    @Test
    fun `nothing is asked for until somebody is watching`() = runTest {
        val articles = FakeArticles(ArticlesResult.Loaded(listOf(article(1)), null))

        FeedViewModel(articles, FakeWeather())
        yield()

        // Constructing a view model is not a reason to spend somebody's data.
        assertEquals("nothing was watching", 0, articles.asked.size)
    }

    @Test
    fun `the card follows the reading, whenever it changes`() = runTest {
        val weather = FakeWeather()
        val feed = FeedViewModel(FakeArticles(ArticlesResult.Loaded(listOf(article(1)), null)), weather)

        val seen = mutableListOf<FeedUiState>()
        val watching = launch { feed.state.collect { seen += it } }
        runCurrent()
        // Whether to ask, and when, is the repository's. The screen only has to
        // show what the stream currently says.
        weather.says(weather())
        runCurrent()
        watching.cancel()

        assertEquals(weather().headline(), (seen.last() as FeedUiState.Content).weather)
    }

    @Test
    fun `coming back does not reload a list the reader is already in`() = runTest {
        val articles = FakeArticles(ArticlesResult.Loaded(listOf(article(1)), null))
        val feed = FeedViewModel(articles, FakeWeather())

        watch(feed)
        advanceTimeBy(6_000)
        watch(feed)

        assertEquals("the list must not move under them", 1, articles.asked.size)
    }

    @Test
    fun `an article reaches the screen as words, not as a domain object`() = runTest {
        val feed = feedOf(ArticlesResult.Loaded(listOf(article(1)), next = null))

        feed.state.test {
            val row = (awaitItem() as FeedUiState.Content).articles.single()

            assertEquals("Article 1", row.title)
            // One string, because it is one line on the card -- and because
            // deciding how a time reads is not a decision a composable can be
            // asked about without a device.
            assertEquals("Somewhere · 31 Aug, 18:00", row.attribution)
        }
    }

    @Test
    fun `the weather reaches the screen as words too`() = runTest {
        val feed = FeedViewModel(
            FakeArticles(ArticlesResult.Loaded(listOf(article(1)), next = null)),
            FakeWeather(weather()),
        )

        feed.state.test {
            var shown: WeatherHeadline? = null
            while (shown == null) {
                val next = awaitItem()
                if (next is FeedUiState.Content) shown = next.weather
            }

            assertEquals("Taipei", shown.place)
            assertEquals("26°", shown.temperature)
            assertEquals("Cloudy · 32° / 25°", shown.conditions)
        }
    }

    @Test
    fun `a failure reaches the screen as something a reader can read`() = runTest {
        val feed = feedOf(ArticlesResult.Failed(FeedFailure.Server(500)))

        feed.state.test {
            val error = awaitItem() as FeedUiState.Error

            assertEquals("Something went wrong.", error.message)
            assertEquals("The feed is having trouble (error 500).", error.hint)
        }
    }

    @Test
    fun `it says it is loading before anything has arrived`() = runTest {
        val gate = CompletableDeferred<ArticlesResult>()
        val feed = FeedViewModel(
            object : ArticleRepository {
                override suspend fun articles(after: PageCursor?, force: Boolean) = gate.await()
                override suspend fun article(id: ArticleId) = notAsked()
            },
            FakeWeather(),
        )

        feed.state.test {
            assertEquals(FeedUiState.Loading, awaitItem())

            gate.complete(ArticlesResult.Loaded(listOf(article(1)), next = null))

            assertTrue(awaitItem() is FeedUiState.Content)
        }
    }

    @Test
    fun `articles become something to read, and say whether there is more`() = runTest {
        val feed = feedOf(
            ArticlesResult.Loaded(listOf(article(1), article(2)), PageCursor(NEXT)),
        )

        feed.state.test {
            val content = awaitItem() as FeedUiState.Content
            assertEquals(listOf("1", "2"), content.articles.map { it.id.value })
            assertTrue(content.canLoadMore)
        }
    }

    @Test
    fun `the weather arrives beside the articles, not instead of them`() = runTest {
        val feed = FeedViewModel(
            FakeArticles(ArticlesResult.Loaded(listOf(article(1)), next = null)),
            FakeWeather(weather()),
        )

        feed.state.test {
            val content = awaitItem() as FeedUiState.Content
            assertEquals(listOf("1"), content.articles.map { it.id.value })
            assertEquals(weather().headline(), content.weather)
        }
    }

    @Test
    fun `weather that arrives before the articles is still there when they do`() = runTest {
        val gate = CompletableDeferred<ArticlesResult>()
        val feed = FeedViewModel(
            object : ArticleRepository {
                override suspend fun articles(after: PageCursor?, force: Boolean) = gate.await()
                override suspend fun article(id: ArticleId) = notAsked()
            },
            FakeWeather(weather()),
        )

        feed.state.test {
            assertEquals(FeedUiState.Loading, awaitItem())

            gate.complete(ArticlesResult.Loaded(listOf(article(1)), next = null))

            val content = awaitItem() as FeedUiState.Content
            assertEquals("the card should not depend on which answer came back first", weather().headline(), content.weather)
        }
    }

    @Test
    fun `the weather survives the arrival of the next page`() = runTest {
        val feed = FeedViewModel(
            FakeArticles(
                ArticlesResult.Loaded(listOf(article(1)), PageCursor(NEXT)),
                ArticlesResult.Loaded(listOf(article(2)), next = null),
            ),
            FakeWeather(weather()),
        )

        feed.state.test {
            // Which of the two lands first is not this test's business, so it
            // waits for both rather than assuming an order.
            var content = awaitItem() as? FeedUiState.Content
            while (content?.weather == null) content = awaitItem() as? FeedUiState.Content

            feed.loadMore()
            awaitItem()

            val more = awaitItem() as FeedUiState.Content
            assertEquals(listOf("1", "2"), more.articles.map { it.id.value })
            assertEquals("a card should not vanish because a page arrived", weather().headline(), more.weather)
        }
    }

    @Test
    fun `weather that will not load does not take the feed with it`() = runTest {
        val feed = FeedViewModel(
            FakeArticles(ArticlesResult.Loaded(listOf(article(1)), next = null)),
            FakeWeather(),
        )

        feed.state.test {
            val content = awaitItem() as FeedUiState.Content
            assertEquals(listOf("1"), content.articles.map { it.id.value })
            assertNull("a card that did not load is not a card", content.weather)
        }
    }

    @Test
    fun `a feed that will not load is a failure even when the weather is fine`() = runTest {
        val feed = FeedViewModel(
            FakeArticles(ArticlesResult.Failed(FeedFailure.Offline())),
            FakeWeather(weather()),
        )

        feed.state.test {
            assertEquals(FeedUiState.Offline, awaitItem())
        }
    }

    @Test
    fun `a page with nothing in it is empty, not an empty list of articles`() = runTest {
        val feed = feedOf(ArticlesResult.Loaded(emptyList(), next = null))

        feed.state.test {
            assertEquals(FeedUiState.Empty, awaitItem())
        }
    }

    @Test
    fun `a page nobody could read is not an empty feed either`() = runTest {
        val feed = feedOf(ArticlesResult.Loaded(emptyList(), next = null, dropped = 3))

        feed.state.test {
            val state = awaitItem()
            assertTrue("expected an error, got $state", state is FeedUiState.Error)
            assertEquals(
                "expected it to say the page was unreadable, got $state",
                "The feed sent something this app could not read.",
                (state as FeedUiState.Error).hint,
            )
        }
    }

    @Test
    fun `a next page nobody could read keeps what is already on screen`() = runTest {
        val feed = FeedViewModel(
            FakeArticles(
                ArticlesResult.Loaded(listOf(article(1)), PageCursor(NEXT)),
                ArticlesResult.Loaded(emptyList(), next = null, dropped = 2),
            ),
            FakeWeather(),
        )

        feed.state.test {
            awaitItem()

            feed.loadMore()
            awaitItem()

            val state = awaitItem() as FeedUiState.Content
            assertEquals(listOf("1"), state.articles.map { it.id.value })
            assertEquals("expected to be told, got $state", "The feed sent something this app could not read.", state.moreFailed)
        }
    }

    @Test
    fun `no network is its own answer, not an empty feed`() = runTest {
        val feed = feedOf(ArticlesResult.Failed(FeedFailure.Offline()))

        feed.state.test {
            assertEquals(FeedUiState.Offline, awaitItem())
        }
    }

    @Test
    fun `a server that failed is an error, and not the same screen as offline`() = runTest {
        val feed = feedOf(ArticlesResult.Failed(FeedFailure.Server(500)))

        feed.state.test {
            val state = awaitItem()
            assertTrue("expected an error, got $state", state is FeedUiState.Error)
            assertEquals(
                "The feed is having trouble (error 500).",
                (state as FeedUiState.Error).hint,
            )
        }
    }

    @Test
    fun `asking for more adds to what is already there`() = runTest {
        val repository = FakeArticles(
            ArticlesResult.Loaded(listOf(article(1)), PageCursor(NEXT)),
            ArticlesResult.Loaded(listOf(article(2)), next = null),
        )
        val feed = FeedViewModel(repository, FakeWeather())

        feed.state.test {
            assertEquals(listOf("1"), (awaitItem() as FeedUiState.Content).articles.map { it.id.value })

            feed.loadMore()

            assertTrue("the reader should see it working", (awaitItem() as FeedUiState.Content).loadingMore)

            val more = awaitItem() as FeedUiState.Content
            assertEquals(listOf("1", "2"), more.articles.map { it.id.value })
            assertTrue("nothing left to load", !more.canLoadMore)
        }
        assertEquals(listOf(null, PageCursor(NEXT)), repository.asked)
    }

    @Test
    fun `an article that arrives twice is only in the list once`() = runTest {
        // The cached first page carries the cursor it was fetched with, and the
        // list has moved on since. A window computed against yesterday's offsets
        // hands back something already on screen -- and two rows with one id is
        // not a cosmetic problem: the list is keyed by it.
        val feed = FeedViewModel(
            FakeArticles(
                ArticlesResult.Loaded(listOf(article(1), article(2)), PageCursor(NEXT)),
                ArticlesResult.Loaded(listOf(article(2), article(3)), next = null),
            ),
            FakeWeather(),
        )

        feed.state.test {
            awaitItem()
            feed.loadMore()
            awaitItem()

            val all = (awaitItem() as FeedUiState.Content).articles.map { it.id.value }
            assertEquals(listOf("1", "2", "3"), all)
        }
    }

    @Test
    fun `a page that fails to load does not throw away the articles already read`() = runTest {
        val feed = FeedViewModel(
            FakeArticles(
                ArticlesResult.Loaded(listOf(article(1)), PageCursor(NEXT)),
                ArticlesResult.Failed(FeedFailure.Offline()),
            ),
            FakeWeather(),
        )

        feed.state.test {
            awaitItem()

            feed.loadMore()
            awaitItem()

            val state = awaitItem()
            assertTrue("expected to still have the first page, got $state", state is FeedUiState.Content)
            val content = state as FeedUiState.Content
            assertEquals(listOf("1"), content.articles.map { it.id.value })
            assertEquals(
                "the reader should be told the next page failed",
                "There is no connection right now.",
                content.moreFailed,
            )
            assertTrue("and be able to try again", content.canLoadMore)
        }
    }

    @Test
    fun `pulling to refresh keeps the list on screen while it loads`() = runTest {
        val repository = FakeArticles(
            ArticlesResult.Loaded(listOf(article(1)), next = null),
            ArticlesResult.Loaded(listOf(article(2)), next = null),
        )
        val feed = FeedViewModel(repository, FakeWeather())

        feed.state.test {
            awaitItem()

            feed.refresh()

            // Not a spinner where the list was: somebody who pulls a list down is
            // still reading it, and taking it away to prove something is loading
            // is a worse answer than leaving it there.
            val during = awaitItem() as FeedUiState.Content
            assertEquals(listOf("1"), during.articles.map { it.id.value })
            assertTrue("the pull should be visibly doing something", during.refreshing)

            val after = awaitItem() as FeedUiState.Content
            assertEquals(listOf("2"), after.articles.map { it.id.value })
            assertTrue("and visibly stop", !after.refreshing)
        }
        assertEquals(listOf(true), repository.forced)
    }

    @Test
    fun `retrying from an error screen has nothing to keep`() = runTest {
        val feed = FeedViewModel(
            FakeArticles(
                ArticlesResult.Failed(FeedFailure.Offline()),
                ArticlesResult.Loaded(listOf(article(1)), next = null),
            ),
            FakeWeather(),
        )

        feed.state.test {
            assertEquals(FeedUiState.Offline, awaitItem())

            feed.refresh()

            assertEquals(FeedUiState.Loading, awaitItem())
            assertTrue(awaitItem() is FeedUiState.Content)
        }
    }

    @Test
    fun `retrying insists, rather than being told the answer is still fresh`() = runTest {
        val repository = FakeArticles(
            ArticlesResult.Loaded(listOf(article(1)), next = null),
            ArticlesResult.Loaded(listOf(article(2)), next = null),
        )
        val feed = FeedViewModel(repository, FakeWeather())

        feed.state.test {
            awaitItem()

            feed.retry()

            // The list stays up while it reloads; see the pull-to-refresh case.
            awaitItem()
            assertEquals(listOf("2"), (awaitItem() as FeedUiState.Content).articles.map { it.id.value })
        }
        assertEquals("a reader asking again should not be answered from a file", listOf(true), repository.forced)
    }

    @Test
    fun `asking for the next page never insists`() = runTest {
        val repository = FakeArticles(
            ArticlesResult.Loaded(listOf(article(1)), PageCursor(NEXT)),
            ArticlesResult.Loaded(listOf(article(2)), next = null),
        )
        val feed = FeedViewModel(repository, FakeWeather())

        feed.state.test {
            awaitItem()
            feed.loadMore()
            awaitItem()
            awaitItem()
        }

        assertEquals("only the first page is ever cached, so only it can be insisted on", emptyList<Boolean>(), repository.forced)
    }

    @Test
    fun `retrying after a failure asks again from the beginning`() = runTest {
        val repository = FakeArticles(
            ArticlesResult.Failed(FeedFailure.Offline()),
            ArticlesResult.Loaded(listOf(article(1)), next = null),
        )
        val feed = FeedViewModel(repository, FakeWeather())

        feed.state.test {
            assertEquals(FeedUiState.Offline, awaitItem())

            feed.retry()

            assertEquals(FeedUiState.Loading, awaitItem())
            assertTrue(awaitItem() is FeedUiState.Content)
        }
        assertEquals(listOf(null, null), repository.asked)
    }

    /** Somebody looks at the screen, then stops -- which is what leaving it does. */
    private suspend fun TestScope.watch(feed: FeedViewModel) {
        val watching = launch { feed.state.collect {} }
        runCurrent()
        watching.cancel()
    }

    private fun feedOf(vararg results: ArticlesResult) =
        FeedViewModel(FakeArticles(*results), FakeWeather())

    private fun weather() = Weather(
        place = "Taipei",
        temperature = 26,
        high = 32,
        low = 25,
        sky = Sky.CLOUDY,
        measuredAt = Instant.parse("2026-09-01T02:30:00Z"),
        stepsEvery = Duration.ofMinutes(15),
    )

    /**
     * The repository decides when to ask; this only has to be a stream. What the
     * screen does with it is the whole of what these tests are about.
     */
    private class FakeWeather(reading: Weather? = null) : WeatherRepository {
        private val readings = MutableStateFlow(reading)
        override val current: StateFlow<Weather?> = readings

        fun says(reading: Weather?) {
            readings.value = reading
        }
    }

    private fun article(id: Int) = ArticleItem(
        id = ArticleId("$id"),
        title = "Article $id",
        summary = "",
        source = "Somewhere",
        url = "https://example.com/$id",
        imageUrl = null,
        publishedAt = Instant.parse("2026-08-31T10:00:00Z"),
    )

    private class FakeArticles(vararg results: ArticlesResult) : ArticleRepository {
        private val queue = ArrayDeque(results.toList())
        val asked = mutableListOf<PageCursor?>()

        override suspend fun article(id: ArticleId) = notAsked()

        /** Which calls insisted on a request rather than accepting a cached page. */
        val forced = mutableListOf<Boolean>()

        override suspend fun articles(after: PageCursor?, force: Boolean): ArticlesResult {
            if (force) forced += true
            asked += after
            // A real one goes to the network and therefore suspends. Without this,
            // the feed never yields between setting "loading" and setting the
            // result, and a StateFlow conflates the two into one -- so the test
            // would be asserting against a collapse this fake invented.
            yield()
            return queue.removeFirstOrNull() ?: error("the feed asked for a page nobody prepared")
        }
    }

    private companion object {
        /** The feed never asks for a single article; the detail screen does. */
        fun notAsked(): Nothing = error("the feed should not ask for one article")

        const val NEXT = "https://api.spaceflightnewsapi.net/v4/articles/?limit=20&offset=20"
    }
}
