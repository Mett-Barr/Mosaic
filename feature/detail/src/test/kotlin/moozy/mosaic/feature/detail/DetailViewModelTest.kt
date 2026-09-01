package moozy.mosaic.feature.detail

import app.cash.turbine.test
import java.time.Instant
import java.util.TimeZone
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.ArticleResult
import moozy.mosaic.domain.model.ArticlesResult
import moozy.mosaic.domain.model.FeedFailure
import moozy.mosaic.domain.model.PageCursor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import moozy.mosaic.domain.repository.ArticleRepository
import moozy.mosaic.domain.repository.SavedArticles
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * An article that will not open has two very different reasons, and the reader
 * does different things about them: one is worth waiting out, the other is a dead
 * end. A single "could not load" screen makes that choice for them, wrongly, half
 * the time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var deviceZone: TimeZone

    @Before
    fun useTestDispatcher() {
        Dispatchers.setMain(dispatcher)
        deviceZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Taipei"))
    }

    @After
    fun releaseDispatcher() {
        TimeZone.setDefault(deviceZone)
        Dispatchers.resetMain()
    }

    @Test
    fun `an article reaches the screen as words, not as a domain object`() = runTest {
        val detail = detailOf(ArticleResult.Loaded(article()))

        detail.state.test {
            detail.open(ArticleId("39742"))
            awaitItem()

            val shown = (awaitItem() as DetailUiState.Content).article
            assertEquals("Roman Commissioning", shown.title)
            assertEquals("NASA \u00b7 31 Aug, 20:16", shown.attribution)
            // The button names where it is going, so the name is part of
            // what the screen is handed rather than something it assembles.
            assertEquals("Read the full article at NASA", shown.readFullLabel)
            assertEquals("https://science.nasa.gov/roman/", shown.url)
        }
    }

    @Test
    fun `an article that is gone says so, and does not offer another go`() = runTest {
        val detail = detailOf(ArticleResult.Failed(FeedFailure.Missing()))

        detail.state.test {
            detail.open(ArticleId("39742"))
            awaitItem()

            val failed = awaitItem() as DetailUiState.Failed
            assertEquals("This article is gone.", failed.message)
            assertEquals("It is no longer where the feed said it was.", failed.hint)
            assertFalse("a dead end is not worth another go", failed.canRetry)
        }
    }

    @Test
    fun `it says it is loading until the article arrives`() = runTest {
        val gate = CompletableDeferred<ArticleResult>()
        val detail = DetailViewModel(
            object : ArticleRepository {
                override suspend fun articles(after: PageCursor?, force: Boolean): ArticlesResult = notAsked()
                override suspend fun article(id: ArticleId) = gate.await()
            },
            FakeSaved(),
        )

        detail.state.test {
            detail.open(ArticleId("1"))
            assertEquals(DetailUiState.Loading, awaitItem())

            gate.complete(ArticleResult.Loaded(article()))

            assertTrue(awaitItem() is DetailUiState.Content)
        }
    }

    @Test
    fun `the article arrives whole`() = runTest {
        val detail = detailOf(ArticleResult.Loaded(article()))

        detail.state.test {
            detail.open(ArticleId("39742"))
            awaitItem()

            val shown = (awaitItem() as DetailUiState.Content).article
            assertEquals(article(), shown)
        }
    }

    @Test
    fun `an article that is gone and a phone with no network are different answers`() = runTest {
        val missing = detailOf(ArticleResult.Failed(FeedFailure.Server(404)))
        val offline = detailOf(ArticleResult.Failed(FeedFailure.Offline()))

        missing.state.test {
            missing.open(ArticleId("1"))
            awaitItem()
            assertEquals(FeedFailure.Server(404), (awaitItem() as DetailUiState.Failed).reason)
        }
        offline.state.test {
            offline.open(ArticleId("1"))
            awaitItem()
            assertTrue((awaitItem() as DetailUiState.Failed).reason is FeedFailure.Offline)
        }
    }

    @Test
    fun `an answer for an article the reader has left cannot replace the one they are on`() = runTest {
        val slowFirst = CompletableDeferred<ArticleResult>()
        val detail = DetailViewModel(
            object : ArticleRepository {
                override suspend fun articles(after: PageCursor?, force: Boolean): ArticlesResult = notAsked()
                override suspend fun article(id: ArticleId): ArticleResult =
                    if (id.value == "1") slowFirst.await() else ArticleResult.Loaded(article("2", "Second"))
            },
            FakeSaved(),
        )

        detail.state.test {
            detail.open(ArticleId("1"))
            assertEquals(DetailUiState.Loading, awaitItem())

            detail.open(ArticleId("2"))
            assertEquals("Second", (awaitItem() as DetailUiState.Content).article.title)

            // The first article finally answers. Nobody is waiting for it any more.
            slowFirst.complete(ArticleResult.Loaded(article("1", "First")))

            expectNoEvents()
        }
    }

    @Test
    fun `opening the same article twice does not ask twice`() = runTest {
        val repository = FakeArticle(ArticleResult.Loaded(article()))
        val detail = DetailViewModel(repository, FakeSaved())

        detail.state.test {
            detail.open(ArticleId("39742"))
            awaitItem()
            awaitItem()

            detail.open(ArticleId("39742"))
        }

        assertEquals(listOf(ArticleId("39742")), repository.asked)
    }

    @Test
    fun `retrying asks again`() = runTest {
        val repository = FakeArticle(
            ArticleResult.Failed(FeedFailure.Offline()),
            ArticleResult.Loaded(article()),
        )
        val detail = DetailViewModel(repository, FakeSaved())

        detail.state.test {
            detail.open(ArticleId("39742"))
            awaitItem()
            assertTrue(awaitItem() is DetailUiState.Failed)

            detail.retry()

            assertEquals(DetailUiState.Loading, awaitItem())
            assertTrue(awaitItem() is DetailUiState.Content)
        }
        assertEquals(listOf(ArticleId("39742"), ArticleId("39742")), repository.asked)
    }

    @Test
    fun `an article the reader kept says so`() = runTest {
        val kept = FakeSaved()
        val detail = DetailViewModel(FakeArticle(ArticleResult.Loaded(article())), kept)

        detail.state.test {
            detail.open(ArticleId("39742"))
            awaitItem()
            assertTrue("nothing is kept yet", !(awaitItem() as DetailUiState.Content).saved)

            detail.keep()

            assertTrue("the reader kept it", (awaitItem() as DetailUiState.Content).saved)
        }
        assertEquals(listOf(article()), kept.articles.value)
    }

    @Test
    fun `an article the reader let go of is dropped from what was kept`() = runTest {
        val kept = FakeSaved(article())
        val detail = DetailViewModel(FakeArticle(ArticleResult.Loaded(article())), kept)

        detail.state.test {
            detail.open(ArticleId("39742"))
            awaitItem()
            assertTrue("it starts kept", (awaitItem() as DetailUiState.Content).saved)

            detail.letGo()

            assertTrue("no longer kept", !(awaitItem() as DetailUiState.Content).saved)
        }
        assertEquals(emptyList<ArticleItem>(), kept.articles.value)
    }

    @Test
    fun `an article that was kept opens with no network at all`() = runTest {
        val kept = FakeSaved(article())
        val detail = DetailViewModel(
            FakeArticle(ArticleResult.Failed(FeedFailure.Offline())),
            kept,
        )

        detail.state.test {
            detail.open(ArticleId("39742"))
            awaitItem()

            val state = awaitItem()
            assertTrue("the kept copy should have been enough, got $state", state is DetailUiState.Content)
            assertEquals(article(), (state as DetailUiState.Content).article)
            assertTrue("and it is still marked as kept", state.saved)
        }
    }

    @Test
    fun `an article nobody kept still says there is no network`() = runTest {
        val detail = DetailViewModel(
            FakeArticle(ArticleResult.Failed(FeedFailure.Offline())),
            FakeSaved(),
        )

        detail.state.test {
            detail.open(ArticleId("39742"))
            awaitItem()

            assertTrue(awaitItem() is DetailUiState.Failed)
        }
    }

    private fun detailOf(result: ArticleResult) = DetailViewModel(FakeArticle(result), FakeSaved())

    private fun article(id: String = "39742", title: String = "Roman Commissioning") = ArticleItem(
        id = ArticleId(id),
        title = title,
        summary = "Where is Roman?",
        source = "NASA",
        url = "https://science.nasa.gov/roman/",
        imageUrl = "https://assets.science.nasa.gov/roman.jpg",
        publishedAt = Instant.parse("2026-08-31T12:16:53Z"),
    )

    private class FakeSaved(vararg initial: ArticleItem) : SavedArticles {
        val articles = MutableStateFlow(initial.toList())
        override val saved: Flow<List<ArticleItem>> = articles

        override suspend fun save(article: ArticleItem) {
            articles.value = listOf(article) + articles.value.filterNot { it.id == article.id }
        }

        override suspend fun forget(id: ArticleId) {
            articles.value = articles.value.filterNot { it.id == id }
        }
    }

    private class FakeArticle(vararg results: ArticleResult) : ArticleRepository {
        private val queue = ArrayDeque(results.toList())
        val asked = mutableListOf<ArticleId>()

        override suspend fun articles(after: PageCursor?, force: Boolean): ArticlesResult = notAsked()

        override suspend fun article(id: ArticleId): ArticleResult {
            asked += id
            yield()
            return queue.removeFirstOrNull() ?: error("the screen asked for a page nobody prepared")
        }
    }

    private companion object {
        /** The detail screen never asks for the list. */
        fun notAsked(): Nothing = error("the detail screen should not ask for the feed")
    }
}
