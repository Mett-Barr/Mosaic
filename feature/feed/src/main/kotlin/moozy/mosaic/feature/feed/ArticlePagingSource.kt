package moozy.mosaic.feature.feed

import androidx.paging.PagingSource
import androidx.paging.PagingState
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.ArticlesResult
import moozy.mosaic.domain.model.FeedFailure
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
        // Null asks for the top of the list, which is exactly what Paging
        // means by a refresh with no key. Nothing here has to translate.
        return when (val answer = articles.articles(after = params.key)) {
            is ArticlesResult.Loaded -> if (answer.wasUnreadable()) {
                // The count is the whole of the diagnosis: it says the page was
                // not empty, it was unusable, which is what nobody could see
                // from an empty list.
                val detail = "A page of ${answer.dropped} rows arrived and none of them could be read."
                LoadResult.Error(FeedRefused(FeedFailure.Unreadable(detail)))
            } else {
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
     * A page that arrived and could not be used at all.
     *
     * The question is asked of [ArticlesResult.Loaded.articles] -- what the
     * server sent -- and deliberately not of the list left after this generation
     * removes what it has already given out. The two look identical from here,
     * both empty, and they mean opposite things: a page emptied by
     * de-duplication is a reader reaching the end of a list, and turning that
     * into an error screen would put a failure in front of the most ordinary
     * thing the feed does.
     *
     * Rows this app could not read are not a partial failure worth interrupting
     * anybody over -- some arriving is a page -- so only losing all of them
     * counts, and only when there were some to lose.
     */
    private fun ArticlesResult.Loaded.wasUnreadable(): Boolean = articles.isEmpty() && dropped > 0

    /**
     * Always the top.
     *
     * The usual implementation resumes from wherever the reader was, which is
     * wrong here twice over: there is no cursor that means "just before this
     * one", and a new generation is a new list rather than a continuation.
     */
    override fun getRefreshKey(state: PagingState<PageCursor, ArticleItem>): PageCursor? = null
}
