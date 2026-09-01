package moozy.mosaic.feature.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.repository.SavedArticles

/**
 * The kept list, as the screen sees it.
 *
 * It is derived from the store rather than copied out of it, so something kept on
 * another screen turns up here without this one being told.
 */
@HiltViewModel
class SavedViewModel @Inject constructor(
    private val kept: SavedArticles,
) : ViewModel() {

    val state: StateFlow<SavedUiState> = kept.saved
        .map { articles ->
            if (articles.isEmpty()) {
                SavedUiState.Empty
            } else {
                SavedUiState.Content(articles.map { it.row() }.toImmutableList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE), SavedUiState.Empty)

    fun letGo(id: ArticleId) {
        viewModelScope.launch { kept.forget(id) }
    }

    private companion object {
        /** Long enough to survive a rotation, short enough not to outlive the screen. */
        const val SUBSCRIPTION_GRACE = 5_000L
    }
}
