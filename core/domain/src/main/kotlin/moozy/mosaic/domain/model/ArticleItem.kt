package moozy.mosaic.domain.model

import java.time.Instant

/**
 * The identity of an article, held apart from the [String] it wraps so that the ids
 * of the other sources this feed will carry cannot be handed over in its place.
 */
@JvmInline
value class ArticleId(val value: String) {
    init {
        require(value.isNotBlank()) { "An article id cannot be blank." }
    }
}

/**
 * One article in the feed.
 *
 * [publishedAt] is an instant and not a formatted string: freshness is arithmetic
 * on it, and how it reads to a person is the UI's business.
 */
data class ArticleItem(
    val id: ArticleId,
    val title: String,
    val summary: String,
    val source: String,
    val url: String,
    val imageUrl: String?,
    val publishedAt: Instant,
) {
    init {
        require(title.isNotBlank()) { "An article cannot have a blank title: $id" }
    }
}
