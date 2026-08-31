package moozy.mosaic.feature.feed

import app.cash.turbine.test
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import moozy.mosaic.domain.model.WeatherResult
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

    @Before
    fun useTestDispatcher() = Dispatchers.setMain(dispatcher)

    @After
    fun releaseDispatcher() = Dispatchers.resetMain()

    @Test
    fun `it says it is loading before anything has arrived`() = runTest {
        val gate = CompletableDeferred<ArticlesResult>()
        val feed = FeedViewModel(
            object : ArticleRepository {
                override suspend fun articles(after: PageCursor?) = gate.await()
                override suspend fun article(id: ArticleId) = notAsked()
            },
            FakeWeather(WeatherResult.Failed(FeedFailure.Offline())),
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
            FakeWeather(WeatherResult.Loaded(weather())),
        )

        feed.state.test {
            val content = awaitItem() as FeedUiState.Content
            assertEquals(listOf("1"), content.articles.map { it.id.value })
            assertEquals(weather(), content.weather)
        }
    }

    @Test
    fun `weather that arrives before the articles is still there when they do`() = runTest {
        val gate = CompletableDeferred<ArticlesResult>()
        val feed = FeedViewModel(
            object : ArticleRepository {
                override suspend fun articles(after: PageCursor?) = gate.await()
                override suspend fun article(id: ArticleId) = notAsked()
            },
            FakeWeather(WeatherResult.Loaded(weather())),
        )

        feed.state.test {
            assertEquals(FeedUiState.Loading, awaitItem())

            gate.complete(ArticlesResult.Loaded(listOf(article(1)), next = null))

            val content = awaitItem() as FeedUiState.Content
            assertEquals("the card should not depend on which answer came back first", weather(), content.weather)
        }
    }

    @Test
    fun `the weather survives the arrival of the next page`() = runTest {
        val feed = FeedViewModel(
            FakeArticles(
                ArticlesResult.Loaded(listOf(article(1)), PageCursor(NEXT)),
                ArticlesResult.Loaded(listOf(article(2)), next = null),
            ),
            FakeWeather(WeatherResult.Loaded(weather())),
        )

        feed.state.test {
            awaitItem()
            assertEquals(weather(), (awaitItem() as FeedUiState.Content).weather)

            feed.loadMore()
            awaitItem()

            val more = awaitItem() as FeedUiState.Content
            assertEquals(listOf("1", "2"), more.articles.map { it.id.value })
            assertEquals("a card should not vanish because a page arrived", weather(), more.weather)
        }
    }

    @Test
    fun `weather that will not load does not take the feed with it`() = runTest {
        val feed = FeedViewModel(
            FakeArticles(ArticlesResult.Loaded(listOf(article(1)), next = null)),
            FakeWeather(WeatherResult.Failed(FeedFailure.Offline())),
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
            FakeWeather(WeatherResult.Loaded(weather())),
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
            assertTrue(
                "expected it to say the page was unreadable, got $state",
                (state as FeedUiState.Error).reason is FeedFailure.Unreadable,
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
            FakeWeather(WeatherResult.Failed(FeedFailure.Offline())),
        )

        feed.state.test {
            awaitItem()

            feed.loadMore()
            awaitItem()

            val state = awaitItem() as FeedUiState.Content
            assertEquals(listOf("1"), state.articles.map { it.id.value })
            assertTrue("expected to be told, got $state", state.moreFailed is FeedFailure.Unreadable)
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
            assertEquals(FeedFailure.Server(500), (state as FeedUiState.Error).reason)
        }
    }

    @Test
    fun `asking for more adds to what is already there`() = runTest {
        val repository = FakeArticles(
            ArticlesResult.Loaded(listOf(article(1)), PageCursor(NEXT)),
            ArticlesResult.Loaded(listOf(article(2)), next = null),
        )
        val feed = FeedViewModel(repository, FakeWeather(WeatherResult.Failed(FeedFailure.Offline())))

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
    fun `a page that fails to load does not throw away the articles already read`() = runTest {
        val feed = FeedViewModel(
            FakeArticles(
                ArticlesResult.Loaded(listOf(article(1)), PageCursor(NEXT)),
                ArticlesResult.Failed(FeedFailure.Offline()),
            ),
            FakeWeather(WeatherResult.Failed(FeedFailure.Offline())),
        )

        feed.state.test {
            awaitItem()

            feed.loadMore()
            awaitItem()

            val state = awaitItem()
            assertTrue("expected to still have the first page, got $state", state is FeedUiState.Content)
            val content = state as FeedUiState.Content
            assertEquals(listOf("1"), content.articles.map { it.id.value })
            assertTrue("the reader should be told the next page failed", content.moreFailed is FeedFailure.Offline)
            assertTrue("and be able to try again", content.canLoadMore)
        }
    }

    @Test
    fun `retrying after a failure asks again from the beginning`() = runTest {
        val repository = FakeArticles(
            ArticlesResult.Failed(FeedFailure.Offline()),
            ArticlesResult.Loaded(listOf(article(1)), next = null),
        )
        val feed = FeedViewModel(repository, FakeWeather(WeatherResult.Failed(FeedFailure.Offline())))

        feed.state.test {
            assertEquals(FeedUiState.Offline, awaitItem())

            feed.retry()

            assertEquals(FeedUiState.Loading, awaitItem())
            assertTrue(awaitItem() is FeedUiState.Content)
        }
        assertEquals(listOf(null, null), repository.asked)
    }

    private fun feedOf(vararg results: ArticlesResult) =
        FeedViewModel(FakeArticles(*results), FakeWeather(WeatherResult.Failed(FeedFailure.Offline())))

    private fun weather() = Weather(
        place = "Taipei",
        temperature = 26,
        high = 32,
        low = 25,
        sky = Sky.CLOUDY,
        measuredAt = Instant.parse("2026-09-01T02:30:00Z"),
    )

    private class FakeWeather(private val result: WeatherResult) : WeatherRepository {
        override suspend fun current(): WeatherResult {
            yield()
            return result
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

        override suspend fun articles(after: PageCursor?): ArticlesResult {
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
