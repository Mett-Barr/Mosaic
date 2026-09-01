package moozy.mosaic.feature.feed

import androidx.paging.PagingSource
import androidx.paging.PagingState
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.ArticlesResult
import moozy.mosaic.domain.model.PageCursor
import moozy.mosaic.domain.repository.ArticleRepository

/**
 * One generation of the feed.
 *
 * The key is the cursor the source hands back, not a number worked out here. An
 * offset computed by this app is a guess about a list that moves: measured, the
 * same cutoff's count grew by one within a day, which means an article can be
 * inserted between two requests and push everything after it along by one.
 *
 * Nothing here knows where a page came from. Whether the top of the list was on
 * a disk or had to be fetched is [articles]' business; this only ever asks for
 * one, and only ever with the cursor the previous one gave.
 */
internal class ArticlePagingSource(
    private val articles: ArticleRepository,
) : PagingSource<PageCursor, ArticleItem>() {

    /**
     * What this generation has already handed to the screen.
     *
     * Here rather than in the view model because a generation is exactly one of
     * these objects: the set is born with it and thrown away with it, so there
     * is nothing to clear and no question about whether it is still describing
     * the list on screen.
     *
     * Industry practice for an offset API somebody else owns, and it is worth
     * saying what it does not fix: an article removed from the list before the
     * page boundary shifts everything forward instead of back, and that one is
     * missed silently. No client-side answer to that exists.
     */
    private val alreadyGiven = mutableSetOf<ArticleId>()

    override suspend fun load(params: LoadParams<PageCursor>): LoadResult<PageCursor, ArticleItem> {
        val key = params.key
        val answer = if (key == null) articles.firstPage() else articles.nextPage(key)
        return when (answer) {
            is ArticlesResult.Loaded -> {
                val fresh = answer.articles.filterNot { it.id in alreadyGiven }
                alreadyGiven += fresh.map { it.id }
                LoadResult.Page(
                    data = fresh,
                    // Nothing comes before the top of the list, and there is no
                    // way back up: the source only ever says what is next.
                    prevKey = null,
                    nextKey = answer.next,
                )
            }

            is ArticlesResult.Failed -> LoadResult.Error(FeedRefused(answer.reason))
        }
    }

    /**
     * Always the top.
     *
     * The usual implementation resumes from wherever the reader was, which is
     * wrong here twice over: there is no cursor that means "just before this
     * one", and a new generation is a new list rather than a continuation.
     */
    override fun getRefreshKey(state: PagingState<PageCursor, ArticleItem>): PageCursor? = null
}
