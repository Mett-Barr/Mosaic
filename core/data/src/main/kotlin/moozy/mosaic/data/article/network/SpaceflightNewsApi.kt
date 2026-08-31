package moozy.mosaic.data.article.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import moozy.mosaic.domain.model.ArticleItem

/**
 * One window of the article list: what it contained, where the server says the
 * next window is, and what the page lost on the way in.
 *
 * [next] is the server's own link rather than an offset this app worked out. The
 * list moves -- articles are published while someone is reading -- and a client
 * that computes `offset += 20` will show the twentieth article twice, or never.
 * Following the link the server handed back does not make the list stand still,
 * but it does stop this app from being the thing that gets it wrong.
 */
internal data class ArticlePage(
    val articles: List<ArticleItem>,
    val next: String?,
    val droppedReasons: List<String>,
) {
    val hasMore: Boolean get() = next != null
}

/**
 * The engine is a parameter rather than something this module picks, so a test can
 * hand over a MockEngine and the app can hand over one that knows about the
 * platform's connection pool, without either having to know the other exists.
 *
 * `expectSuccess` is on because the alternative is worse than an exception: a 500
 * whose body does not parse surfaces as a complaint about response types, which
 * is a stranger thing to hand a caller than the error itself.
 *
 * The timeouts are not about saving data -- they cost nothing to have -- but a
 * request that never returns leaves the reader on a spinner forever, and a
 * spinner is not one of the four states this app promises to handle.
 */
internal fun spaceflightNewsClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = true
    install(ContentNegotiation) { json(SpaceflightNewsJson) }
    install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
        socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
    }
}

/**
 * Reads windows of the article list.
 *
 * The first window is asked for by size; every window after it is asked for by
 * following [ArticlePage.next], which is why [articles] takes a link rather than
 * an offset.
 */
internal class SpaceflightNewsApi(private val client: HttpClient) {

    suspend fun articles(limit: Int, after: String? = null): ArticlePage {
        val page: ArticlePageDto = client.get(after ?: ARTICLES_URL) {
            // The server's link already carries the window it means; adding our
            // own parameters to it would be second-guessing it.
            if (after == null) parameter("limit", limit)
        }.body()
        val mapped = page.toArticles()
        return ArticlePage(
            articles = mapped.articles,
            next = page.next?.takeIf { it.continuesTheArticleList() },
            droppedReasons = mapped.droppedReasons,
        )
    }

    /**
     * A link that arrives in a response is input, not instruction. Only a link
     * back into the same collection on the same host is followed; anything else
     * ends the list rather than being somewhere to go next.
     *
     * The whole prefix is matched, scheme and host and path together, which is
     * what makes https://api.spaceflightnewsapi.net.example.com/ fail the check
     * -- a host comparison that stopped at the dot would not.
     */
    private fun String.continuesTheArticleList(): Boolean = startsWith(ARTICLES_URL)

    private companion object {
        const val API_HOST = "api.spaceflightnewsapi.net"
        const val ARTICLES_URL = "https://$API_HOST/v4/articles/"
    }
}

private const val REQUEST_TIMEOUT_MILLIS = 20_000L
private const val CONNECT_TIMEOUT_MILLIS = 10_000L
private const val SOCKET_TIMEOUT_MILLIS = 20_000L
