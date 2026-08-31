package moozy.mosaic.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleResult
import moozy.mosaic.domain.repository.ArticleRepository

/**
 * Holds one article for as long as a reader is looking at it.
 *
 * [open] is called by the screen rather than the id arriving through the
 * constructor, so that opening the same article again -- which is what a
 * recomposition looks like from here -- does not send the same request twice.
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val articles: ArticleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var showing: ArticleId? = null
    private var loading: Job? = null

    fun open(id: ArticleId) {
        if (id == showing) return
        showing = id
        load(id)
    }

    fun retry() {
        load(showing ?: return)
    }

    private fun load(id: ArticleId) {
        // Whoever was asked before is answering about an article nobody is
        // looking at any more. Letting it finish would change the screen under
        // the reader into something they did not ask for.
        loading?.cancel()
        loading = viewModelScope.launch {
            _state.value = DetailUiState.Loading
            _state.value = when (val result = articles.article(id)) {
                is ArticleResult.Loaded -> DetailUiState.Content(result.article)
                is ArticleResult.Failed -> DetailUiState.Failed(result.reason)
            }
        }
    }
}
