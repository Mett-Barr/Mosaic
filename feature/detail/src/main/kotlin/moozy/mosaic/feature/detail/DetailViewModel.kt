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
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.ArticleResult
import moozy.mosaic.domain.repository.ArticleRepository
import moozy.mosaic.domain.repository.SavedArticles

/**
 * Holds one article for as long as a reader is looking at it.
 *
 * [open] is called by the screen rather than the id arriving through the
 * constructor, so that opening the same article again -- which is what a
 * recomposition looks like from here -- does not send the same request twice.
 *
 * Where the article comes from is not decided here. [articles] is asked once and
 * answers from the copy the reader kept, from the page the feed is showing, or
 * from the network, whichever there is (DECISIONS 30, 41). [kept] is still
 * needed, for a different question: not "which copy of this article" but "is it
 * kept right now", which is what the button shows and what changes while the
 * reader is looking at it.
 *
 * That question is watched rather than asked. It has its own query and its own
 * answer, and putting a suspending read of it on the way to the article would
 * hold an article this app already has behind news about something else.
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val articles: ArticleRepository,
    private val kept: SavedArticles,
) : ViewModel() {

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var showing: ArticleId? = null
    // The article itself, not the view of it: keeping one means handing the
    // whole thing to the store, and the screen was only given the words.
    private var holding: ArticleItem? = null
    private var loading: Job? = null

    /**
     * The reading list as it last arrived, so that "is this one kept" can be
     * answered without asking for it again.
     *
     * The collector below is already watching that list; reading it a second time
     * would be a second version of one fact, and -- the half that shows -- a
     * suspending one. An article this app already has would still queue behind a
     * query about something else, and the loading state that puts on screen is
     * the one the card the reader tapped has nothing to grow into.
     *
     * Written and read on the same dispatcher: [viewModelScope] is Main-confined
     * and so is every coroutine here.
     */
    private var keptNow: List<ArticleItem> = emptyList()

    fun open(id: ArticleId) {
        if (id == showing) return
        showing = id
        load(id)
    }

    fun retry() {
        load(showing ?: return)
    }

    /** Keep this article to read later, network or no network. */
    fun keep() {
        val article = holding ?: return
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
                keptNow = saved
                val onScreen = _state.value
                val id = holding?.id
                if (onScreen is DetailUiState.Content && id != null) {
                    _state.value = onScreen.copy(saved = saved.any { it.id == id })
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
            // Nothing is on screen, so there is nothing to keep. Leaving the
            // last article here would let a save meant for this one land on
            // that one.
            holding = null
            _state.value = DetailUiState.Loading
            _state.value = when (val result = articles.article(id)) {
                is ArticleResult.Loaded -> {
                    holding = result.article
                    DetailUiState.Content(
                        article = result.article.view(),
                        // The list as it last arrived, not a fresh question about
                        // it. The collector in `init` has been watching it since
                        // this screen opened and will correct this the moment it
                        // says something different -- which is what it is for.
                        saved = keptNow.any { it.id == result.article.id },
                    )
                }
                // Nothing to fall back to from here. A reader who kept this one
                // was already served the copy they kept -- that happened before
                // any request was made, one layer down, where the article has a
                // single source of truth (DECISIONS 30). So a failure that
                // arrives here is the answer, not the first of two opinions.
                is ArticleResult.Failed -> DetailUiState.Failed(
                    message = result.reason.headline(),
                    hint = result.reason.hint(),
                    canRetry = result.reason.worthTryingAgain(),
                )
            }
        }
    }
}
