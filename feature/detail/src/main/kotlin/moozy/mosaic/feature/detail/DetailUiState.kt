package moozy.mosaic.feature.detail

import androidx.compose.runtime.Immutable

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
    data class Content(val article: ArticleView, val saved: Boolean = false) : DetailUiState

    /**
     * [canRetry] is false for an article that is gone: it will be gone next
     * time too, and a button that cannot work is worse than no button.
     */
    data class Failed(
        val message: String,
        val hint: String,
        val canRetry: Boolean,
    ) : DetailUiState
}
