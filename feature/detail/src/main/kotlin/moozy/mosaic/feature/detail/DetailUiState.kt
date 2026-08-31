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

    /**
     * [saved] is whether the reader kept this one. It lives on the state rather
     * than being asked for separately so that the button and the article cannot
     * disagree about which article they are describing.
     */
    data class Content(val article: ArticleItem, val saved: Boolean = false) : DetailUiState

    data class Failed(val reason: FeedFailure) : DetailUiState
}
