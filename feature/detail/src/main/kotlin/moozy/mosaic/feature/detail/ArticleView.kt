package moozy.mosaic.feature.detail

import androidx.compose.runtime.Immutable
import moozy.mosaic.core.ui.readableTime
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.FeedFailure

/**
 * One article, in the words the screen shows it in.
 *
 * The layer between the domain and the screen. Everything here is a decision --
 * how a time reads, what the button leaving for the publisher's site is called --
 * and a decision made inside a composable is one nothing in this project can run
 * a check against.
 *
 * [url] is here because the button needs somewhere to go, not because it is
 * shown. [readFullLabel] names the destination, so the name travels with it.
 */
@Immutable
data class ArticleView(
    val title: String,
    val attribution: String,
    val summary: String,
    val imageUrl: String?,
    val url: String,
    val readFullLabel: String,
)

internal fun ArticleItem.view() = ArticleView(
    title = title,
    attribution = "$source · ${readableTime(publishedAt)}",
    summary = summary,
    imageUrl = imageUrl,
    url = url,
    readFullLabel = "Read the full article at $source",
)

internal fun FeedFailure.headline(): String = when (this) {
    is FeedFailure.Missing -> "This article is gone."
    is FeedFailure.Offline -> "You appear to be offline."
    else -> "Something went wrong."
}

internal fun FeedFailure.hint(): String = when (this) {
    is FeedFailure.Missing -> "It is no longer where the feed said it was."
    is FeedFailure.Offline -> "The article will open when the connection is back."
    is FeedFailure.Timeout -> "The article took too long to arrive."
    is FeedFailure.Server -> "The feed is having trouble."
    is FeedFailure.Unreadable -> "What arrived was not something this app can read."
    is FeedFailure.Unexpected -> "Something unexpected happened."
}

/** An article that is gone will be gone next time too; everything else might not be. */
internal fun FeedFailure.worthTryingAgain(): Boolean = this !is FeedFailure.Missing
