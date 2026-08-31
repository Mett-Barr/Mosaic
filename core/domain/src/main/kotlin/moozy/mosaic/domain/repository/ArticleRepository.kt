package moozy.mosaic.domain.repository

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

    suspend fun articles(after: PageCursor?): ArticlesResult
}
