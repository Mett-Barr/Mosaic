package moozy.mosaic.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleResult
import moozy.mosaic.domain.repository.ArticleRepository
import moozy.mosaic.domain.repository.SavedArticles

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
    private val kept: SavedArticles,
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

    private suspend fun keptCopyOf(id: ArticleId): DetailUiState.Content? =
        kept.saved.first().firstOrNull { it.id == id }?.let { DetailUiState.Content(it, saved = true) }

    /** Keep this article to read later, network or no network. */
    fun keep() {
        val article = (_state.value as? DetailUiState.Content)?.article ?: return
        viewModelScope.launch { kept.save(article) }
    }

    /** Stop keeping it. */
    fun letGo() {
        val id = showing ?: return
        viewModelScope.launch { kept.forget(id) }
    }

    init {
        // Whether this article is kept is answered by the list of kept articles,
        // not by a copy of that answer taken when the screen opened: keeping it
        // from somewhere else has to show up here too.
        viewModelScope.launch {
            kept.saved.collect { saved ->
                val showing = _state.value
                if (showing is DetailUiState.Content) {
                    _state.value = showing.copy(saved = saved.any { it.id == showing.article.id })
                }
            }
        }
    }

    private fun load(id: ArticleId) {
        // Whoever was asked before is answering about an article nobody is
        // looking at any more. Letting it finish would change the screen under
        // the reader into something they did not ask for.
        loading?.cancel()
        loading = viewModelScope.launch {
            _state.value = DetailUiState.Loading
            _state.value = when (val result = articles.article(id)) {
                is ArticleResult.Loaded -> DetailUiState.Content(
                    article = result.article,
                    saved = kept.saved.first().any { it.id == result.article.id },
                )
                // A reader who kept this one asked for it to be here when the
                // network is not. Falling back to the copy they kept is the whole
                // point of having kept it; failing anyway would make the button a
                // decoration.
                is ArticleResult.Failed -> keptCopyOf(id) ?: DetailUiState.Failed(result.reason)
            }
        }
    }
}
