package moozy.mosaic.data.article

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
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
 * The top of the list, from a file when there is one and from the source
 * otherwise -- and a newer one on the way whenever the file answered.
 *
 * Not a freshness policy: there is no window, and nothing is ever shown instead
 * of asking. What the file buys is the moment after a cold start, when the
 * process has been killed and the reader is looking at a screen that would
 * otherwise be empty until a request comes back.
 *
 * Only the top of the list is kept. A continuation is a question about what
 * comes after something the reader is already holding, and an old answer to it
 * is a different list rather than the next page of this one.
 *
 * [scope] outlives every screen because the errand it runs does: a refresh
 * started by one reader must not be cancelled because they walked away from the
 * screen a second later. The request has already been paid for.
 */
internal class ArticlesWithAFallback(
    private val network: ArticleRepository,
    private val cache: ArticleCache,
    private val scope: CoroutineScope,
) : ArticleRepository {

    private val replacements = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val changed: Flow<Unit> = replacements

    /** Whether what is held came from the source rather than off the disk. */
    private var holdingTheNewest = false

    override suspend fun firstPage(): ArticlesResult {
        val stored = cache.read()
        if (stored == null || holdingTheNewest) {
            // Nothing to show, or what is shown is already the newest there is.
            // Either way the answer is whatever the source says, waited for.
            return fetch() ?: stored?.asResult() ?: ArticlesResult.Failed(FeedFailure.Offline())
        }
        // Something to show, and it came off a disk. Show it, and go and look.
        scope.launch { refresh() }
        return stored.asResult()
    }

    override suspend fun nextPage(after: PageCursor): ArticlesResult = network.nextPage(after)

    override suspend fun refresh() {
        val hadOne = cache.read() != null
        if (fetch() != null && hadOne) {
            // Only when it replaced one somebody could be looking at. Announcing
            // the first arrival would ask the screen to redraw what it is
            // already drawing.
            replacements.tryEmit(Unit)
        }
    }

    /** The source, written down and remembered as the newest thing we have. */
    private suspend fun fetch(): ArticlesResult.Loaded? =
        when (val fresh = network.firstPage()) {
            is ArticlesResult.Loaded -> {
                cache.write(
                    CachedArticles(
                        articles = fresh.articles,
                        next = fresh.next,
                        dropped = fresh.dropped,
                    ),
                )
                holdingTheNewest = true
                fresh
            }

            is ArticlesResult.Failed -> null
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
