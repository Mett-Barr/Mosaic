package moozy.mosaic.data.article

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.test.runTest
import moozy.mosaic.data.article.network.SpaceflightNewsApi
import moozy.mosaic.data.article.network.spaceflightNewsClient
import moozy.mosaic.domain.model.ArticlesResult
import moozy.mosaic.domain.model.FeedFailure
import moozy.mosaic.domain.model.PageCursor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The assignment asks for loading, empty, error and offline to be four things a
 * reader can tell apart. That is only possible if something turns "this phone has
 * no network" and "the server broke" into two different answers, and this is the
 * last layer where the difference still exists: one floor up they are both just a
 * screen with nothing on it.
 */
class NetworkArticleRepositoryTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun repositoryWith(engine: MockEngine) =
        NetworkArticleRepository(SpaceflightNewsApi(spaceflightNewsClient(engine)))

    private fun repositoryReturning(body: String) = repositoryWith(
        MockEngine { request ->
            requests += request
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    )

    private fun page(nextJson: String, rows: String) =
        """{"count": 100, "next": $nextJson, "results": [$rows]}"""

    private fun row(id: Int) =
        """{"id": $id, "title": "Article $id", "url": "https://example.com/$id",
            "news_site": "Somewhere", "published_at": "2026-08-31T10:00:00Z"}"""

    @Test
    fun `it hands back the articles and where to continue from`() = runTest {
        val link = "https://api.spaceflightnewsapi.net/v4/articles/?limit=20&offset=20"

        val result = repositoryReturning(page("\"$link\"", row(1))).articles(after = null)

        val loaded = assertLoaded(result)
        assertEquals("1", loaded.articles.single().id.value)
        assertEquals(PageCursor(link), loaded.next)
    }

    @Test
    fun `the last page comes back with nowhere to continue from`() = runTest {
        val result = repositoryReturning(page("null", row(1))).articles(after = null)

        assertNull(assertLoaded(result).next)
    }

    @Test
    fun `the cursor it was handed is the one it continues from`() = runTest {
        val link = "https://api.spaceflightnewsapi.net/v4/articles/?limit=20&offset=40"

        repositoryReturning(page("null", row(2))).articles(after = PageCursor(link))

        val asked = requests.single().url
        assertEquals("api.spaceflightnewsapi.net", asked.host)
        assertEquals("/v4/articles/", asked.encodedPath)
        assertEquals("40", asked.parameters["offset"])
        assertEquals("20", asked.parameters["limit"])
    }

    @Test
    fun `a server that failed is not the same answer as a phone with no network`() = runTest {
        val server = repositoryWith(MockEngine { respondError(HttpStatusCode.InternalServerError) })
            .articles(after = null)
        val offline = repositoryWith(MockEngine { throw UnknownHostException("no dns") })
            .articles(after = null)

        val serverFailure = assertFailed(server)
        assertTrue("expected a server failure, got $serverFailure", serverFailure is FeedFailure.Server)
        assertEquals(
            HttpStatusCode.InternalServerError.value,
            (serverFailure as FeedFailure.Server).status,
        )
        assertTrue("expected offline, got ${assertFailed(offline)}", assertFailed(offline) is FeedFailure.Offline)
    }

    @Test
    fun `a request that ran out of time says so rather than passing as offline`() = runTest {
        val result = repositoryWith(MockEngine { throw SocketTimeoutException("too slow") })
            .articles(after = null)

        assertTrue("expected a timeout, got ${assertFailed(result)}", assertFailed(result) is FeedFailure.Timeout)
    }

    @Test
    fun `a response it cannot read is its own kind of failure`() = runTest {
        val result = repositoryWith(
            MockEngine {
                respond(
                    "<html>not json at all</html>",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ).articles(after = null)

        assertTrue("expected unreadable, got ${assertFailed(result)}", assertFailed(result) is FeedFailure.Unreadable)
    }

    @Test
    fun `a page whose rows were all unusable is not the same as an empty page`() = runTest {
        val unusable = """{"id": 1, "title": "  ", "url": "https://example.com/1",
            "news_site": "Somewhere", "published_at": "2026-08-31T10:00:00Z"}"""

        val result = repositoryReturning(page("null", unusable)).articles(after = null)

        val loaded = assertLoaded(result)
        assertTrue("expected nothing usable, got ${loaded.articles}", loaded.articles.isEmpty())
        assertEquals(1, loaded.dropped)
    }

    @Test
    fun `a failure nobody anticipated is still an answer`() = runTest {
        val result = repositoryWith(MockEngine { throw IllegalStateException("who knows") })
            .articles(after = null)

        assertTrue("expected unexpected, got ${assertFailed(result)}", assertFailed(result) is FeedFailure.Unexpected)
    }

    @Test
    fun `this is where failures stop being thrown`() = runTest {
        val thrown = runCatching {
            repositoryWith(MockEngine { throw IOException("connection reset") }).articles(after = null)
        }.exceptionOrNull()

        assertNull("the caller should be told, not interrupted", thrown)
    }

    private fun assertLoaded(result: ArticlesResult): ArticlesResult.Loaded {
        assertTrue("expected articles, got $result", result is ArticlesResult.Loaded)
        return result as ArticlesResult.Loaded
    }

    private fun assertFailed(result: ArticlesResult): FeedFailure {
        assertTrue("expected a failure, got $result", result is ArticlesResult.Failed)
        return (result as ArticlesResult.Failed).reason
    }
}
