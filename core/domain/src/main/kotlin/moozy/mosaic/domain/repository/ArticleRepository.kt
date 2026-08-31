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
 *
 * The cursor is a parameter rather than something the repository remembers. A
 * repository that holds "the page I am on" has one such page for every caller, and
 * two screens asking at once would move each other's list. Where a reader has got
 * to belongs to whatever is showing the list.
 */
interface ArticleRepository {

    /**
     * [force] is a reader saying they want a newer answer than the one that would
     * otherwise be given. Whatever policy sits between here and the network is
     * expected to step aside: a policy exists so the app does not spend somebody's
     * data without being asked, not so it can decline when they do ask.
     */
    suspend fun articles(after: PageCursor?, force: Boolean = false): ArticlesResult

    /**
     * One article, for a reader who opened it.
     *
     * It is asked for again rather than carried over from the list, because the
     * list is a screen's worth of state and a deep link has no list behind it.
     * Once there is a cache this is where it will be read from first.
     */
    suspend fun article(id: ArticleId): ArticleResult
}
