package moozy.mosaic.domain.repository

import kotlinx.coroutines.flow.Flow
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem

/**
 * The articles a reader kept.
 *
 * A [Flow] rather than a suspending read, because saving is something that
 * happens on one screen and shows up on another: the list and the button that
 * fills it are not the same place, and neither should have to ask the other.
 *
 * Nothing here can fail. Whether a device can write a file is not a question the
 * reader can do anything about, and a save button that sometimes reports failure
 * is a worse promise than one that keeps what it can.
 */
interface SavedArticles {

    /** Most recently saved first. */
    val saved: Flow<List<ArticleItem>>

    suspend fun save(article: ArticleItem)

    suspend fun forget(id: ArticleId)
}
