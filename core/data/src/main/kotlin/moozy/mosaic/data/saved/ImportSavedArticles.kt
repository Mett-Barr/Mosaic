package moozy.mosaic.data.saved

import java.io.File
import java.io.IOException
import java.time.DateTimeException
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.Clock

/**
 * The list the previous version wrote, read once and then let go.
 *
 * The whole of this change is a swap behind an interface that did not move,
 * except here: this is the only part that can lose something. DECISIONS 12 chose
 * the file partly because losing a reading list quietly is indistinguishable
 * from the reader clearing it themselves, and dropping it now would be exactly
 * that.
 *
 * It runs on the way in, in the caller's own coroutine, rather than in a
 * database callback or a scope of its own. That way nothing is emitted before it
 * has happened -- no empty list followed by the restored one -- it is cancelled
 * with the screen that asked, and a test can watch it by collecting the list.
 */
internal class ImportSavedArticles(
    private val file: File,
    private val rows: SavedArticleDao,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val once = Mutex()
    private var done = false

    /**
     * The file version's Mutex serialised every write for the life of the app,
     * because each one was a read-modify-write. This one serialises one import,
     * once: two screens collect the list, and only one of them may read the file.
     */
    suspend fun runOnce(onProblem: (String) -> Unit) {
        if (done) return
        once.withLock {
            if (done) return
            done = bringOver(onProblem)
        }
    }

    /** True when there is nothing left to bring over, on this launch or a later one. */
    private suspend fun bringOver(onProblem: (String) -> Unit): Boolean {
        if (!withContext(io) { file.exists() }) return true
        val text = try {
            withContext(io) { file.readText() }
        } catch (unreachable: IOException) {
            // Not settled: a file that could not be opened this time is still
            // the reader's list, and the next launch should try again.
            onProblem("the list the previous version kept could not be opened: ${unreachable.message}")
            return false
        }
        val kept = decode(text, onProblem)
        if (kept == null) {
            // Set aside rather than deleted: it is the reader's, and unreadable
            // is not the same as worthless. Renamed so that a broken file is not
            // re-parsed on every launch for the rest of this install's life.
            withContext(io) { file.renameTo(File(file.parentFile, file.name + ".unreadable")) }
            return true
        }
        // One statement, so one transaction. A process killed here rolls the
        // whole thing back and the file is still there for the next launch; and
        // if it is killed between the commit and the delete, the next launch
        // writes the same primary keys again, which moves nothing a reader sees.
        rows.saveAll(kept.rows(clock.now(), onProblem))
        withContext(io) { file.delete() }
        return true
    }

    private fun decode(text: String, onProblem: (String) -> Unit): List<StoredArticle>? =
        try {
            if (text.isBlank()) emptyList() else json.decodeFromString(SAVED, text)
        } catch (unreadable: SerializationException) {
            onProblem("the list the previous version kept could not be read: ${unreadable.message}")
            null
        }

    /**
     * The rows that can still be an article, and only those, in the order the
     * file had them.
     *
     * A JSON array is newest-first and carries no times of its own, so position
     * is the whole record of the order: row i is saved one millisecond before
     * row i-1. Deterministic, and exact for any list a person curated by hand.
     *
     * This is DECISIONS 18's code, moved rather than deleted -- the untrusted
     * boundary did not vanish when the list became a table, it relocated to the
     * one place bytes this app did not write still arrive.
     */
    private fun List<StoredArticle>.rows(
        now: Instant,
        onProblem: (String) -> Unit,
    ): List<SavedArticleEntity> = mapIndexedNotNull { position, row ->
        try {
            row.toArticle().row(savedAt = now.toEpochMilli() - position)
        } catch (refused: IllegalArgumentException) {
            onProblem("a saved article held values this app cannot use: ${refused.message}")
            null
        } catch (unreadable: DateTimeException) {
            // Not an IllegalArgumentException, which is the whole reason this
            // branch exists: Instant.parse throws DateTimeParseException, and
            // without it a bad time left the read entirely (DECISIONS 18).
            onProblem("a saved article had a time that is not one: ${unreadable.message}")
            null
        }
    }

    private companion object {
        val SAVED = ListSerializer(StoredArticle.serializer())
    }
}

/**
 * What a saved article looked like on disk.
 *
 * The one thing that survives from the file version, because the file still
 * exists -- for one more read. Separate from [ArticleItem] for the reason it
 * always was: the domain does not have to know it was ever written down.
 */
@Serializable
internal data class StoredArticle(
    val id: String,
    val title: String,
    val summary: String,
    val source: String,
    val url: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("published_at") val publishedAt: String,
)

internal fun StoredArticle.toArticle() = ArticleItem(
    id = ArticleId(id),
    title = title,
    summary = summary,
    source = source,
    url = url,
    imageUrl = imageUrl,
    publishedAt = Instant.parse(publishedAt),
)
