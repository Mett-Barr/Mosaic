package moozy.mosaic.feature.feed

import androidx.compose.runtime.Immutable
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import moozy.mosaic.domain.model.FeedFailure

/**
 * Which of the screens the feed can be is on show.
 *
 * Deliberately not a field on a state object: it is entirely derivable from what
 * Paging already reports, and a copy of a derivable fact is a second version of
 * it that can disagree with the first.
 */
@Immutable
sealed interface FeedPhase {

    /** Nothing to show yet, and a reason to wait. */
    data object Loading : FeedPhase

    /** The feed answered and there is genuinely nothing in it. */
    data object Empty : FeedPhase

    /** There are articles. Whatever else is happening, they are what matters. */
    data object Ready : FeedPhase

    /** Nothing to show, and a reason for it the reader can act on. */
    data class Failed(val message: String, val hint: String) : FeedPhase
}

/**
 * The screen, from what Paging says and how much it is holding.
 *
 * The order is the whole of it. Articles on screen win over everything: a
 * refresh that fails while somebody is reading must leave them reading, and the
 * version this replaces did not -- it answered with an offline screen and took
 * twenty articles away to report a request nobody had asked for.
 *
 * After that, nothing-yet and nothing-at-all are told apart by whether the
 * refresh is still running, which is the only thing that distinguishes them.
 */
internal fun feedPhase(load: CombinedLoadStates, itemCount: Int): FeedPhase = when {
    itemCount > 0 -> FeedPhase.Ready
    load.refresh is LoadState.Loading -> FeedPhase.Loading
    load.refresh is LoadState.Error -> (load.refresh as LoadState.Error).error.asPhase()
    else -> FeedPhase.Empty
}

/**
 * A [Throwable] back into something a reader can read.
 *
 * `LoadResult.Error` takes a `Throwable`, so this is where the typed failure is
 * unwrapped -- and where one that was never wrapped has to be survivable. The
 * compiler cannot check this the way a sealed `when` would; that is the price of
 * the library's signature, and the reason there is a test for it.
 */
private fun Throwable.asPhase(): FeedPhase.Failed = when (val reason = (this as? FeedRefused)?.reason) {
    is FeedFailure.Offline -> FeedPhase.Failed(
        message = "You appear to be offline.",
        hint = "The feed will be here when the connection is.",
    )

    null -> FeedPhase.Failed(SOMETHING_WENT_WRONG, "Something unexpected happened.")

    else -> FeedPhase.Failed(SOMETHING_WENT_WRONG, reason.hint())
}

/** The same words wherever the app has nothing more specific to say. */
internal const val SOMETHING_WENT_WRONG = "Something went wrong."
