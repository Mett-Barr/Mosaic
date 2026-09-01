package moozy.mosaic.data.saved

import java.io.File
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * "Saved articles are readable offline after first load" is a promise about a
 * second run of the app, not about a second call in the same run. So the tests
 * that matter here are the ones that throw the object away and read the file
 * again with a new one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FileSavedArticlesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun store(file: File = folder.newFile("saved.json")) =
        FileSavedArticles(file, UnconfinedTestDispatcher())

    @Test
    fun `a saved article with a time that is not a time is left out, not thrown`() = runTest {
        val file = folder.newFile("saved.json")
        file.writeText(
            """[{"id":"1","title":"T","summary":"S","source":"NASA",
               "url":"https://example.test/1","published_at":"last Tuesday"}]"""
                .trimIndent(),
        )

        val store = FileSavedArticles(file, UnconfinedTestDispatcher())

        assertEquals(emptyList<ArticleItem>(), store.saved.first())
    }

    @Test
    fun `a saved article the domain refuses is left out, not thrown`() = runTest {
        val file = folder.newFile("saved.json")
        file.writeText(
            """[{"id":"","title":"T","summary":"S","source":"NASA",
               "url":"https://example.test/1","published_at":"2026-09-01T12:00:00Z"}]"""
                .trimIndent(),
        )

        val store = FileSavedArticles(file, UnconfinedTestDispatcher())

        assertEquals(emptyList<ArticleItem>(), store.saved.first())
    }

    @Test
    fun `a save that cannot be written leaves the list that was already there`() = runTest {
        val file = folder.newFile("saved.json")
        val store = FileSavedArticles(file, UnconfinedTestDispatcher())
        store.save(article(1))
        // Occupy the path every write has to go through, so that the next one
        // cannot get there. A process killed mid-write is the same shape of
        // problem: the destination must not be what is being written into.
        File(folder.root, "saved.json.writing").mkdir()

        store.save(article(2))

        assertEquals(listOf(article(1)), store.saved.first())
        val laterRun = FileSavedArticles(file, UnconfinedTestDispatcher())
        assertEquals(listOf(article(1)), laterRun.saved.first())
    }

    @Test
    fun `a save that cannot be written says so rather than throwing`() = runTest {
        val file = folder.newFile("saved.json")
        val store = FileSavedArticles(file, UnconfinedTestDispatcher())
        File(folder.root, "saved.json.writing").mkdir()

        store.save(article(1))

        assertNotNull(store.lastProblem.first())
    }

    @Test
    fun `a finished save leaves nothing half-written behind`() = runTest {
        val file = folder.newFile("saved.json")

        store(file).save(article(1))

        assertEquals(listOf("saved.json"), folder.root.list()?.sorted())
    }

    @Test
    fun `an article that was saved can be read back`() = runTest {
        val store = store()

        store.save(article(1))

        assertEquals(listOf(article(1)), store.saved.first())
    }

    @Test
    fun `what was saved is still there for a new reader of the same file`() = runTest {
        val file = folder.newFile("saved.json")
        FileSavedArticles(file, UnconfinedTestDispatcher()).save(article(1))

        val laterRun = FileSavedArticles(file, UnconfinedTestDispatcher())

        assertEquals(listOf(article(1)), laterRun.saved.first())
    }

    @Test
    fun `saving the same article twice keeps one copy`() = runTest {
        val store = store()

        store.save(article(1))
        store.save(article(1).copy(title = "The headline changed"))

        val saved = store.saved.first()
        assertEquals(1, saved.size)
        assertEquals("The headline changed", saved.single().title)
    }

    @Test
    fun `the most recently saved article comes first`() = runTest {
        val store = store()

        store.save(article(1))
        store.save(article(2))
        store.save(article(3))

        assertEquals(listOf("3", "2", "1"), store.saved.first().map { it.id.value })
    }

    @Test
    fun `an article that was forgotten is gone from the file too`() = runTest {
        val file = folder.newFile("saved.json")
        val store = FileSavedArticles(file, UnconfinedTestDispatcher())
        store.save(article(1))
        store.save(article(2))

        store.forget(ArticleId("1"))

        assertEquals(listOf("2"), store.saved.first().map { it.id.value })
        assertEquals(listOf("2"), FileSavedArticles(file, UnconfinedTestDispatcher()).saved.first().map { it.id.value })
    }

    @Test
    fun `a store with no file yet is empty rather than broken`() = runTest {
        val store = FileSavedArticles(File(folder.root, "not-written-yet.json"), UnconfinedTestDispatcher())

        assertTrue(store.saved.first().isEmpty())
    }

    @Test
    fun `a file that got mangled reads as empty rather than taking the app down`() = runTest {
        val file = folder.newFile("saved.json")
        file.writeText("this is not json, and half of it is missing")

        val store = FileSavedArticles(file, UnconfinedTestDispatcher())

        assertTrue(store.saved.first().isEmpty())
        assertNotNull("losing a reading list silently is worse than losing it", store.lastProblem.first())
    }

    @Test
    fun `a mangled file is not left in the way of saving something new`() = runTest {
        val file = folder.newFile("saved.json")
        file.writeText("{ not json")
        val store = FileSavedArticles(file, UnconfinedTestDispatcher())

        store.save(article(1))

        assertEquals(listOf(article(1)), FileSavedArticles(file, UnconfinedTestDispatcher()).saved.first())
    }

    private fun article(id: Int) = ArticleItem(
        id = ArticleId("$id"),
        title = "Article $id",
        summary = "A summary that has to survive the round trip.",
        source = "NASA",
        url = "https://example.com/$id",
        imageUrl = if (id % 2 == 0) null else "https://example.com/$id.jpg",
        publishedAt = Instant.parse("2026-08-31T0$id:00:00Z"),
    )
}
