package moozy.mosaic.data.saved

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Asking the table about one article.
 *
 * The list query already has a test of its own; this one exists because
 * "the row with this id" is a different question, and answering it by reading
 * every row and looking through them in Kotlin is the thing being replaced.
 *
 * Room verifies the SQL at compile time, so what is left to check here is what
 * it cannot: that the row which comes back is the one that was asked for, and
 * that an id nobody saved is an absence rather than somebody else's article.
 *
 * SDK 34 rather than the default, for the reason [RoomSavedArticlesTest] gives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedArticleDaoTest {

    private val opened = mutableListOf<SavedArticlesDatabase>()

    @After
    fun closeDatabases() = opened.forEach { it.close() }

    /** Room is handed the test's own scheduler, for the reason [RoomSavedArticlesTest] gives. */
    private fun dao(scheduler: TestCoroutineScheduler): SavedArticleDao =
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            SavedArticlesDatabase::class.java,
        )
            .setQueryCoroutineContext(StandardTestDispatcher(scheduler))
            .build()
            .also { opened += it }
            .saved()

    @Test
    fun `an article that was saved can be found by its id alone`() = runTest {
        val rows = dao(testScheduler)
        rows.saveAll(listOf(row("1"), row("2"), row("3")))

        val found = rows.find("2")

        assertEquals(row("2"), found)
    }

    @Test
    fun `an id nobody saved is nothing, not somebody else's article`() = runTest {
        val rows = dao(testScheduler)
        rows.saveAll(listOf(row("1"), row("3")))

        assertNull(rows.find("2"))
    }

    @Test
    fun `an article that was forgotten can no longer be found`() = runTest {
        val rows = dao(testScheduler)
        rows.save(row("1"))

        rows.forget("1")

        assertNull(rows.find("1"))
    }

    private fun row(id: String) = SavedArticleEntity(
        id = id,
        title = "Article $id",
        summary = "A summary that has to survive the round trip.",
        source = "NASA",
        url = "https://example.com/$id",
        imageUrl = "https://example.com/$id.jpg",
        publishedAt = 0L,
        savedAt = 0L,
    )
}
