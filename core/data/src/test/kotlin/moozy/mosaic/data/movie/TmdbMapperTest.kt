package moozy.mosaic.data.movie

import kotlinx.serialization.SerializationException
import moozy.mosaic.domain.model.MovieId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * A trending film arrives as a row that is mostly not about the film: genre ids,
 * a popularity score, a backdrop, the original title in its own alphabet. Three
 * fields of it reach a reader, and one of those three is not in the response at
 * all -- the poster is a path, and the address it hangs off is this app's to
 * build.
 *
 * The fixture is hand-written in the shape TMDB's own reference documents for
 * `/3/trending/movie/day`, not captured: reaching the real endpoint needs a token,
 * and a token has no business being in a file this repository keeps.
 */
class TmdbMapperTest {

    private fun payload(name: String): String =
        checkNotNull(javaClass.getResource(name)) { "missing fixture $name" }.readText()

    private fun map(json: String) = TmdbJson
        .decodeFromString(TrendingPageDto.serializer(), json)
        .toMovies()

    @Test
    fun `a day of trending films becomes films somebody can look at`() {
        val movies = map(payload("/tmdb-trending-day.json"))

        assertEquals(MovieId(1087192), movies.first().id)
        assertEquals("How to Train Your Dragon", movies.first().title)
    }

    @Test
    fun `the score arrives as the source's own number, not as a rounded one`() {
        // Rounding 8.117 to 8.1 is how a card reads it, and a card is not here.
        // The domain holds what the source said, the same way an article holds an
        // instant rather than "2 hours ago".
        val movies = map(payload("/tmdb-trending-day.json"))

        assertEquals(8.117, movies.first().rating!!, 0.0001)
    }

    @Test
    fun `a poster path becomes an address, because a path is not one`() {
        val movies = map(payload("/tmdb-trending-day.json"))

        assertEquals(
            "https://image.tmdb.org/t/p/w342/q5pXRYTycaeW6dEgsCrd4mYPmxM.jpg",
            movies.first().posterUrl,
        )
    }

    @Test
    fun `a film with no poster on file has no address rather than half of one`() {
        // The base and the size are always there, so a path that is absent would
        // still concatenate into something that looks like a URL and 404s.
        val movies = map(payload("/tmdb-trending-day.json"))

        assertNull(movies.single { it.id == MovieId(1234821) }.posterUrl)
    }

    @Test
    fun `a film nobody has voted on has no score rather than the worst one`() {
        // TMDB sends vote_average 0 for a film with no votes, and 0 out of ten is
        // a sentence about the film. A day-old release trending on its opening
        // day is exactly the row this happens to.
        val movies = map(payload("/tmdb-trending-day.json"))

        assertNull(movies.single { it.id == MovieId(803796) }.rating)
    }

    @Test
    fun `a row this app cannot use costs that row and not the strip`() {
        // The fourth row in the fixture has no title. Decoding the results as a
        // list of films would have lost the other three with it.
        val movies = map(payload("/tmdb-trending-day.json"))

        assertEquals(3, movies.size)
        assertEquals(
            listOf(MovieId(1087192), MovieId(1234821), MovieId(803796)),
            movies.map { it.id },
        )
    }

    @Test
    fun `a different day is a different list`() {
        // The same fixture asserted twice proves a mapper that returns constants.
        // Nothing here shares a value with the captured shape above.
        val movies = map(
            """
            {"page": 1, "total_pages": 1, "total_results": 1,
             "results": [{"id": 42, "title": "Another Film Entirely",
                          "poster_path": "/zzz.jpg", "vote_average": 3.25, "vote_count": 8}]}
            """.trimIndent(),
        )

        assertEquals(MovieId(42), movies.single().id)
        assertEquals("Another Film Entirely", movies.single().title)
        assertEquals(3.25, movies.single().rating!!, 0.0001)
        assertEquals("https://image.tmdb.org/t/p/w342/zzz.jpg", movies.single().posterUrl)
    }

    @Test
    fun `a response with no results in it is broken rather than empty`() {
        // "Nothing is trending" and "the answer was not the answer" are different
        // days, and only one of them is worth writing down as this day's list.
        assertThrows(SerializationException::class.java) {
            map("""{"page": 1, "total_pages": 0, "total_results": 0}""")
        }
    }
}
