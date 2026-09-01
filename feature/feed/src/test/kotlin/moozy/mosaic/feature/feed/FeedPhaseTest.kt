package moozy.mosaic.feature.feed

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import moozy.mosaic.domain.model.FeedFailure
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which screen the reader is looking at, worked out from what Paging says.
 *
 * A function rather than a `when` inside a composable, because this project has
 * no screen tests: a decision made in a composable is a decision nothing here
 * can check. Three of the decisions below are ones it would be easy to get
 * wrong, and one of them is a bug the hand-written version had.
 */
class FeedPhaseTest {

    @Test
    fun `articles on screen stay on screen when a refresh fails`() {
        val phase = feedPhase(states(refresh = failed(FeedFailure.Offline())), itemCount = 20)

        // The version this replaces answered Offline here, which took twenty
        // articles away from somebody who was reading them to say the refresh
        // they did not ask for had not arrived.
        assertEquals(FeedPhase.Ready, phase)
    }

    @Test
    fun `nothing yet is not the same as nothing at all`() {
        assertEquals(FeedPhase.Loading, feedPhase(states(refresh = LoadState.Loading), itemCount = 0))
        assertEquals(FeedPhase.Empty, feedPhase(states(), itemCount = 0))
    }

    @Test
    fun `a phone with no connection says so, rather than saying something went wrong`() {
        val phase = feedPhase(states(refresh = failed(FeedFailure.Offline())), itemCount = 0)

        assertEquals(
            FeedPhase.Failed(
                message = "You appear to be offline.",
                hint = "The feed will be here when the connection is.",
            ),
            phase,
        )
    }

    @Test
    fun `anything else says what went wrong as far as it knows`() {
        val phase = feedPhase(states(refresh = failed(FeedFailure.Server(500))), itemCount = 0)

        assertEquals(
            FeedPhase.Failed(
                message = "Something went wrong.",
                hint = "The feed is having trouble (error 500).",
            ),
            phase,
        )
    }

    @Test
    fun `a failure that arrived without a reason still says something`() {
        // LoadResult.Error takes a Throwable, so nothing stops one arriving that
        // this app did not wrap. It must not be a crash and must not be silence.
        val phase = feedPhase(states(refresh = LoadState.Error(IllegalStateException("?"))), itemCount = 0)

        assertEquals("Something went wrong.", (phase as FeedPhase.Failed).message)
    }

    private fun failed(reason: FeedFailure) = LoadState.Error(FeedRefused(reason))

    private fun states(
        refresh: LoadState = LoadState.NotLoading(endOfPaginationReached = false),
        append: LoadState = LoadState.NotLoading(endOfPaginationReached = false),
    ) = CombinedLoadStates(
        refresh = refresh,
        prepend = LoadState.NotLoading(endOfPaginationReached = true),
        append = append,
        source = LoadStates(
            refresh = refresh,
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = append,
        ),
        mediator = null,
    )
}
