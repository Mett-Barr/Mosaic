package moozy.mosaic.data.movie

import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import moozy.mosaic.domain.model.Movie
import moozy.mosaic.domain.model.MovieId
import moozy.mosaic.domain.model.TrendingMovies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * "Once a day" is only a policy if the day survives the app being closed.
 *
 * Held in a field it would have meant once per launch, which is the articles'
 * rule wearing the films' name -- and a reader who opens this app five times
 * before lunch would have paid for five identical lists.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FileTrendingStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun store(file: File) = FileTrendingStore(file, UnconfinedTestDispatcher())

    @Test
    fun `a day written down is there for the next launch`() = runTest {
        val file = folder.newFile("trending.json")

        store(file).write(trending())

        val read = store(file).read()
        assertEquals(LocalDate.parse("2026-09-02"), read?.forDay)
        assertEquals(listOf("How to Train Your Dragon", "A Film Nobody Has Voted On"), read?.movies?.map { it.title })
    }

    @Test
    fun `everything a card draws survives the round trip`() = runTest {
        val file = folder.newFile("trending.json")

        store(file).write(trending())

        val film = store(file).read()?.movies?.first()
        assertEquals(MovieId(1087192), film?.id)
        assertEquals(8.117, film?.rating!!, 0.0001)
        assertEquals("https://image.tmdb.org/t/p/w342/q5pXRYTycaeW6dEgsCrd4mYPmxM.jpg", film.posterUrl)
    }

    @Test
    fun `a film with no score keeps having no score rather than gaining a zero`() = runTest {
        val file = folder.newFile("trending.json")

        store(file).write(trending())

        assertNull(store(file).read()?.movies?.last()?.rating)
    }

    @Test
    fun `a file nobody has written to holds no day`() = runTest {
        assertNull(store(File(folder.root, "never-written.json")).read())
    }

    @Test
    fun `a mangled file reads as nothing rather than as a crash`() = runTest {
        val file = folder.newFile("trending.json")
        file.writeText("half a file")

        assertNull(store(file).read())
    }

    @Test
    fun `a file whose values the domain refuses reads as nothing`() = runTest {
        // Valid JSON, and a film with no title. Parsing succeeds and the domain
        // is the one that says no, which is a different throw from the one above.
        val file = folder.newFile("trending.json")
        file.writeText(
            """{"for_day": "2026-09-02", "movies": [{"id": 1, "title": "  ", "rating": 5.0}]}""",
        )

        assertNull(store(file).read())
    }

    @Test
    fun `a file holding a day that is not a date reads as nothing`() = runTest {
        val file = folder.newFile("trending.json")
        file.writeText("""{"for_day": "the day before yesterday", "movies": []}""")

        assertNull(store(file).read())
    }

    private fun trending() = TrendingMovies(
        movies = listOf(
            Movie(
                id = MovieId(1087192),
                title = "How to Train Your Dragon",
                rating = 8.117,
                posterUrl = "https://image.tmdb.org/t/p/w342/q5pXRYTycaeW6dEgsCrd4mYPmxM.jpg",
            ),
            Movie(
                id = MovieId(803796),
                title = "A Film Nobody Has Voted On",
                rating = null,
                posterUrl = null,
            ),
        ),
        forDay = LocalDate.parse("2026-09-02"),
    )
}
