package moozy.mosaic.feature.detail

import androidx.compose.runtime.Immutable
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.FeedFailure

/**
 * Everything the article screen can be showing.
 *
 * A failure keeps the reason it failed rather than becoming a single "could not
 * load": an article that is gone and a phone with no network lead a reader to do
 * different things, and a screen that cannot tell them apart chooses for them.
 */
@Immutable
sealed interface DetailUiState {

    data object Loading : DetailUiState

    data class Content(val article: ArticleItem) : DetailUiState

    data class Failed(val reason: FeedFailure) : DetailUiState
}
