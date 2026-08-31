package moozy.mosaic.data.saved

import java.io.File
import java.io.IOException
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
    private val articles = MutableStateFlow(read().map { it.toArticle() })

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
            withContext(io) { write(next) }
            articles.value = next.map { it.toArticle() }
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

    private fun write(next: List<StoredArticle>) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(SAVED, next))
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
