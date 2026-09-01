package moozy.mosaic.data.article

import java.time.Instant
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.ArticleResult
import moozy.mosaic.domain.model.ArticlesResult
import moozy.mosaic.domain.model.FeedFailure
import moozy.mosaic.domain.model.PageCursor
import moozy.mosaic.domain.repository.ArticleRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The half of the freshness policy that saves anything. Deciding what "fresh"
 * means costs no data; not making the request does.
 */
class ArticlesWithAFallbackTest {

    private val noon: Instant = Instant.parse("2026-09-01T12:00:00Z")
    private var now: Instant = noon

    private fun caching(network: ArticleRepository, cache: FakeCache = FakeCache()) =
        ArticlesWithAFallback(network = network, cache = cache)

    @Test
    fun `the page already here is given at once, and a newer one is sent for`() = runTest {
        val cache = FakeCache()
        cache.write(CachedArticles(listOf(article(1)), next = null))
        val network = CountingArticles(ArticlesResult.Loaded(listOf(article(2)), next = null))
        val caching = caching(network, cache)

        val page = caching.firstPage()

        // Cold start is the process having been killed: nothing is in memory, but
        // the list is on disk. It goes on screen now; the newer one arrives as a
        // second generation rather than as a wait.
        assertEquals(listOf("1"), (page as ArticlesResult.Loaded).articles.map { it.id.value })
        runCurrent()
        assertEquals("and somebody went to look for a newer one", 1, network.calls)
    }

    @Test
    fun `with nothing here it waits for the network rather than saying there is nothing`() = runTest {
        val network = CountingArticles(ArticlesResult.Loaded(listOf(article(1)), next = null))

        val page = caching(network).firstPage()

        assertEquals(listOf("1"), (page as ArticlesResult.Loaded).articles.map { it.id.value })
        assertEquals(1, network.calls)
    }

    @Test
    fun `asking for a newer page writes it down`() = runTest {
        val cache = FakeCache()
        val caching = caching(CountingArticles(ArticlesResult.Loaded(listOf(article(2)), next = null)), cache)

        caching.refreshFirstPage()

        assertEquals(listOf("2"), cache.stored?.articles?.map { it.id.value })
    }

    @Test
    fun `replacing a page that was already here says so`() = runTest {
        val cache = FakeCache()
        cache.write(CachedArticles(listOf(article(1)), next = null))
        val caching = caching(CountingArticles(ArticlesResult.Loaded(listOf(article(2)), next = null)), cache)

        val said = mutableListOf<Unit>()
        val watching = launch { caching.changed.collect { said += it } }
        runCurrent()
        caching.refreshFirstPage()
        runCurrent()
        watching.cancel()

        assertEquals("whoever is showing the old one needs to know", 1, said.size)
    }

    @Test
    fun `arriving where there was nothing does not say anything changed`() = runTest {
        val caching = caching(CountingArticles(ArticlesResult.Loaded(listOf(article(1)), next = null)))

        val said = mutableListOf<Unit>()
        val watching = launch { caching.changed.collect { said += it } }
        runCurrent()
        caching.refreshFirstPage()
        runCurrent()
        watching.cancel()

        // Nobody was shown the old one, because there was no old one. Saying it
        // changed would ask the screen to redraw what it is already drawing.
        assertEquals(emptyList<Unit>(), said)
    }

    @Test
    fun `every ask for the first page goes to the network`() = runTest {
        val network = CountingArticles(
            ArticlesResult.Loaded(listOf(article(1)), next = null),
            ArticlesResult.Loaded(listOf(article(2)), next = null),
        )
        val caching = caching(network)

        caching.articles(after = null)
        now = noon.plusSeconds(60)
        val second = caching.articles(after = null)

        // There is no window any more. The only two things that ask for a first
        // page are the app starting and the reader pulling the list down, and
        // both of those are somebody asking to see the feed.
        assertEquals("nobody asks for the top of the list by accident", 2, network.calls)
        assertEquals(listOf("2"), (second as ArticlesResult.Loaded).articles.map { it.id.value })
    }

    @Test
    fun `a page that will not load is answered by the one already here`() = runTest {
        val network = CountingArticles(
            ArticlesResult.Loaded(listOf(article(1)), next = null),
            ArticlesResult.Failed(FeedFailure.Offline()),
        )
        val caching = caching(network)

        caching.articles(after = null)
        now = noon.plusSeconds(60 * 60)
        val offline = caching.articles(after = null)

        assertTrue("expected the cached page, got $offline", offline is ArticlesResult.Loaded)
        assertEquals(listOf("1"), (offline as ArticlesResult.Loaded).articles.map { it.id.value })
    }

