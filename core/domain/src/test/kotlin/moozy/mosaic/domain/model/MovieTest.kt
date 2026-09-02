package moozy.mosaic.domain.model

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The third source in this feed, and the third answer to "how long is this still
 * true".
 *
 * The weather carries its own step and the articles carry none, so neither of
 * those rules could be borrowed here. What trending films carry instead is a day:
 * the endpoint the app asks is `/trending/movie/day`, so the unit the source
 * recomputes on is named in the address rather than in the response. That is the
 * whole of the rule, and it is here because a rule kept in the repository is a
 * rule only a network test can look at.
 */
class MovieTest {

    @Test
    fun `the day's list is still the day's list for the rest of that day`() {
        val trending = trending(LocalDate.parse("2026-09-02"))

        assertTrue(trending.stillCurrentOn(LocalDate.parse("2026-09-02")))
    }

    @Test
    fun `a new day is a new list, because a day is what the source recomputes`() {
        val trending = trending(LocalDate.parse("2026-09-02"))

        assertFalse(trending.stillCurrentOn(LocalDate.parse("2026-09-03")))
    }

    @Test
    fun `a clock that went backwards is not a reason to ask again`() {
        // A phone flown west over a date line, or a clock corrected after running
        // ahead. The list held was computed for a later day than the one now
        // being had, and asking again cannot produce anything the app does not
        // already have -- so it is still current, not stale.
        val trending = trending(LocalDate.parse("2026-09-02"))

        assertTrue(trending.stillCurrentOn(LocalDate.parse("2026-08-31")))
    }

    @Test
    fun `a film with no title is not a film`() {
        assertThrows(IllegalArgumentException::class.java) { movie(title = "   ") }
    }

    @Test
    fun `a score off the scale the source uses is not a score`() {
        // TMDB rates out of ten. A number outside it means the field was read
        // from somewhere other than where this app thinks it is reading.
        assertThrows(IllegalArgumentException::class.java) { movie(rating = 11.0) }
        assertThrows(IllegalArgumentException::class.java) { movie(rating = -0.5) }
    }

    private fun trending(day: LocalDate) = TrendingMovies(movies = listOf(movie()), forDay = day)

    private fun movie(title: String = "How to Train Your Dragon", rating: Double? = 8.117) = Movie(
        id = MovieId(1087192),
        title = title,
        rating = rating,
        posterUrl = null,
    )
}
