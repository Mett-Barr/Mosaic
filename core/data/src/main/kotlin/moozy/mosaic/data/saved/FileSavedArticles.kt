package moozy.mosaic.data.saved

import java.io.File
import java.io.IOException
import java.time.DateTimeException
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.repository.SavedArticles

/**
 * Saved articles, kept as one JSON file.
 *
 * A whole-file rewrite on every change, which would be the wrong shape for a feed
 * and is the right one for a list somebody curates by hand: it is small, it is
 * read all at once, and there is no query anybody wants to run over it that a
 * list cannot answer. What it buys is that the whole thing is testable on a JVM
 * with a temporary directory -- no device, no Robolectric, no schema.
 *
 * The reading is deliberately forgiving. A half-written file is a thing that
 * happens on a phone that ran out of battery mid-save, and losing a reading list
 * is bad; crashing on every launch afterwards is worse.
 */
internal class FileSavedArticles(
    private val file: File,
    private val io: CoroutineDispatcher,
) : SavedArticles {

    private val json = Json { ignoreUnknownKeys = true }
    private val writing = Mutex()
    private val problem = MutableStateFlow<String?>(null)

    /**
     * Why the file could not be read, if it could not be.
     *
     * Recovering by starting from nothing is the only way to keep the app usable,
     * but it is also how a reading list disappears without anybody being told. The
     * reason is kept here so that somebody can be.
     */
    val lastProblem: Flow<String?> = problem.asStateFlow()
    private val articles = MutableStateFlow(read().readable())

    override val saved: Flow<List<ArticleItem>> = articles.asStateFlow()

    override suspend fun save(article: ArticleItem) = update { kept ->
        // Newest first, and one copy per article: saving something already saved
        // is how a reader updates it, not how they get two of it.
        listOf(article.stored()) + kept.filterNot { it.id == article.id.value }
    }

    override suspend fun forget(id: ArticleId) = update { kept ->
        kept.filterNot { it.id == id.value }
    }

    private suspend fun update(change: (List<StoredArticle>) -> List<StoredArticle>) {
        writing.withLock {
            val next = change(read())
            // The list in memory follows the file, not the other way round. A
            // change that could not be written down is a change that did not
            // happen, and showing it anyway would tell the reader their article
            // is saved when the next launch will disagree.
            if (withContext(io) { write(next) }) {
                articles.value = next.readable()
            }
        }
    }

    private fun read(): List<StoredArticle> =
        try {
            file.takeIf { it.exists() }
                ?.readText()
                ?.takeIf { it.isNotBlank() }
                ?.let { json.decodeFromString(SAVED, it) }
                .orEmpty()
        } catch (unreadable: SerializationException) {
            // Whatever is in there is not a reading list. Starting from nothing is
            // the only way back, and it is what the next save will write over.
            problem.value = "the saved list could not be read: ${unreadable.message}"
            emptyList()
        } catch (unreachable: IOException) {
            problem.value = "the saved list could not be opened: ${unreachable.message}"
            emptyList()
        }

    /**
     * The rows that can still be an article, and only those.
     *
     * A row is dropped rather than the file, because one unreadable entry is not
     * a reason to lose the rest of somebody's reading list. What makes a row
     * unreadable is a stored time that will not parse, or a value the domain
     * refuses -- a file must not be a way in past a constructor.
     */
    private fun List<StoredArticle>.readable(): List<ArticleItem> = mapNotNull { row ->
        try {
            row.toArticle()
        } catch (refused: IllegalArgumentException) {
            problem.value = "a saved article held values this app cannot use: ${refused.message}"
            null
        } catch (unreadable: DateTimeException) {
            problem.value = "a saved article had a time that is not one: ${unreadable.message}"
            null
        }
    }

    /**
     * Write the whole list, or leave the one that is there alone.
     *
     * Through a second file and a rename, because writeText empties the
     * destination before it fills it: a process killed in between -- a phone out
     * of battery mid-save -- would leave the reader with an empty reading list
     * rather than the one they had. A rename cannot be half-done.
     */
    private fun write(next: List<StoredArticle>): Boolean =
        try {
            file.parentFile?.mkdirs()
            val writing = File(file.parentFile, file.name + ".writing")
            writing.writeText(json.encodeToString(SAVED, next))
            check(writing.renameTo(file) || (file.delete() && writing.renameTo(file))) {
                "the list could not be moved into place"
            }
            true
        } catch (unwritable: IOException) {
            problem.value = "the saved list could not be written: ${unwritable.message}"
            false
        } catch (unplaceable: IllegalStateException) {
            problem.value = "the saved list could not be written: ${unplaceable.message}"
            false
        }

    private companion object {
        val SAVED = kotlinx.serialization.builtins.ListSerializer(StoredArticle.serializer())
    }
}

/**
 * What a saved article looks like on disk.
 *
 * Separate from [ArticleItem] so the domain does not have to know it is ever
 * written down, and so the file format can change without the model having to.
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

private fun ArticleItem.stored() = StoredArticle(
    id = id.value,
    title = title,
    summary = summary,
    source = source,
    url = url,
    imageUrl = imageUrl,
    publishedAt = publishedAt.toString(),
)

private fun StoredArticle.toArticle() = ArticleItem(
    id = ArticleId(id),
    title = title,
    summary = summary,
    source = source,
    url = url,
    imageUrl = imageUrl,
    publishedAt = Instant.parse(publishedAt),
)
