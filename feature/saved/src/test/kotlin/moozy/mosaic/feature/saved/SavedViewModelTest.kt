package moozy.mosaic.feature.saved

import app.cash.turbine.test
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.repository.SavedArticles
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The screen a reader gets to when there is no network. It has one job that the
 * feed does not: showing something that is already here, without asking anybody
 * for it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedViewModelTest {

    @Before
    fun useTestDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun releaseDispatcher() = Dispatchers.resetMain()

    @Test
    fun `a kept article reaches the screen as words, not as a domain object`() = runTest {
        SavedViewModel(FakeSaved(article(1))).state.test {
            val row = (awaitItem() as SavedUiState.Content).articles.single()

            assertEquals("Article 1", row.title)
            assertEquals("NASA", row.source)
        }
    }

    @Test
    fun `a reader with nothing kept is told so, not shown an empty list`() = runTest {
        SavedViewModel(FakeSaved()).state.test {
            assertEquals(SavedUiState.Empty, awaitItem())
        }
    }

    @Test
    fun `what was kept is what is shown`() = runTest {
        val model = SavedViewModel(FakeSaved(article(2), article(1)))

        model.state.test {
            assertEquals(listOf("2", "1"), (awaitItem() as SavedUiState.Content).articles.map { it.id.value })
        }
    }

    @Test
    fun `letting go of the last one leaves the empty screen, not a blank list`() = runTest {
        val kept = FakeSaved(article(1))
        val model = SavedViewModel(kept)

        model.state.test {
            awaitItem()

            model.letGo(ArticleId("1"))

            assertEquals(SavedUiState.Empty, awaitItem())
        }
        assertEquals(emptyList<ArticleItem>(), kept.articles.value)
    }

    @Test
    fun `something kept elsewhere turns up here without being asked for`() = runTest {
        val kept = FakeSaved()
        val model = SavedViewModel(kept)

        model.state.test {
            assertEquals(SavedUiState.Empty, awaitItem())

            kept.save(article(9))

            assertEquals(listOf("9"), (awaitItem() as SavedUiState.Content).articles.map { it.id.value })
        }
    }

    private fun article(id: Int) = ArticleItem(
        id = ArticleId("$id"),
        title = "Article $id",
        summary = "",
        source = "NASA",
        url = "https://example.com/$id",
        imageUrl = null,
        publishedAt = Instant.parse("2026-08-31T10:00:00Z"),
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
}
