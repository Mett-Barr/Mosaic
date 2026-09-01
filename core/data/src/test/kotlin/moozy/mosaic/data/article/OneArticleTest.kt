package moozy.mosaic.data.article

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.time.Instant
import kotlinx.coroutines.test.runTest
import moozy.mosaic.data.article.network.SpaceflightNewsApi
import moozy.mosaic.data.article.network.spaceflightNewsClient
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleResult
import moozy.mosaic.domain.model.Clock
import moozy.mosaic.domain.model.FeedFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A reader who opened an article and cannot be shown it needs to know which of the
 * two things happened: the article is gone, or the phone is. The first is a dead
 * end; the second is worth waiting out.
 */
class OneArticleTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun repositoryWith(engine: MockEngine) =
        NetworkArticleRepository(SpaceflightNewsApi(spaceflightNewsClient(engine), Clock { NOW }))

    private fun repositoryReturning(body: String) = repositoryWith(
        MockEngine { request ->
            requests += request
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    )

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

    private companion object {
        /** The window these tests read is pinned here; none of them care when. */
        val NOW: Instant = Instant.parse("2026-09-01T09:00:00Z")
    }
}
