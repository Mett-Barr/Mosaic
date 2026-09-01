package moozy.mosaic.domain.repository

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
 */
interface ArticleRepository {

    /**
     * One page. Null asks for the top of the list; anything else asks for what
     * comes after the cursor the source handed back with the page before.
     *
     * The cursor is a parameter rather than something the repository remembers. A
     * repository that holds "the page I am on" has one such page for every caller,
     * and two screens asking at once would move each other's list. Where a reader
     * has got to belongs to whatever is showing the list.
     */
    suspend fun articles(after: PageCursor?): ArticlesResult

    /**
     * One article, for a reader who opened it.
     *
     * It is asked for again rather than carried over from the list, because the
     * list is a screen's worth of state and a deep link has no list behind it.
     */
    suspend fun article(id: ArticleId): ArticleResult
}
