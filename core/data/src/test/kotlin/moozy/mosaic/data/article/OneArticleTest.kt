package moozy.mosaic.data.article

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.net.UnknownHostException
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import moozy.mosaic.data.article.network.SpaceflightNewsApi
import moozy.mosaic.data.article.network.spaceflightNewsClient
import moozy.mosaic.data.saved.SavedArticleDao
import moozy.mosaic.data.saved.SavedArticleEntity
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleResult
import moozy.mosaic.domain.model.ArticlesResult
import moozy.mosaic.domain.model.Clock
import moozy.mosaic.domain.model.FeedFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A reader who opened an article and cannot be shown it needs to know which of the
 * two things happened: the article is gone, or the phone is. The first is a dead
 * end; the second is worth waiting out.
 *
 * One article has three sources, and which one answers is decided here rather than
 * on the screen. That is the whole subject of the second half of this file: an
 * article the reader kept is theirs already, and asking the network for it again
 * spends a request and a spinner on the one article they said they wanted
 * available without one -- and an article the feed has just drawn a card from is
 * in hand too, for as long as that card is on screen.
 */
class OneArticleTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun repositoryWith(engine: MockEngine, kept: SavedArticleDao = KeptRows()) =
        SavedFirstArticleRepository(
            api = SpaceflightNewsApi(spaceflightNewsClient(engine), Clock { NOW }),
            kept = kept,
        )

    private fun repositoryReturning(body: String, kept: SavedArticleDao = KeptRows()) = repositoryWith(
        MockEngine { request ->
            requests += request
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        },
        kept,
    )

    /**
     * A source that can be asked both questions: pages come back in the order they
     * were handed in, and anything asked for by id gets [one]. Its headline differs
     * from every page row below, so an answer says which source produced it.
     */
    private fun repositoryServing(
        vararg pages: String,
        one: String = article,
        kept: SavedArticleDao = KeptRows(),
    ): SavedFirstArticleRepository {
        val remaining = ArrayDeque(pages.toList())
        return repositoryWith(
            MockEngine { request ->
                requests += request
                val body =
                    if (request.url.encodedPath == "/v4/articles/") remaining.removeFirst() else one
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
            kept,
        )
    }

    private fun page(rows: String, next: String? = null) =
        """{"count": 100, "next": ${next?.let { "\"$it\"" } ?: "null"}, "results": [$rows]}"""

    private fun pageRow(id: Int, title: String) = """
        {"id": $id, "title": "$title", "url": "https://example.com/$id",
         "news_site": "The Verge", "summary": "A summary.", "image_url": null,
         "published_at": "2026-08-31T10:00:00Z"}
    """.trimIndent()

    private val article = """
        {"id": 39742, "title": "Roman Commissioning", "url": "https://science.nasa.gov/roman/",
         "news_site": "NASA", "summary": "Where is Roman?", "image_url": "https://img/roman.jpg",
         "published_at": "2026-08-31T12:16:53Z"}
    """.trimIndent()

    @Test
    fun `it asks for the one article by its id`() = runTest {
        repositoryReturning(article).article(ArticleId("39742"))

        assertEquals("/v4/articles/39742/", requests.single().url.encodedPath)
    }

    @Test
    fun `the article comes back whole`() = runTest {
        val result = repositoryReturning(article).article(ArticleId("39742"))

        assertTrue("expected an article, got $result", result is ArticleResult.Loaded)
        val loaded = (result as ArticleResult.Loaded).article
        assertEquals("Roman Commissioning", loaded.title)
        assertEquals("NASA", loaded.source)
        assertEquals("https://img/roman.jpg", loaded.imageUrl)
    }

    @Test
    fun `an article that is not there is missing, not a server that misbehaved`() = runTest {
        val result = repositoryWith(MockEngine { respondError(HttpStatusCode.NotFound) })
            .article(ArticleId("1"))

        // The screen has to decide whether to offer a retry, and it should not
        // have to know what 404 means to do that. "Gone" is the answer; the
        // status code is how this layer found out.
        assertTrue("expected a failure, got $result", result is ArticleResult.Failed)
        assertTrue(
            "expected it to say the article is gone, got ${(result as ArticleResult.Failed).reason}",
            result.reason is FeedFailure.Missing,
        )
    }

    @Test
    fun `a server that broke is still a server that broke`() = runTest {
        val result = repositoryWith(MockEngine { respondError(HttpStatusCode.InternalServerError) })
            .article(ArticleId("1"))

        val reason = (result as ArticleResult.Failed).reason
        assertTrue("expected a server failure, got $reason", reason is FeedFailure.Server)
        assertEquals(HttpStatusCode.InternalServerError.value, (reason as FeedFailure.Server).status)
    }

    @Test
    fun `an article the response mangled is unreadable, not missing`() = runTest {
        val result = repositoryReturning("""{"id": 1, "title": "  ", "url": "https://x/1",
            "news_site": "Nowhere", "published_at": "2026-08-31T10:00:00Z"}""")
            .article(ArticleId("1"))

        assertTrue("expected a failure, got $result", result is ArticleResult.Failed)
        assertTrue((result as ArticleResult.Failed).reason is FeedFailure.Unreadable)
    }

    @Test
    fun `an article the reader kept is read from the phone, not asked for again`() = runTest {
        // The engine would answer, and with a different headline, so an article
        // that did come off the network could not pass for the kept copy.
        val repository = repositoryReturning(article, kept = KeptRows(row("39742", "The copy they kept")))

        val result = repository.article(ArticleId("39742"))

        assertTrue("expected the kept copy, got $result", result is ArticleResult.Loaded)
        assertEquals("The copy they kept", (result as ArticleResult.Loaded).article.title)
        assertEquals("nothing should have been asked for", emptyList<HttpRequestData>(), requests)
    }

    @Test
    fun `an article nobody kept is still asked for over the network`() = runTest {
        // Something else is kept, so the answer can only be right if the table
        // was asked about this id rather than found to be empty.
        val repository = repositoryReturning(article, kept = KeptRows(row("7", "A different article")))

        val result = repository.article(ArticleId("39742"))

        assertEquals("/v4/articles/39742/", requests.single().url.encodedPath)
        assertEquals("Roman Commissioning", (result as ArticleResult.Loaded).article.title)
    }

    @Test
    fun `an article nobody kept, on a phone with no network, still says so`() = runTest {
        val result = repositoryWith(MockEngine { throw UnknownHostException("no dns") })
            .article(ArticleId("39742"))

        assertTrue("expected a failure, got $result", result is ArticleResult.Failed)
        assertTrue(
            "expected offline, got ${(result as ArticleResult.Failed).reason}",
            result.reason is FeedFailure.Offline,
        )
    }

    @Test
    fun `a kept row this app can no longer read sends it to the network instead`() = runTest {
        // A blank title is a row no writer of this table could have produced, so
        // it stands in for one that arrived some other way. It is "nothing kept
        // here" rather than a failure of its own: there is still a source left.
        val repository = repositoryReturning(article, kept = KeptRows(row("39742", title = " ")))

        val result = repository.article(ArticleId("39742"))

        assertEquals("/v4/articles/39742/", requests.single().url.encodedPath)
        assertEquals("Roman Commissioning", (result as ArticleResult.Loaded).article.title)
    }

    @Test
    fun `a local source that will not answer sends it to the network too`() = runTest {
        val repository = repositoryReturning(article, kept = RefusingRows())

        val result = repository.article(ArticleId("39742"))

        assertEquals("/v4/articles/39742/", requests.single().url.encodedPath)
        assertEquals("Roman Commissioning", (result as ArticleResult.Loaded).article.title)
    }

    /**
     * The feed drew a card from this article a moment ago, so the app has it. Asking
     * again spends a request and a spinner on something already in hand, and the
     * spinner is the half that shows: while it is up, the card the reader tapped has
     * nothing to become.
     */
    @Test
    fun `an article the feed just showed is not asked for again`() = runTest {
        val repository = repositoryServing(page(pageRow(1, "The one they tapped")))

        repository.articles(after = null)
        val result = repository.article(ArticleId("1"))

        assertTrue("expected the article, got $result", result is ArticleResult.Loaded)
        assertEquals("The one they tapped", (result as ArticleResult.Loaded).article.title)
        assertEquals("only the page should have been asked for", 1, requests.size)
    }

    @Test
    fun `an article further down the list is held the same way the first page is`() = runTest {
        val repository = repositoryServing(
            page(pageRow(1, "First page"), next = "$ARTICLES?offset=20"),
            page(pageRow(2, "Second page")),
        )

        val first = repository.articles(after = null) as ArticlesResult.Loaded
        repository.articles(after = first.next)
        val result = repository.article(ArticleId("2"))

        assertEquals("Second page", (result as ArticleResult.Loaded).article.title)
        assertEquals("only the two pages should have been asked for", 2, requests.size)
    }

    /**
     * What is held is what the list on screen is holding, and asking for the top of
     * the list is how that list starts again. Nothing outlives the generation that
     * put it there, so nothing here is a second answer to "how fresh is the feed".
     */
    @Test
    fun `asking for the top of the list again lets go of what came before it`() = runTest {
        val repository = repositoryServing(
            page(pageRow(1, "Before the refresh")),
            page(pageRow(2, "After the refresh")),
        )

        repository.articles(after = null)
        repository.articles(after = null)
        val result = repository.article(ArticleId("1"))

        assertTrue("expected the article, got $result", result is ArticleResult.Loaded)
        assertEquals("/v4/articles/1/", requests.last().url.encodedPath)
    }

    /** A deep link, or a process that was killed, still has an article to load. */
    @Test
    fun `an article the feed never showed is still asked for`() = runTest {
        val repository = repositoryServing(page(pageRow(1, "The one on screen")))

        repository.articles(after = null)
        val result = repository.article(ArticleId("39742"))

        assertEquals("/v4/articles/39742/", requests.last().url.encodedPath)
        assertEquals("Roman Commissioning", (result as ArticleResult.Loaded).article.title)
    }

    /**
     * The order of the three sources is the order DECISIONS 30 put the first two in:
     * what the reader kept is the article, and a copy the feed happens to be holding
     * does not get to overrule it.
     */
    @Test
    fun `the copy the reader kept still answers first, even where the feed showed it too`() = runTest {
        val repository = repositoryServing(
            page(pageRow(39742, "The copy the feed showed")),
            kept = KeptRows(row("39742", "The copy they kept")),
        )

        repository.articles(after = null)
        val result = repository.article(ArticleId("39742"))

        assertEquals("The copy they kept", (result as ArticleResult.Loaded).article.title)
        assertEquals("only the page should have been asked for", 1, requests.size)
    }

    private fun row(id: String, title: String) = SavedArticleEntity(
        id = id,
        title = title,
        summary = "The summary that was kept with it.",
        source = "NASA",
        url = "https://science.nasa.gov/roman/",
        imageUrl = "https://img/roman.jpg",
        publishedAt = Instant.parse("2026-08-31T12:16:53Z").toEpochMilli(),
        savedAt = 0L,
    )

    /**
     * The kept rows and nothing else. The repository only reads from here, so
     * the writes are in memory to keep the fake honest about what it stands in
     * for rather than because anything under test uses them.
     */
    private open class KeptRows(vararg rows: SavedArticleEntity) : SavedArticleDao {
        private val byId = rows.associateByTo(mutableMapOf()) { it.id }

        override fun saved(): Flow<List<SavedArticleEntity>> = flowOf(byId.values.toList())

        override suspend fun find(id: String): SavedArticleEntity? = byId[id]

        override suspend fun save(article: SavedArticleEntity) {
            byId[article.id] = article
        }

        override suspend fun saveAll(articles: List<SavedArticleEntity>) {
            articles.forEach { byId[it.id] = it }
        }

        override suspend fun forget(id: String) {
            byId.remove(id)
        }
    }

    /** A local source that cannot be asked at all. */
    private class RefusingRows : KeptRows() {
        override suspend fun find(id: String): Nothing = error("the database would not answer")
    }

    private companion object {
        /** The window these tests read is pinned here; none of them care when. */
        val NOW: Instant = Instant.parse("2026-09-01T09:00:00Z")

        /** The collection the source hands its own links back into. */
        const val ARTICLES = "https://api.spaceflightnewsapi.net/v4/articles/"
    }
}
