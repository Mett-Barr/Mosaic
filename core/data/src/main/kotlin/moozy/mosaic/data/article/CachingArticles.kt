package moozy.mosaic.data.article

import java.time.Instant
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.ArticleResult
import moozy.mosaic.domain.model.ArticlesResult
import moozy.mosaic.domain.model.Cadence
import moozy.mosaic.domain.model.Clock
import moozy.mosaic.domain.model.DataCost
import moozy.mosaic.domain.model.Freshness
import moozy.mosaic.domain.model.PageCursor
import moozy.mosaic.domain.repository.ArticleRepository

/** The top of the article list as it was the last time anybody asked for it. */
internal data class CachedArticles(
    val articles: List<ArticleItem>,
    val next: PageCursor?,
    val fetchedAt: Instant,
)

internal interface ArticleCache {
    suspend fun read(): CachedArticles?
    suspend fun write(articles: CachedArticles)
}

/**
 * The half of the freshness policy that saves anything.
 *
 * Only the top of the list is cached. That is what opening the app asks for, so
 * it is the request worth not making; a continuation is a question about what
 * comes after something the reader is already holding, and answering it from an
 * hour ago produces a different list rather than the next page of this one.
 *
 * When the network fails and there is a page here, the page wins. It is the same
 * promise the saved articles make, one layer up: something the reader can read
 * beats an apology.
 */
internal class CachingArticles(
    private val network: ArticleRepository,
    private val cache: ArticleCache,
    private val clock: Clock,
    private val dataCost: DataCost,
    private val freshness: Freshness = Cadence.ARTICLES,
) : ArticleRepository {

    override suspend fun articles(after: PageCursor?): ArticlesResult {
        if (after != null) return network.articles(after)

        val cached = cache.read()
        if (cached != null && !freshness.isStale(cached.fetchedAt, clock.now(), dataCost.isMetered())) {
            return cached.asResult()
        }

        return when (val fresh = network.articles(after = null)) {
            is ArticlesResult.Loaded -> {
                cache.write(
                    CachedArticles(
                        articles = fresh.articles,
                        next = fresh.next,
                        fetchedAt = clock.now(),
                    ),
                )
                fresh
            }

            is ArticlesResult.Failed -> cached?.asResult() ?: fresh
        }
    }

    override suspend fun article(id: ArticleId): ArticleResult = network.article(id)

    private fun CachedArticles.asResult() = ArticlesResult.Loaded(articles = articles, next = next)
}
