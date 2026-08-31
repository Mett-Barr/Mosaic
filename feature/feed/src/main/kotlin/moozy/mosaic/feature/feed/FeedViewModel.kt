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
import moozy.mosaic.domain.model.Weather
import moozy.mosaic.domain.model.WeatherResult
import moozy.mosaic.domain.repository.ArticleRepository
import moozy.mosaic.domain.repository.WeatherRepository

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
    private val weather: WeatherRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private var next: PageCursor? = null
    private var loading = false
    private var sky: Weather? = null

    init {
        load(from = null)
        // Asked for alongside the articles rather than after them: the two sources
        // have nothing to do with each other, and making the reader wait for the
        // weather before seeing the news would be inventing a dependency.
        viewModelScope.launch {
            when (val reading = weather.current()) {
                is WeatherResult.Loaded -> {
                    sky = reading.weather
                    val showing = _state.value
                    if (showing is FeedUiState.Content) {
                        _state.value = showing.copy(weather = reading.weather)
                    }
                }

                // No card, and no error either. The reader came for the articles.
                is WeatherResult.Failed -> Unit
            }
        }
    }

    /**
     * Start again from the top, and insist: a reader who asks again has said they
     * want a newer answer than the cache would give them.
     */
    fun retry() {
        next = null
        load(from = null, force = true)
    }

    /** Ask for the page after the last one, if the source said there is one. */
    fun loadMore() {
        load(from = next ?: return)
    }

    private fun load(from: PageCursor?, force: Boolean = false) {
        if (loading) return
        loading = true
        val before = _state.value
        viewModelScope.launch {
            _state.value = beganLoading(before, from)
            _state.value = when (val result = articles.articles(after = from, force = force)) {
                is ArticlesResult.Loaded -> loaded(result, before, from)
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

    /**
     * A page that arrived with nothing usable in it is not an empty feed. Both
     * look like a list of no articles from here, and only one of them means the
     * reader has reached the end.
     */
    private fun loaded(
        result: ArticlesResult.Loaded,
        before: FeedUiState,
        from: PageCursor?,
    ): FeedUiState {
        next = result.next
        val keeping = kept(before, from)
        // By id, keeping the copy already on screen. A page fetched against an
        // offset from an older version of the list can repeat what the reader is
        // looking at, and the list is keyed by id -- Compose throws on a repeat
        // rather than drawing one of them.
        val all = (keeping + result.articles).distinctBy { it.id }.toImmutableList()
        val nothingUsable = result.articles.isEmpty() && result.dropped > 0
        return when {
            nothingUsable && keeping.isNotEmpty() -> FeedUiState.Content(
                articles = all,
                canLoadMore = result.next != null,
                moreFailed = unreadable(result.dropped),
                weather = sky,
            )

            nothingUsable -> FeedUiState.Error(unreadable(result.dropped))

            all.isEmpty() -> FeedUiState.Empty

            // Every path that builds a Content carries the weather. Forgetting it
            // on one of them makes the card depend on which request answered
            // first, which is not something a reader should be able to notice.
            else -> FeedUiState.Content(
                articles = all,
                canLoadMore = result.next != null,
                weather = sky,
            )
        }
    }

    private fun unreadable(dropped: Int) =
        FeedFailure.Unreadable("$dropped rows arrived in a shape this app could not use")

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
