package moozy.mosaic.domain.repository

import kotlinx.coroutines.flow.Flow
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleResult
import moozy.mosaic.domain.model.ArticlesResult
import moozy.mosaic.domain.model.PageCursor

/**
 * What the feed needs in order to show articles.
 *
 * Declared here and implemented in `:core:data`, so the dependency points the way
 * the architecture wants it to: the feature says what it needs, and the layer that
 * knows about HTTP and JSON is the one that has to comply.
 *
 * Nothing here says where an answer came from. Whether the top of the list was on
 * a disk or had to be fetched is the implementation's business, and a caller that
 * could tell the difference would end up making decisions that belong down there.
 */
interface ArticleRepository {

    /**
     * The top of the list, as it should appear now.
     *
     * Answered from whatever is already here when there is something, so that a
     * reader reopening the app sees the list they had rather than a blank screen.
     * When there is nothing, this waits for the source rather than answering with
     * an empty list -- "nothing yet" and "nothing at all" are different screens.
     *
     * Asking also sends for a newer one when what is returned did not come from
     * the source in the first place. That is one question with two consequences,
     * not two questions: nothing else knows whether what is held is already the
     * newest thing there is.
     */
    suspend fun firstPage(): ArticlesResult

    /**
     * The page after one the reader is already holding.
     *
     * The cursor is a parameter rather than something the repository remembers. A
     * repository that holds "the page I am on" has one such page for every caller,
     * and two screens asking at once would move each other's list.
     *
     * Never answered from what was written down: an old answer to "what comes
     * after this" is a different list, not the next page of this one.
     */
    suspend fun nextPage(after: PageCursor): ArticlesResult

    /**
     * Go and get a newer top of the list, because the reader asked.
     *
     * A command, where [firstPage] is a question. It is the pull-to-refresh
     * gesture and nothing else: the app starting does not call this, because
     * asking for the first page already does whatever is needed.
     */
    suspend fun refresh()

    /**
     * A newer top of the list has replaced the one that was being shown.
     *
     * Says nothing when the first page arrives where there was none, because
     * nobody is looking at an older one that needs replacing.
     */
    val changed: Flow<Unit>

    /**
     * One article, for a reader who opened it.
     *
     * It is asked for again rather than carried over from the list, because the
     * list is a screen's worth of state and a deep link has no list behind it.
     */
    suspend fun article(id: ArticleId): ArticleResult
}