    @Test
    fun `a page that will not load with nothing cached still fails`() = runTest {
        val caching = caching(CountingArticles(ArticlesResult.Failed(FeedFailure.Offline())))

        val result = caching.articles(after = null)

        assertTrue("expected a failure, got $result", result is ArticlesResult.Failed)
    }

    @Test
    fun `a page whose rows were all unreadable does not come back as an empty one`() = runTest {
        val cache = FakeCache()
        val network = CountingArticles(ArticlesResult.Loaded(emptyList(), next = null, dropped = 3))

        caching(network, cache).articles(after = null)
        now = noon.plusSeconds(60)
        // The stored page is only reached through a failure now, which is the
        // only way it is ever reached at all.
        val offline = CountingArticles(ArticlesResult.Failed(FeedFailure.Offline()))
        val second = caching(offline, cache).articles(after = null)

        assertEquals(
            "an unreadable page read back as empty is a lie the cache invented",
            3,
            (second as ArticlesResult.Loaded).dropped,
        )
    }

    @Test
    fun `pages after the first are never served from the cache`() = runTest {
        val network = CountingArticles(
            ArticlesResult.Loaded(listOf(article(2)), next = null),
            ArticlesResult.Loaded(listOf(article(3)), next = null),
        )
        val caching = caching(network)

        caching.articles(after = PageCursor("https://api.spaceflightnewsapi.net/v4/articles/?offset=20"))
        caching.articles(after = PageCursor("https://api.spaceflightnewsapi.net/v4/articles/?offset=20"))

        assertEquals("a continuation is not the top of the list", 2, network.calls)
    }

    @Test
    fun `an article on the cached page opens when the network will not`() = runTest {
        val cache = FakeCache()
        val caching = caching(
            CountingArticles(ArticlesResult.Loaded(listOf(article(1), article(2)), next = null)),
            cache,
        )
        caching.articles(after = null)

        val offline = caching(
            CountingArticles(articleAnswer = ArticleResult.Failed(FeedFailure.Offline())),
            cache,
        ).article(ArticleId("2"))

        assertEquals(
            "it is on the page the reader is looking at; refusing to open it is absurd",
            "2",
            (offline as ArticleResult.Loaded).article.id.value,
        )
    }

    @Test
    fun `an article nobody has a copy of still fails when the network does`() = runTest {
        val offline = caching(
            CountingArticles(articleAnswer = ArticleResult.Failed(FeedFailure.Offline())),
        ).article(ArticleId("9"))

        assertTrue("expected a failure, got $offline", offline is ArticleResult.Failed)
    }

    @Test
    fun `an article the server says is gone stays gone, cache or no cache`() = runTest {
        val cache = FakeCache()
        val caching = caching(
            CountingArticles(ArticlesResult.Loaded(listOf(article(1)), next = null)),
            cache,
        )
        caching.articles(after = null)

        val missing = caching(
            CountingArticles(articleAnswer = ArticleResult.Failed(FeedFailure.Server(404))),
            cache,
        ).article(ArticleId("1"))

        assertTrue(
            "a copy in a cache is not a reason to contradict the server, got $missing",
            missing is ArticleResult.Failed,
        )
    }

    @Test
    fun `a fetched page is written down for the next run`() = runTest {
        val cache = FakeCache()
        caching(CountingArticles(ArticlesResult.Loaded(listOf(article(1)), next = null)), cache)
            .articles(after = null)

        assertEquals(listOf("1"), cache.stored?.articles?.map { it.id.value })
    }

    private fun article(id: Int) = ArticleItem(
        id = ArticleId("$id"),
        title = "Article $id",
        summary = "",
        source = "NASA",
        url = "https://example.com/$id",
        imageUrl = null,
        publishedAt = Instant.parse("2026-09-01T10:00:00Z"),
    )

    private class CountingArticles(
        vararg answers: ArticlesResult,
        private val articleAnswer: ArticleResult? = null,
    ) : ArticleRepository {
        private val queue = ArrayDeque(answers.toList())
        var calls = 0

        override suspend fun articles(after: PageCursor?): ArticlesResult {
            calls++
            return queue.removeFirstOrNull() ?: error("the feed asked for a page nobody prepared")
        }

        override suspend fun article(id: ArticleId): ArticleResult =
            articleAnswer ?: error("nobody prepared an answer about one article")
    }

    private class FakeCache : ArticleCache {
        var stored: CachedArticles? = null
        override suspend fun read(): CachedArticles? = stored
        override suspend fun write(articles: CachedArticles) {
            stored = articles
        }
    }
}
