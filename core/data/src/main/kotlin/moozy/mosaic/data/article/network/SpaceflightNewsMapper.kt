package moozy.mosaic.data.article.network

import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem

/**
 * Unknown keys are ignored deliberately. The response carries fields this app has
 * no use for -- authors, launches, events -- and the day the API grows one more,
 * that should not be the day the feed goes empty.
 */
internal val SpaceflightNewsJson = Json { ignoreUnknownKeys = true }

/**
 * A page of the article list.
 *
 * [results] has no default on purpose. A response that does not carry the list at
 * all is a broken response, and letting it decode into an empty page would put a
 * "there is nothing here" screen in front of a reader when the truth is "something
 * is wrong" -- two different screens, and telling them apart is the point of
 * handling the states explicitly.
 *
 * The rows stay undecoded here so that one bad row stays one bad row: decoding
 * them as `List<ArticleDto>` would let a single wrong type anywhere in the list
 * fail the whole page before the mapper below ever ran.
 */
@Serializable
internal data class ArticlePageDto(
    val results: List<JsonElement>,
    val count: Int = 0,
    val next: String? = null,
)

/**
 * The fields this app cannot show an article without have no defaults: a row that
 * arrives without one is not a usable article and is dropped as such. A missing
 * summary is a different matter -- an article with nothing but a headline still
 * reads -- and a missing image is ordinary.
 */
@Serializable
internal data class ArticleDto(
    val id: Int,
    val title: String,
    val url: String,
    @SerialName("news_site") val newsSite: String,
    @SerialName("published_at") val publishedAt: String,
    val summary: String = "",
    @SerialName("image_url") val imageUrl: String? = null,
)

/**
 * The articles a page yielded, and why the rest of its rows did not make it.
 *
 * The reasons are carried rather than logged because this module has no opinion
 * about where logs go, and because a feed that quietly returns nineteen of twenty
 * articles looks exactly like a feed that returned nineteen. Somebody upstream
 * gets to decide whether anyone should hear about it.
 */
internal data class MappedArticles(
    val articles: List<ArticleItem>,
    val droppedReasons: List<String>,
) {
    val dropped: Int get() = droppedReasons.size
}

internal fun ArticlePageDto.toArticles(): MappedArticles {
    val droppedReasons = mutableListOf<String>()
    val articles = results.mapNotNull { it.toArticleOrNull(droppedReasons::add) }
    return MappedArticles(articles = articles, droppedReasons = droppedReasons)
}

/**
 * Null for a row that cannot become an article.
 *
 * The domain's rules are not restated here. This asks the model by trying, so an
 * invariant added there later cannot quietly stop being enforced at this boundary:
 * it would show up as a dropped row, which is the intended behaviour, rather than
 * as a crash in the middle of a page.
 */
private fun JsonElement.toArticleOrNull(onDropped: (String) -> Unit): ArticleItem? {
    val dto = decodeArticleOrNull(onDropped) ?: return null
    val published = dto.publishedAt.toInstantOrNull(onDropped) ?: return null
    return try {
        ArticleItem(
            id = ArticleId(dto.id.toString()),
            title = dto.title,
            summary = dto.summary,
            source = dto.newsSite,
            url = dto.url,
            imageUrl = dto.imageUrl?.takeIf { it.isNotBlank() },
            publishedAt = published,
        )
    } catch (unusable: IllegalArgumentException) {
        onDropped("row ${dto.id}: ${unusable.message}")
        null
    }
}

private fun JsonElement.decodeArticleOrNull(onDropped: (String) -> Unit): ArticleDto? =
    try {
        SpaceflightNewsJson.decodeFromJsonElement(ArticleDto.serializer(), this)
    } catch (malformed: SerializationException) {
        onDropped("unreadable row: ${malformed.message}")
        null
    }

private fun String.toInstantOrNull(onDropped: (String) -> Unit): Instant? =
    try {
        Instant.parse(this)
    } catch (malformed: DateTimeParseException) {
        onDropped("unreadable timestamp: ${malformed.message}")
        null
    }
