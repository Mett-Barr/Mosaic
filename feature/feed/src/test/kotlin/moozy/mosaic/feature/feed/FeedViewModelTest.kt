package moozy.mosaic.feature.feed

import androidx.paging.testing.asSnapshot
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.ArticleResult
import moozy.mosaic.domain.model.ArticlesResult
import moozy.mosaic.domain.model.ForecastDay
import moozy.mosaic.domain.model.Movie
import moozy.mosaic.domain.model.MovieId
import moozy.mosaic.domain.model.PageCursor
import moozy.mosaic.domain.model.Sky
import moozy.mosaic.domain.model.Weather
import moozy.mosaic.domain.repository.ArticleRepository
import moozy.mosaic.domain.repository.MovieRepository
import moozy.mosaic.domain.repository.WeatherRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * What is left for the view model to answer for.
 *
 * Most of what this file used to assert now belongs elsewhere: which screen is
 * showing is [FeedPhaseTest]'s, and what one generation of the list will and
 * will not hand over is [ArticlePagingSourceTest]'s. What remains here is the
 * part Paging does not do -- when work begins, what the words look like, and
 * what a pull actually causes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    private lateinit var deviceZone: TimeZone

    @Before
    fun useTestDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // The times this screen shows are the reader's own, so a test asserting
        // one has to say whose clock it is reading.
        deviceZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Taipei"))
    }

    @After
    fun releaseDispatcher() {
        TimeZone.setDefault(deviceZone)
        Dispatchers.resetMain()
    }

    @Test
    fun `nothing is asked for until somebody is watching`() = runTest {
        val articles = CountingArticles(page(1))

        FeedViewModel(articles, FakeWeather(), FakeMovies())
        runCurrent()

        // Constructing a view model is not a reason to spend somebody's data,
        // and this one is built once and then alive for the whole session.
        assertEquals("nothing was watching", 0, articles.asked)
    }

    @Test
    fun `an article reaches the screen as words, not as a domain object`() = runTest {
        val feed = FeedViewModel(CountingArticles(page(1)), FakeWeather(), FakeMovies())

        val shown = feed.stories.asSnapshot().single()

        assertEquals("Article 1", shown.title)
        // One string, because it is one line on the card -- and because deciding
        // how a time reads is not a decision a composable can be asked about
        // without a device.
        assertEquals("Somewhere · 31 Aug, 18:00", shown.attribution)
    }

    @Test
    fun `the weather reaches the screen as words too`() = runTest {
        val weather = FakeWeather(reading())
        val feed = FeedViewModel(CountingArticles(page(1)), weather, FakeMovies())

        val watching = launch { feed.weather.collect {} }
        runCurrent()
        val shown = feed.weather.value
        watching.cancel()

        assertEquals("Taipei", shown?.place)
        assertEquals("26°", shown?.temperature)
        assertEquals("Cloudy · 32° / 25°", shown?.conditions)
    }

    @Test
    fun `the three days ahead reach the screen as a weekday and a temperature`() = runTest {
        val weather = FakeWeather(reading().copy(days = threeDays()))
        val feed = FeedViewModel(CountingArticles(page(1)), weather, FakeMovies())

        val watching = launch { feed.weather.collect {} }
        runCurrent()
        val shown = feed.weather.value
        watching.cancel()

        // 2026-09-01 is a Tuesday, and it is a Tuesday in English wherever the
        // phone thinks it is -- the same rule the timestamps follow.
        assertEquals(listOf("Tue", "Wed", "Thu"), shown?.days?.map { it.day })
        // The high, and only the high: a column with two numbers in it is a
        // table, and the strip is there to say which way the week is going.
        assertEquals(listOf("32°", "29°", "33°"), shown?.days?.map { it.temperature })
        assertEquals(listOf(Sky.CLOUDY, Sky.RAIN, Sky.CLEAR), shown?.days?.map { it.sky })
    }

    @Test
    fun `a reading with no days ahead is a card with no strip under it`() = runTest {
        val feed = FeedViewModel(CountingArticles(page(1)), FakeWeather(reading()), FakeMovies())

        val watching = launch { feed.weather.collect {} }
        runCurrent()
        val shown = feed.weather.value
        watching.cancel()

        assertEquals("26°", shown?.temperature)
        assertEquals(emptyList<DayHeadline>(), shown?.days)
    }

    @Test
    fun `a film reaches the screen as words too`() = runTest {
        val feed = FeedViewModel(CountingArticles(page(1)), FakeWeather(), FakeMovies(film()))

        val watching = launch { feed.movies.collect {} }
        runCurrent()
        val shown = feed.movies.value.single()
        watching.cancel()

        assertEquals("How to Train Your Dragon", shown.title)
        // One decimal, because that is how the source's own site says it and
        // because "8.117 out of ten" is a claim about a film nobody is making.
        // The rounding happens here rather than in the mapper: what a number
        // looks like is a decision about this screen.
        assertEquals("8.1", shown.rating)
        // Against the fixture rather than against the string spelled out again:
        // how that URL is built is `TmdbMovies.toMovie`'s decision and is
        // asserted there, so repeating it here would only say that a literal
        // equals itself. What is left worth saying is that this layer does not
        // drop it -- `MovieStrip` draws a grey placeholder tile for a null, so a
        // poster lost in the mapping is a strip of empty rectangles that no
        // other test in this module would notice.
        assertEquals(film().posterUrl, shown.posterUrl)
    }

    @Test
    fun `a film nobody has voted on reaches the screen with no score at all`() = runTest {
        val feed = FeedViewModel(
            CountingArticles(page(1)),
            FakeWeather(),
            FakeMovies(film().copy(rating = null)),
        )

        val watching = launch { feed.movies.collect {} }
        runCurrent()
        val shown = feed.movies.value.single()
        watching.cancel()

        // Not "0.0", and not "-": a badge nobody can fill is a badge that is
        // not drawn, the same way a card nobody can fill is not drawn.
        assertNull(shown.rating)
    }

    @Test
    fun `no films is no strip rather than an empty one`() = runTest {
        val feed = FeedViewModel(CountingArticles(page(1)), FakeWeather(), FakeMovies())

        val watching = launch { feed.movies.collect {} }
        runCurrent()
        val shown = feed.movies.value
        watching.cancel()

        // The same answer the weather gives when there is no reading: absent.
        // A token nobody configured lands here too, which is why the screen has
        // one case to draw rather than two.
        assertEquals(emptyList<MoviePoster>(), shown)
    }

    @Test
    fun `pulling asks the source again`() = runTest {
        val articles = CountingArticles(page(1), page(2))
        val feed = FeedViewModel(articles, FakeWeather(), FakeMovies())
        feed.stories.asSnapshot()

        feed.refresh()
        val second = feed.stories.asSnapshot()

        // A pull builds a new generation, which asks for the top of the list
        // again. The old one is discarded rather than added to.
        assertEquals(2, articles.asked)
        assertEquals(listOf("2"), second.map { it.id.value })
    }

    private fun page(vararg ids: Int) = ArticlesResult.Loaded(
        articles = ids.map { article(it) },
        next = null,
    )

    private fun article(id: Int) = ArticleItem(
        id = ArticleId("$id"),
        title = "Article $id",
        summary = "",
        source = "Somewhere",
        url = "https://example.com/$id",
        imageUrl = null,
        publishedAt = Instant.parse("2026-08-31T10:00:00Z"),
    )

    private fun threeDays() = listOf(
        ForecastDay(LocalDate.parse("2026-09-01"), high = 32, low = 25, sky = Sky.CLOUDY),
        ForecastDay(LocalDate.parse("2026-09-02"), high = 29, low = 24, sky = Sky.RAIN),
        ForecastDay(LocalDate.parse("2026-09-03"), high = 33, low = 26, sky = Sky.CLEAR),
    )

    private fun film() = Movie(
        id = MovieId(1087192),
        title = "How to Train Your Dragon",
        rating = 8.117,
        posterUrl = "https://image.tmdb.org/t/p/w342/poster.jpg",
    )

    private fun reading() = Weather(
        place = "Taipei",
        temperature = 26,
        high = 32,
        low = 25,
        sky = Sky.CLOUDY,
        measuredAt = Instant.parse("2026-09-01T02:30:00Z"),
        stepsEvery = Duration.ofMinutes(15),
    )

    private class CountingArticles(vararg pages: ArticlesResult) : ArticleRepository {
        private val queue = ArrayDeque(pages.toList())
        var asked = 0
            private set

        override suspend fun articles(after: PageCursor?): ArticlesResult {
            asked++
            return queue.removeFirstOrNull() ?: error("the feed asked for a page nobody prepared")
        }

        override suspend fun article(id: ArticleId): ArticleResult =
            error("the list does not ask about one article")
    }

    private class FakeWeather(reading: Weather? = null) : WeatherRepository {
        override val current: StateFlow<Weather?> = MutableStateFlow(reading)
    }

    private class FakeMovies(vararg films: Movie) : MovieRepository {
        override val trending: StateFlow<List<Movie>> = MutableStateFlow(films.toList())
    }
}
