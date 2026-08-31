package moozy.mosaic.feature.saved

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import moozy.mosaic.domain.model.ArticleItem

/**
 * What the kept list can be showing.
 *
 * There is no loading and no failure here, and that is the point of the screen:
 * everything it shows is already on the device. The only two answers are "here
 * they are" and "you have not kept anything yet".
 */
@Immutable
sealed interface SavedUiState {

    data object Empty : SavedUiState

    data class Content(val articles: ImmutableList<ArticleItem>) : SavedUiState
}
