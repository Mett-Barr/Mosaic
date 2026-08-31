package moozy.mosaic.feature.detail

import app.cash.turbine.test
import java.time.Instant
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
import moozy.mosaic.domain.repository.ArticleRepository
import org.junit.After
import org.junit.Assert.assertEquals
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

    @Before
    fun useTestDispatcher() = Dispatchers.setMain(dispatcher)

    @After
    fun releaseDispatcher() = Dispatchers.resetMain()

    @Test
    fun `it says it is loading until the article arrives`() = runTest {
        val gate = CompletableDeferred<ArticleResult>()
        val detail = DetailViewModel(
            object : ArticleRepository {
                override suspend fun articles(after: PageCursor?): ArticlesResult = notAsked()
                override suspend fun article(id: ArticleId) = gate.await()
            },
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

            val content = awaitItem() as DetailUiState.Content
            assertEquals("Roman Commissioning", content.article.title)
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
    fun `opening the same article twice does not ask twice`() = runTest {
        val repository = FakeArticle(ArticleResult.Loaded(article()))
        val detail = DetailViewModel(repository)

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
        val detail = DetailViewModel(repository)

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

    private fun detailOf(result: ArticleResult) = DetailViewModel(FakeArticle(result))

    private fun article() = ArticleItem(
        id = ArticleId("39742"),
        title = "Roman Commissioning",
        summary = "Where is Roman?",
        source = "NASA",
        url = "https://science.nasa.gov/roman/",
        imageUrl = null,
        publishedAt = Instant.parse("2026-08-31T12:16:53Z"),
    )

    private class FakeArticle(vararg results: ArticleResult) : ArticleRepository {
        private val queue = ArrayDeque(results.toList())
        val asked = mutableListOf<ArticleId>()

        override suspend fun articles(after: PageCursor?): ArticlesResult = notAsked()

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
