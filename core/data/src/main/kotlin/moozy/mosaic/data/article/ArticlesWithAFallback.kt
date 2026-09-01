package moozy.mosaic.data.article

import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.ArticleResult
import moozy.mosaic.domain.model.ArticlesResult
import moozy.mosaic.domain.model.FeedFailure
import moozy.mosaic.domain.model.PageCursor
import moozy.mosaic.domain.repository.ArticleRepository

/** The top of the article list as it was the last time anybody asked for it. */
internal data class CachedArticles(
    val articles: List<ArticleItem>,
    val next: PageCursor?,
    /**
     * How many rows the page lost. Kept because a page of nothing but unusable
     * rows is an error, and one written down without this reads back as a page
     * with nothing in it -- which is a different screen and a lie.
     */
    val dropped: Int = 0,
)

internal interface ArticleCache {
    suspend fun read(): CachedArticles?
    suspend fun write(articles: CachedArticles)
}

/**
 * What the feed shows when the request for it fails.
 *
 * Not a freshness policy. Every ask for a first page goes to the network,
 * because the only two things that ask are the app starting and the reader
 * pulling the list down, and neither of those is waste. What is written down
 * is what the reader sees when the network cannot answer -- the same promise
 * the saved articles make, one layer up: something readable beats an apology.
 *
 * Only the top of the list is kept. A continuation is a question about what
 * comes after something the reader is already holding, and an old answer to it
 * is a different list rather than the next page of this one.
 */
internal class ArticlesWithAFallback(
    private val network: ArticleRepository,
    private val cache: ArticleCache,
) : ArticleRepository {

    override suspend fun articles(after: PageCursor?): ArticlesResult {
        if (after != null) return network.articles(after)

        return when (val fresh = network.articles(after = null)) {
            is ArticlesResult.Loaded -> {
                cache.write(
                    CachedArticles(
                        articles = fresh.articles,
                        next = fresh.next,
                        dropped = fresh.dropped,
                    ),
                )
                fresh
            }

            is ArticlesResult.Failed -> cache.read()?.asResult() ?: fresh
        }
    }

    /**
     * The network first, because a single article is small and the reader has
     * asked for this one. What the cache adds is the case where the request
     * cannot happen at all: the page they are looking at came out of a file, and
     * refusing to open something already on the device is absurd.
     *
     * Only a transport failure falls back. A server that says the article is gone
     * is stating a fact about the world, and a copy in a cache is not a reason to
     * contradict it.
     */
    override suspend fun article(id: ArticleId): ArticleResult =
        when (val fresh = network.article(id)) {
            is ArticleResult.Loaded -> fresh
            is ArticleResult.Failed -> if (fresh.reason.isTransport()) {
                cachedArticle(id)?.let(ArticleResult::Loaded) ?: fresh
            } else {
                fresh
            }
        }

    private suspend fun cachedArticle(id: ArticleId) =
        cache.read()?.articles?.firstOrNull { it.id == id }

    private fun FeedFailure.isTransport() = this is FeedFailure.Offline || this is FeedFailure.Timeout

    private fun CachedArticles.asResult() =
        ArticlesResult.Loaded(articles = articles, next = next, dropped = dropped)
}
