package moozy.mosaic.data.saved

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem

/**
 * A kept article, as a row.
 *
 * Separate from the domain model for the reason the JSON row it replaces was
 * separate: the domain does not have to know it is ever written down.
 *
 * Both times are stored as epoch milliseconds rather than as the text the file
 * used. Nothing but this app's own DAO can write this table, so a timestamp that
 * will not parse is no longer a thing that can arrive -- and Instant.ofEpochMilli
 * is total over Long, so reading one back cannot throw.
 */
@Entity(tableName = "saved_articles")
internal data class SavedArticleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val summary: String,
    val source: String,
    val url: String,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    @ColumnInfo(name = "published_at") val publishedAt: Long,
    @ColumnInfo(name = "saved_at") val savedAt: Long,
)

/**
 * [savedAt] is a parameter rather than a clock read here because the two callers
 * mean different moments by it: keeping an article means now, and bringing over
 * the list the previous version wrote means the position that list was already in.
 */
internal fun ArticleItem.row(savedAt: Long) = SavedArticleEntity(
    id = id.value,
    title = title,
    summary = summary,
    source = source,
    url = url,
    imageUrl = imageUrl,
    publishedAt = publishedAt.toEpochMilli(),
    savedAt = savedAt,
)

/**
 * No time can fail to read back: Instant.ofEpochMilli is total over Long, so the
 * DateTimeException branch DECISIONS 18 was written about is not unlikely here,
 * it is unreachable -- and a catch for it would be decoration.
 *
 * Throws IllegalArgumentException for a row the domain will not hold. That is
 * not reachable through the DAO's own writers; the caller decides whether to
 * treat it as impossible or as one article missing.
 */
internal fun SavedArticleEntity.toArticle() = ArticleItem(
    id = ArticleId(id),
    title = title,
    summary = summary,
    source = source,
    url = url,
    imageUrl = imageUrl,
    publishedAt = Instant.ofEpochMilli(publishedAt),
)
