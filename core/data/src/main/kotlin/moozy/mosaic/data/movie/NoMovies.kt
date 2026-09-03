package moozy.mosaic.data.movie

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import moozy.mosaic.domain.model.Movie
import moozy.mosaic.domain.repository.MovieRepository

/**
 * No films, because this build has no key to ask for them with.
 *
 * The assignment's ground rule is that a clean checkout builds and runs with one
 * command, and a TMDB token cannot be committed. So the absence of one is an
 * ordinary state rather than an error: this app builds, runs, and shows a feed
 * with two kinds of thing in it instead of three.
 *
 * A repository that answers "none" rather than a null repository or a flag on a
 * screen, because the screen already knows how to draw none -- it is the same
 * case as a day whose request failed, and the weather card's absence before it.
 * The one decision made anywhere is which repository the data module builds, and
 * it is made once.
 *
 * Nothing here asks anything. A token-less build makes no TMDB request at all,
 * rather than one that comes back 401 every minute.
 */
internal object NoMovies : MovieRepository {
    override val trending: StateFlow<List<Movie>> = MutableStateFlow(emptyList())
}
