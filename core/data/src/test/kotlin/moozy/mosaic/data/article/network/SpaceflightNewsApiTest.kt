package moozy.mosaic.data.article.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The assignment calls this API flaky, so how a failure reaches the reader matters
 * as much as the happy path. The one thing this must never do is turn a broken
 * response into an empty page: someone shown "nothing here" cannot tell that
 * anything went wrong, and will not retry a screen that looks finished.
 */
class SpaceflightNewsApiTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun apiReturning(vararg bodies: String) = SpaceflightNewsApi(
        spaceflightNewsClient(
            MockEngine { request ->
                requests += request
                respond(
                    bodies[requests.size - 1],
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ),
    )

    private fun page(nextJson: String, rows: String) =
        """{"count": 100, "next": $nextJson, "results": [$rows]}"""

    private fun row(id: Int) =
        """{"id": $id, "title": "Article $id", "url": "https://example.com/$id",
            "news_site": "Somewhere", "published_at": "2026-08-31T10:00:00Z"}"""

    @Test
    fun `the first window is asked for by size`() = runTest {
        apiReturning(page("null", row(1))).articles(limit = 20)

        val url = requests.single().url
        assertEquals("api.spaceflightnewsapi.net", url.host)
        assertEquals("/v4/articles/", url.encodedPath)
        assertEquals("20", url.parameters["limit"])
    }

    @Test
    fun `the next window is the one the server named, not one the client worked out`() = runTest {
        val serverLink = "https://api.spaceflightnewsapi.net/v4/articles/?limit=20&offset=20"
        val api = apiReturning(page("\"$serverLink\"", row(1)), page("null", row(2)))

        val first = api.articles(limit = 20)
        assertEquals(serverLink, first.next)
        assertTrue(first.hasMore)

        api.articles(limit = 20, after = first.next)

        val followed = requests.last().url
        assertEquals("/v4/articles/", followed.encodedPath)
        assertEquals("20", followed.parameters["offset"])
        assertEquals("20", followed.parameters["limit"])
    }

    @Test
    fun `a link that leads somewhere else is the end of the list, not somewhere to go`() = runTest {
        val page = apiReturning(page("\"https://elsewhere.example.com/v4/articles/\"", row(1)))
            .articles(limit = 20)

        assertNull(page.next)
        assertFalse(page.hasMore)
    }

    @Test
    fun `a host that only looks like the API is not the API`() = runTest {
        val lookalike = "https://api.spaceflightnewsapi.net.example.com/v4/articles/?offset=20"
        val page = apiReturning(page("\"$lookalike\"", row(1))).articles(limit = 20)

        assertNull(page.next)
    }

    @Test
    fun `the last page does not claim there is more`() = runTest {
        val page = apiReturning(page("null", row(1))).articles(limit = 20)

        assertFalse(page.hasMore)
        assertEquals(1, page.articles.size)
    }

    @Test
    fun `a server error reaches the caller instead of looking like an empty feed`() = runTest {
        val api = SpaceflightNewsApi(
            spaceflightNewsClient(MockEngine { respondError(HttpStatusCode.InternalServerError) }),
        )

        val thrown = runCatching { api.articles(limit = 20) }.exceptionOrNull()

        assertTrue("expected a server error, got $thrown", thrown is ServerResponseException)
        assertEquals(
            HttpStatusCode.InternalServerError,
            (thrown as ServerResponseException).response.status,
        )
    }

    @Test
    fun `the rows a page lost travel with the ones that survived`() = runTest {
        val badRow = """{"id": 2, "title": "  ", "url": "https://example.com/2",
            "news_site": "Somewhere", "published_at": "2026-08-31T10:00:00Z"}"""

        val page = apiReturning(page("null", row(1) + "," + badRow)).articles(limit = 20)

        assertEquals("1", page.articles.single().id.value)
        assertEquals("Article 1", page.articles.single().title)
        assertTrue(
            "the reason should say which row went and why, was ${page.droppedReasons}",
            page.droppedReasons.single().contains("row 2"),
        )
    }
}
