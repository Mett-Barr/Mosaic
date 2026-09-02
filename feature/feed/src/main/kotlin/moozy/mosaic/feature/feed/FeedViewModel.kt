package moozy.mosaic.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import moozy.mosaic.domain.repository.ArticleRepository
import moozy.mosaic.domain.repository.WeatherRepository

/**
 * What the feed is showing, in two streams that have nothing to say to each
 * other.
 *
 * [stories] is the list, and it is a stream of its own rather than a field on a
 * state object because a `PagingData` may be used only once -- it is a handle on
 * something in flight, not a value, and a data class reads its fields many times
 * over.
 *
 * [weather] is the other source, kept apart for the same reason it is drawn
 * apart: a card at the top of the screen has nothing to do with whether page
 * four arrived.
 *
 * Nothing here holds "loading" or "empty" or "failed". Paging already reports
 * what its loads are doing, and [feedPhase] turns that into a screen where the
 * screen is drawn; a copy kept here would be a second version of one fact.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val articles: ArticleRepository,
    weather: WeatherRepository,
) : ViewModel() {

    /**
     * Reasons to build a new generation of the list. No payload: there is
     * nothing to say beyond that one is wanted.
     *
     * Conflated, so a reader who pulls three times in a second refreshes once.
     */
    private val reloads = Channel<Unit>(Channel.CONFLATED)

    /**
     * A generation when collection starts, and another on every reload.
     *
     * `onStart` is not an event in disguise: it is the hook for the act of
     * collecting, which is exactly the first of the two reasons a generation is
     * wanted. `cachedIn` keeps the loaded pages in this view model's scope, so
     * coming back to the screen finds the list where it was left rather than
     * asking for it again.
     */
    val stories: Flow<PagingData<ArticleRow>> = reloads.receiveAsFlow()
        .onStart { emit(Unit) }
        .flatMapLatest { newGeneration() }
        .cachedIn(viewModelScope)

    val weather: StateFlow<WeatherHeadline?> = weather.current
        .map { reading -> reading?.headline() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(WATCHING_GRACE), null)

    /** The reader asked for a newer list. */
    fun refresh() {
        reloads.trySend(Unit)
    }

    private fun newGeneration(): Flow<PagingData<ArticleRow>> =
        Pager(
            PagingConfig(
                pageSize = PAGE_SIZE,
                // Left alone this is three times the page size, and the source
                // honours `limit` only on the first request before copying it
                // into every link it hands back -- so the default would quietly
                // make every page sixty articles instead of twenty, on somebody
                // else's data plan.
                initialLoadSize = PAGE_SIZE,
                enablePlaceholders = false,
            ),
        ) {
            ArticlePagingSource(articles)
        }.flow.map { page -> page.map { article -> article.row() } }
}

private const val PAGE_SIZE = 20

/** Long enough to survive a rotation, short enough not to outlive the screen. */
private const val WATCHING_GRACE = 5_000L
