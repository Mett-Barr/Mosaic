package moozy.mosaic.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.ArticlesResult
import moozy.mosaic.domain.model.FeedFailure
import moozy.mosaic.domain.model.PageCursor
import moozy.mosaic.domain.repository.ArticleRepository

/**
 * Decides what the feed is showing.
 *
 * Where the reader has got to lives here rather than in the repository: it is the
 * state of a screen, not the state of the data. Two screens on the same feed have
 * two different positions in it, and only one cache.
 */
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val articles: ArticleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private var next: PageCursor? = null
    private var loading = false

    init {
        load(from = null)
    }

    /** Start again from the top. What is on screen now is not worth keeping. */
    fun retry() {
        next = null
        load(from = null)
    }

    /** Ask for the page after the last one, if the source said there is one. */
    fun loadMore() {
        load(from = next ?: return)
    }

    private fun load(from: PageCursor?) {
        if (loading) return
        loading = true
        val before = _state.value
        viewModelScope.launch {
            _state.value = beganLoading(before, from)
            _state.value = when (val result = articles.articles(after = from)) {
                is ArticlesResult.Loaded -> loaded(result, keeping = kept(before, from))
                is ArticlesResult.Failed -> failed(result.reason, before, from)
            }
            loading = false
        }
    }

    private fun beganLoading(before: FeedUiState, from: PageCursor?): FeedUiState =
        if (from != null && before is FeedUiState.Content) {
            before.copy(loadingMore = true, moreFailed = null)
        } else {
            FeedUiState.Loading
        }

    /** A refresh replaces what is on screen; a next page adds to it. */
    private fun kept(before: FeedUiState, from: PageCursor?): List<ArticleItem> =
        if (from != null && before is FeedUiState.Content) before.articles else emptyList()

    private fun loaded(result: ArticlesResult.Loaded, keeping: List<ArticleItem>): FeedUiState {
        next = result.next
        val all = (keeping + result.articles).toImmutableList()
        return if (all.isEmpty()) {
            FeedUiState.Empty
        } else {
            FeedUiState.Content(articles = all, canLoadMore = result.next != null)
        }
    }

    /**
     * A page that failed while there is already something to read is a note on the
     * bottom of the list, not a screen of its own.
     */
    private fun failed(reason: FeedFailure, before: FeedUiState, from: PageCursor?): FeedUiState =
        when {
            from != null && before is FeedUiState.Content ->
                before.copy(loadingMore = false, moreFailed = reason)

            reason is FeedFailure.Offline -> FeedUiState.Offline

            else -> FeedUiState.Error(reason)
        }
}
