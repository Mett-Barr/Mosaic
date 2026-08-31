package moozy.mosaic.data.article

import java.io.File
import java.io.IOException
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
            lastProblem = "the cached page could not be read: ${unreadable.message}"
            file.delete()
            null
        } catch (unreachable: IOException) {
            lastProblem = "the cached page could not be opened: ${unreachable.message}"
            null
        }
    }

    override suspend fun write(articles: CachedArticles) {
        withContext(io) {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(StoredPage.serializer(), articles.stored()))
        }
    }
}

@Serializable
private data class StoredPage(
    val articles: List<StoredCachedArticle>,
    val next: String? = null,
    @SerialName("fetched_at") val fetchedAt: String,
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
