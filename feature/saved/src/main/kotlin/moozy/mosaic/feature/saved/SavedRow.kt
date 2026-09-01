package moozy.mosaic.feature.saved

import androidx.compose.runtime.Immutable
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem

/**
 * A kept article, as the list draws it: a title and where it came from.
 *
 * Less than the article, on purpose. This screen is a way back into something the
 * reader already chose, so it shows enough to recognise it and nothing else --
 * and what it does not show, it does not carry.
 *
 * [id] stays the domain's type because it is not shown: it is what a tap and a
 * remove hand back.
 */
@Immutable
data class SavedRow(
    val id: ArticleId,
    val title: String,
    val source: String,
)

internal fun ArticleItem.row() = SavedRow(id = id, title = title, source = source)
