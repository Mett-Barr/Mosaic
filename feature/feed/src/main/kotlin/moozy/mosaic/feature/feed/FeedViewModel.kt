package moozy.mosaic.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moozy.mosaic.domain.model.ArticlesResult
import moozy.mosaic.domain.model.FeedFailure
import moozy.mosaic.domain.model.PageCursor
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

    /**
     * Work starts when somebody is watching, and only then.
     *
     * Not in `init`: a view model that fetches when it is constructed fetches
     * whether or not a screen exists to show the result, and this one is scoped
     * to the root destination -- constructed once and then alive for the rest of
     * the session. That is also why `init` cannot be the whole story: it runs
     * once, and the reader comes back to this screen many times.
     *
     * `onStart` runs on every new subscription, and the screen resubscribes when
     * it returns to the foreground. So returning is the trigger, and it costs
     * nothing when nothing is due: the weather repository answers from what it
     * already holds unless the source has produced a new reading, and the
     * articles are only asked for when there are none on screen.
     */
    val state: StateFlow<FeedUiState> = combine(_state, weather.current) { feed, sky ->
        // The card is joined on rather than carried, so no path through the
        // feed can forget it. One did, and the card vanished depending on
        // which request answered first.
        if (feed is FeedUiState.Content) feed.copy(weather = sky?.headline()) else feed
    }
        .onStart {
            // Not on every return: replacing the list under a reader who is
            // somewhere in it is worse than a list a few minutes old. A screen
            // showing nothing, or showing a failure, has nothing to lose.
            if (_state.value !is FeedUiState.Content) load(from = null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(WATCHING_GRACE), FeedUiState.Loading)

    private var next: PageCursor? = null
    private var loading = false


    /**
     * Start again from the top, and insist: a reader who asks again has said they
     * want a newer answer than the cache would give them.
     *
     * The same call whether it came from the button on an error screen or from
     * pulling the list down; what differs is what is on screen to keep, and that
     * is decided where the loading state is built.
     */
    fun retry() = refresh()

    fun refresh() {
        next = null
        loading = false
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
                is ArticlesResult.Loaded -> loaded(result, before, from)
                is ArticlesResult.Failed -> failed(result.reason, before, from)
            }
            loading = false
        }
    }

    private fun beganLoading(before: FeedUiState, from: PageCursor?): FeedUiState = when {
        from != null && before is FeedUiState.Content ->
            before.copy(loadingMore = true, moreFailed = null)

        // A refresh over something already readable keeps it. Only a screen with
        // nothing on it gets the spinner.
        before is FeedUiState.Content -> before.copy(refreshing = true, moreFailed = null)

        else -> FeedUiState.Loading
    }

    /** A refresh replaces what is on screen; a next page adds to it. */
    private fun kept(before: FeedUiState, from: PageCursor?): List<ArticleRow> =
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
        val all = (keeping + result.articles.map { it.row() })
            .distinctBy { it.id }
            .toImmutableList()
        val nothingUsable = result.articles.isEmpty() && result.dropped > 0
        return when {
            nothingUsable && keeping.isNotEmpty() -> FeedUiState.Content(
                articles = all,
                canLoadMore = result.next != null,
                moreFailed = unreadable(result.dropped),
            )

            nothingUsable -> FeedUiState.Error(SOMETHING_WENT_WRONG, unreadable(result.dropped))

            all.isEmpty() -> FeedUiState.Empty

            else -> FeedUiState.Content(
                articles = all,
                canLoadMore = result.next != null,
            )
        }
    }

    private fun unreadable(dropped: Int) =
        FeedFailure.Unreadable("$dropped rows arrived in a shape this app could not use").hint()

    /**
     * A page that failed while there is already something to read is a note on the
     * bottom of the list, not a screen of its own.
     */
    private fun failed(reason: FeedFailure, before: FeedUiState, from: PageCursor?): FeedUiState =
        when {
            from != null && before is FeedUiState.Content ->
                before.copy(loadingMore = false, moreFailed = reason.hint())

            reason is FeedFailure.Offline -> FeedUiState.Offline

            else -> FeedUiState.Error(SOMETHING_WENT_WRONG, reason.hint())
        }
}


/** Long enough to survive a rotation, short enough not to outlive the screen. */
private const val WATCHING_GRACE = 5_000L
