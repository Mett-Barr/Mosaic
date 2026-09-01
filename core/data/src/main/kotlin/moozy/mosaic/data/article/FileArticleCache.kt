package moozy.mosaic.data.article

import java.io.File
import java.io.IOException
import java.time.DateTimeException
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.PageCursor

/**
 * The top of the list, written down so the next launch does not have to ask for
 * it again.
 *
 * One file, same as the saved articles, for the same reason: it is read whole,
 * written whole, and small. What it is not is a database of the feed -- only the
 * first page lives here, because that is the request worth not making.
 *
 * Unlike the saved list, losing this costs nothing. A file that will not parse
 * reads as nothing at all, and the next request writes over it.
 */
internal class FileArticleCache(
    private val file: File,
    private val io: CoroutineDispatcher,
) : ArticleCache {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Why the cache could not be read, if it could not be.
     *
     * Losing a cache costs one request, so nothing is shown to the reader about
     * it. Keeping the reason still beats throwing it away: a cache that never
     * loads makes every launch pay for a request, and that is worth being able
     * to find out about.
     */
    internal var lastProblem: String? = null
        private set

    override suspend fun read(): CachedArticles? = withContext(io) {
        try {
            file.takeIf { it.exists() }
                ?.readText()
                ?.takeIf { it.isNotBlank() }
                ?.let { json.decodeFromString(StoredPage.serializer(), it) }
                ?.toCached()
        } catch (unreadable: SerializationException) {
            // First, because SerializationException *is* an
            // IllegalArgumentException: catching the general one above this would
            // leave this branch unreachable and its message unwritten.
            lastProblem = "the cached page could not be read: ${unreadable.message}"
            file.delete()
            null
        } catch (refused: IllegalArgumentException) {
            // Valid JSON whose values the domain will not hold: a blank id, a day
            // colder at its warmest than at its coldest. Parsing succeeded, so
            // neither catch around it sees these.
            lastProblem = "the cached page held values this app cannot use: ${refused.message}"
            file.delete()
            null
        } catch (unreadable: DateTimeException) {
            // A stored time that will not parse. Not an IllegalArgumentException
            // -- which is the whole reason this branch had to be written: without
            // it the throw left read() entirely and took the caller with it.
            lastProblem = "the cached page had a time that is not one: ${unreadable.message}"
            file.delete()
            null
        } catch (unreachable: IOException) {
            lastProblem = "the cached page could not be opened: ${unreachable.message}"
            null
        }
    }

    /**
     * Best effort on purpose. A page that was fetched successfully must reach the
     * reader whether or not it can also be written down; a full disk is not a
     * reason to fail a request that already worked.
     */
    override suspend fun write(articles: CachedArticles) {
        withContext(io) {
            try {
                file.parentFile?.mkdirs()
                // Through a second file and a rename: writeText empties the
                // destination before it fills it, and a process killed in
                // between would leave a file that is neither the old page nor
                // the new one. A rename cannot be half-done.
                val writing = File(file.parentFile, file.name + ".writing")
                writing.writeText(json.encodeToString(StoredPage.serializer(), articles.stored()))
                if (!writing.renameTo(file)) {
                    file.delete()
                    writing.renameTo(file)
                }
            } catch (unwritable: IOException) {
                lastProblem = "the page could not be written down: ${unwritable.message}"
            }
        }
    }
}

@Serializable
private data class StoredPage(
    val articles: List<StoredCachedArticle>,
    val next: String? = null,
    @SerialName("fetched_at") val fetchedAt: String,
    val dropped: Int = 0,
)

@Serializable
private data class StoredCachedArticle(
    val id: String,
    val title: String,
    val summary: String,
    val source: String,
    val url: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("published_at") val publishedAt: String,
)

private fun CachedArticles.stored() = StoredPage(
    articles = articles.map { it.stored() },
    next = next?.value,
    fetchedAt = fetchedAt.toString(),
    dropped = dropped,
)

private fun ArticleItem.stored() = StoredCachedArticle(
    id = id.value,
    title = title,
    summary = summary,
    source = source,
    url = url,
    imageUrl = imageUrl,
    publishedAt = publishedAt.toString(),
)

private fun StoredPage.toCached() = CachedArticles(
    articles = articles.map { it.toArticle() },
    next = next?.let(::PageCursor),
    fetchedAt = Instant.parse(fetchedAt),
    dropped = dropped,
)

private fun StoredCachedArticle.toArticle() = ArticleItem(
    id = ArticleId(id),
    title = title,
    summary = summary,
    source = source,
    url = url,
    imageUrl = imageUrl,
    publishedAt = Instant.parse(publishedAt),
)
