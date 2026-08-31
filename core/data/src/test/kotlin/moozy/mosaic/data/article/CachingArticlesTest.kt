package moozy.mosaic.data.article

import java.time.Instant
import kotlinx.coroutines.test.runTest
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.ArticleResult
import moozy.mosaic.domain.model.ArticlesResult
import moozy.mosaic.domain.model.Cadence
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
class CachingArticlesTest {

    private val noon: Instant = Instant.parse("2026-09-01T12:00:00Z")
    private var now: Instant = noon
    private var metered = false

    private fun caching(network: ArticleRepository, cache: FakeCache = FakeCache()) =
        CachingArticles(
            network = network,
            cache = cache,
            clock = { now },
            dataCost = { metered },
            freshness = Cadence.ARTICLES,
        )

    @Test
    fun `a page fetched a moment ago is served without asking again`() = runTest {
        val network = CountingArticles(ArticlesResult.Loaded(listOf(article(1)), next = null))
        val caching = caching(network)

        caching.articles(after = null)
        now = noon.plusSeconds(60)
        val second = caching.articles(after = null)

        assertEquals(1, network.calls)
        assertEquals(listOf("1"), (second as ArticlesResult.Loaded).articles.map { it.id.value })
    }

    @Test
    fun `a page older than the window is asked about again`() = runTest {
        val network = CountingArticles(
            ArticlesResult.Loaded(listOf(article(1)), next = null),
            ArticlesResult.Loaded(listOf(article(2)), next = null),
        )
        val caching = caching(network)

        caching.articles(after = null)
        now = noon.plusSeconds(16 * 60)
        val second = caching.articles(after = null)

        assertEquals(2, network.calls)
        assertEquals(listOf("2"), (second as ArticlesResult.Loaded).articles.map { it.id.value })
    }

    @Test
    fun `the same page on mobile data is not asked about again`() = runTest {
        val network = CountingArticles(
            ArticlesResult.Loaded(listOf(article(1)), next = null),
            ArticlesResult.Loaded(listOf(article(2)), next = null),
        )
        val caching = caching(network)

        caching.articles(after = null)
        metered = true
        now = noon.plusSeconds(16 * 60)
        caching.articles(after = null)

        assertEquals("the reader is paying for this one", 1, network.calls)
    }

    @Test
    fun `a reader who asks for it again gets a request, cache or no cache`() = runTest {
        val network = CountingArticles(
            ArticlesResult.Loaded(listOf(article(1)), next = null),
            ArticlesResult.Loaded(listOf(article(2)), next = null),
        )
        val caching = caching(network)

        caching.articles(after = null)
        now = noon.plusSeconds(60)
        val asked = caching.articles(after = null, force = true)

        assertEquals("a policy is for the app to follow, not to overrule the reader", 2, network.calls)
        assertEquals(listOf("2"), (asked as ArticlesResult.Loaded).articles.map { it.id.value })
    }

    @Test
    fun `asking again on mobile data is still the reader's decision`() = runTest {
        val network = CountingArticles(
            ArticlesResult.Loaded(listOf(article(1)), next = null),
            ArticlesResult.Loaded(listOf(article(2)), next = null),
        )
        val caching = caching(network)

        caching.articles(after = null)
        metered = true
        caching.articles(after = null, force = true)

        assertEquals("they were told it costs, and asked anyway", 2, network.calls)
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
        val second = caching(CountingArticles(), cache).articles(after = null)

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
    fun `a fetched page is written down for the next run`() = runTest {
        val cache = FakeCache()
        caching(CountingArticles(ArticlesResult.Loaded(listOf(article(1)), next = null)), cache)
            .articles(after = null)

        assertEquals(listOf("1"), cache.stored?.articles?.map { it.id.value })
        assertEquals(noon, cache.stored?.fetchedAt)
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

    private class CountingArticles(vararg answers: ArticlesResult) : ArticleRepository {
        private val queue = ArrayDeque(answers.toList())
        var calls = 0

        override suspend fun articles(after: PageCursor?): ArticlesResult {
            calls++
            return queue.removeFirstOrNull() ?: error("the feed asked for a page nobody prepared")
        }

        override suspend fun article(id: ArticleId): ArticleResult =
            error("the caching layer should not be asking for single articles")
    }

    private class FakeCache : ArticleCache {
        var stored: CachedArticles? = null
        override suspend fun read(): CachedArticles? = stored
        override suspend fun write(articles: CachedArticles) {
            stored = articles
        }
    }
}
