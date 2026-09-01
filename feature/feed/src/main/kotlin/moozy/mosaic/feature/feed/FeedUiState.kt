package moozy.mosaic.feature.feed

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

/**
 * Everything the feed can be showing.
 *
 * The four the assignment asks for are separate cases rather than fields on one
 * state, because a state with `articles: List` and `error: String?` has states
 * that make no sense -- an error with articles, a loading spinner with an error --
 * and something has to decide what those mean. Here nothing has to: they cannot
 * be written down.
 */
@Immutable
sealed interface FeedUiState {

    /** Nothing to show yet, and a reason to wait. */
    data object Loading : FeedUiState

    /** The feed loaded and there is genuinely nothing in it. */
    data object Empty : FeedUiState

    /** The request never got out. Worth offering to try again. */
    data object Offline : FeedUiState

    /**
     * It got out, and what came back was not usable.
     *
     * Two sentences rather than a failure, because two sentences is what
     * the screen draws. Which two is a decision worth a test, and a
     * decision made inside a composable is not one this project can run.
     */
    data class Error(val message: String, val hint: String) : FeedUiState

    /**
     * Articles, and what is happening to them.
     *
     * [weather] is null when there is no reading to show, which includes the case
     * where asking for one failed. A card nobody can fill is not a card, and it is
     * not worth an error either: the reader came for the articles.
     *
     * [moreFailed] is how a failed next page is told to the reader without taking
     * away what they were already reading. Replacing the list with an error screen
     * because page four did not arrive is losing three pages of work to punish
     * someone for scrolling.
     */
    data class Content(
        val articles: ImmutableList<ArticleRow>,
        val canLoadMore: Boolean,
        val loadingMore: Boolean = false,
        /** A refresh the reader asked for, running with the list still on screen. */
        val refreshing: Boolean = false,
        val moreFailed: String? = null,
        val weather: WeatherHeadline? = null,
    ) : FeedUiState
}
