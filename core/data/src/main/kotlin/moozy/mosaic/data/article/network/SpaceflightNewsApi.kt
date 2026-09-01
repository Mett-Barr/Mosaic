package moozy.mosaic.data.article.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.json.JsonElement
import io.ktor.serialization.kotlinx.json.json
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.Clock

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
 * The first window is asked for by size and by a moment; every window after it is
 * asked for by following [ArticlePage.next], which is why [articles] takes a link
 * rather than an offset.
 *
 * The moment is what makes the offsets in those links mean anything. Articles are
 * published while somebody reads, and an unfiltered list shifts down under them:
 * the link that said "start at 20" was written about a list that no longer has
 * the same thing at 20. `published_at_lte` freezes what is being counted, and the
 * server carries the filter into every link it generates from there.
 */
internal class SpaceflightNewsApi(
    private val client: HttpClient,
    private val clock: Clock,
) {

    suspend fun articles(limit: Int, after: String? = null): ArticlePage {
        // A cursor handed in gets the same look as one handed back: it may have
        // come from a cache, a saved state or a caller's mistake, and only the
        // response it came from is any reason to trust it.
        val target = after?.takeIf { it.continuesTheArticleList() }
        val page: ArticlePageDto = client.get(target ?: ARTICLES_URL) {
            // The server's link already carries the window it means -- including
            // the moment it was pinned to -- so adding our own parameters to it
            // would be second-guessing it.
            if (target == null) {
                parameter("limit", limit)
                parameter("published_at_lte", clock.now())
            }
        }.body()
        val mapped = page.toArticles()
        return ArticlePage(
            articles = mapped.articles,
            next = page.next?.takeIf { it.continuesTheArticleList() },
            droppedReasons = mapped.droppedReasons,
        )
    }

    /**
     * One article by id.
     *
     * The row goes through the same mapping as a page's rows: a single article
     * that the domain would refuse is a dropped row here too, and the caller finds
     * out the same way -- nothing came back rather than something unusable did.
     */
    suspend fun article(id: String): MappedArticles {
        val row: JsonElement = client.get("$ARTICLES_URL$id/").body()
        return ArticlePageDto(results = listOf(row)).toArticles()
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
