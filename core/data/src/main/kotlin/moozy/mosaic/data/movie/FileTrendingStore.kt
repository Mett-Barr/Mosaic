package moozy.mosaic.data.movie

import java.io.File
import java.io.IOException
import java.time.DateTimeException
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import moozy.mosaic.domain.model.Movie
import moozy.mosaic.domain.model.MovieId
import moozy.mosaic.domain.model.TrendingMovies

/**
 * The last day's list, written down.
 *
 * One small file read whole and written whole, the same shape as the weather
 * reading beside it. Losing it costs one request, so a file that will not parse
 * reads as nothing at all and the next answer writes over it.
 */
internal class FileTrendingStore(
    private val file: File,
    private val io: CoroutineDispatcher,
) : TrendingStore {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Why the day could not be read back, if it could not be.
     *
     * Nothing is shown to the reader about it -- a lost day costs one request.
     * Keeping the reason still beats discarding it: a file that never loads makes
     * every launch pay for a list it already had.
     */
    internal var lastProblem: String? = null
        private set

    override suspend fun read(): TrendingMovies? = withContext(io) {
        try {
            file.takeIf { it.exists() }
                ?.readText()
                ?.takeIf { it.isNotBlank() }
                ?.let { json.decodeFromString(StoredTrending.serializer(), it) }
                ?.toTrending()
        } catch (unreadable: SerializationException) {
            // First, because SerializationException *is* an
            // IllegalArgumentException: catching the general one above this would
            // leave this branch unreachable and its message unwritten.
            lastProblem = "the stored day could not be read: ${unreadable.message}"
            file.delete()
            null
        } catch (refused: IllegalArgumentException) {
            // Valid JSON whose values the domain will not hold: a blank title, an
            // id that is not positive, a score off the scale. Parsing succeeded,
            // so neither catch around it sees these.
            lastProblem = "the stored day held values this app cannot use: ${refused.message}"
            file.delete()
            null
        } catch (unreadable: DateTimeException) {
            // A stored day that is not a date. Not an IllegalArgumentException,
            // so it needs its own branch or it leaves read() and takes the
            // caller with it.
            lastProblem = "the stored day is not a date: ${unreadable.message}"
            file.delete()
            null
        } catch (unreachable: IOException) {
            lastProblem = "the stored day could not be opened: ${unreachable.message}"
            null
        }
    }

    /**
     * Best effort, like the weather reading. A list that arrived must reach the
     * reader whether or not it can also be written down -- what a failure here
     * costs is tomorrow's saved request, not today's strip.
     */
    override suspend fun write(trending: TrendingMovies) {
        withContext(io) {
            try {
                file.parentFile?.mkdirs()
                // Through a second file and a rename: writeText empties the
                // destination before it fills it, and a process killed in between
                // would leave a file that is neither day. A rename cannot be
                // half-done.
                val writing = File(file.parentFile, file.name + ".writing")
                writing.writeText(json.encodeToString(StoredTrending.serializer(), trending.stored()))
                if (!writing.renameTo(file)) {
                    // Some filesystems refuse a rename onto a name that is
                    // already taken, so the old day has to go first -- and that
                    // is the one moment this is not atomic, because between
                    // these two lines there is no day on disk at all. The
                    // sentence above is still true of each rename; it is this
                    // pair that is not.
                    file.delete()
                    if (!writing.renameTo(file)) {
                        // Neither day survived. Say so, and take the half of a
                        // file with it: a `.writing` left in the cache
                        // directory is a stranger nothing will ever read, and
                        // the next successful write would have to clear it
                        // before it could rename onto it anyway.
                        lastProblem = "the day could not be put in place of the last one"
                        writing.delete()
                    }
                }
            } catch (unwritable: IOException) {
                lastProblem = "the day could not be written down: ${unwritable.message}"
            }
        }
    }
}

/**
 * The shape on disk, which is deliberately not the domain's.
 *
 * The films are stored as this app already made them -- the poster as a whole
 * address rather than as the path it arrived as -- because what is written down
 * is the answer, not the response. A day read back has nothing left to map.
 */
@Serializable
private data class StoredTrending(
    @SerialName("for_day") val forDay: String,
    val movies: List<StoredMovie>,
)

@Serializable
private data class StoredMovie(
    val id: Int,
    val title: String,
    val rating: Double? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
)

private fun TrendingMovies.stored() = StoredTrending(
    forDay = forDay.toString(),
    movies = movies.map { it.stored() },
)

private fun Movie.stored() = StoredMovie(
    id = id.value,
    title = title,
    rating = rating,
    posterUrl = posterUrl,
)

private fun StoredTrending.toTrending() = TrendingMovies(
    movies = movies.map { it.toMovie() },
    forDay = LocalDate.parse(forDay),
)

private fun StoredMovie.toMovie() = Movie(
    id = MovieId(id),
    title = title,
    rating = rating,
    posterUrl = posterUrl,
)
