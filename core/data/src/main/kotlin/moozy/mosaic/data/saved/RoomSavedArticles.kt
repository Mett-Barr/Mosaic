package moozy.mosaic.data.saved

import android.database.sqlite.SQLiteException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.Clock
import moozy.mosaic.domain.repository.SavedArticles

/**
 * Saved articles, kept as rows.
 *
 * The list stops being a copy that every write has to remember to update and
 * becomes a query over the thing that was written: there is no longer a state
 * where the screen shows something the disk disagrees with, because there is no
 * second place for the list to be.
 *
 * Nothing is read until somebody collects. The file version read itself in its
 * constructor, which -- being a singleton built when the first screen asked for
 * it -- was a disk read on the main thread.
 */
internal class RoomSavedArticles(
    private val rows: SavedArticleDao,
    private val clock: Clock,
    private val importing: ImportSavedArticles,
) : SavedArticles {

    private val problem = MutableStateFlow<String?>(null)

    /**
     * Why something could not be written down, if it could not be.
     *
     * Narrower than the file version's: a half-written list, a document that
     * will not parse and a time that will not read back are not states this can
     * be in. What is left is a database that refused a write, which nobody can
     * do anything about but somebody should be able to find out about.
     */
    val lastProblem: Flow<String?> = problem.asStateFlow()

    /**
     * Room re-emits on any invalidation of the table, including a save that
     * produced the same rows; the file version's StateFlow conflated those away
     * for free. distinctUntilChanged buys that back, so "the interface behaves
     * as it did" needs no footnote.
     */
    override val saved: Flow<List<ArticleItem>> = flow {
        ready()
        emitAll(rows.saved().map { it.readable() })
    }.distinctUntilChanged()

    override suspend fun save(article: ArticleItem) {
        ready()
        quietly("this article could not be kept") {
            rows.save(article.row(savedAt = clock.now().toEpochMilli()))
        }
    }

    override suspend fun forget(id: ArticleId) {
        ready()
        quietly("this article could not be forgotten") { rows.forget(id.value) }
    }

    /**
     * The list the previous version wrote is brought over on the way in, in the
     * caller's own coroutine, rather than in a scope of its own (DECISIONS 21)
     * or a database callback. So nothing is emitted before it has happened --
     * there is no flash of an empty list followed by the restored one -- and it
     * is cancelled with the screen that asked for it.
     *
     * save() waits for it too. In practice the read path always runs first,
     * because DetailViewModel collects the list in its init; "in practice" is
     * not a guarantee and this is one line.
     */
    private suspend fun ready() =
        quietly("the list the previous version kept could not be brought over") {
            importing.runOnce { problem.value = it }
        }

    /**
     * The interface promises nothing here can fail, and it has to keep that
     * promise: an uncaught throw out of save() would take the calling screen's
     * coroutine with it.
     *
     * The clause says SQLiteException because that is what Room 2.8 was measured
     * throwing when a write cannot be compiled -- android.database.sqlite's, not
     * androidx.sqlite's. DECISIONS 18 is the record of what guessing this costs.
     */
    private suspend fun quietly(what: String, change: suspend () -> Unit) {
        try {
            change()
        } catch (cancelled: CancellationException) {
            // Always first and always rethrown: a screen that went away is not a
            // failure to report, and swallowing it leaves work with nobody
            // waiting for it. Room's own close() arrives here.
            throw cancelled
        } catch (refused: SQLiteException) {
            problem.value = "$what: ${refused.message}"
        }
    }

    /**
     * The rows that can still be an article, and only those.
     *
     * Not reachable through [save]: an ArticleItem cannot hold a blank title, and
     * this DAO is the only thing that writes the table. It is kept because "only"
     * is a claim about code rather than about the file on disk, and a row that
     * arrived some other way should cost the reader one article, not the list.
     */
    private fun List<SavedArticleEntity>.readable(): List<ArticleItem> = mapNotNull { row ->
        try {
            row.toArticle()
        } catch (refused: IllegalArgumentException) {
            problem.value = "a saved article held values this app cannot use: ${refused.message}"
            null
        }
    }
}
