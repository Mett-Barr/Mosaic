package moozy.mosaic.data.saved

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import java.io.File
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.Clock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Saved articles are readable offline after first load" is a promise about a
 * second run of the app, not about a second call in the same run. So the test
 * that matters most here is still the one that throws the object away and opens
 * the same database again.
 *
 * SDK 34 rather than the default: Robolectric 4.16's SDK 36 sandbox wants Java
 * 21 and this build is pinned to JDK 17. Nothing in these tests depends on the
 * platform version.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomSavedArticlesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val opened = mutableListOf<SavedArticlesDatabase>()

    @After
    fun closeDatabases() = opened.forEach { it.close() }

    /**
     * Room is handed the test's own scheduler.
     *
     * Not a nicety: on its own executor the query, the invalidation and the
     * re-emission all happen on real background threads, which runTest's virtual
     * clock does not wait for -- the collector times out before the first
     * emission arrives. It has to be a StandardTestDispatcher, because Room asks
     * the dispatcher for limitedParallelism and UnconfinedTestDispatcher refuses.
     */
    private fun database(
        scheduler: TestCoroutineScheduler,
        file: File? = null,
    ): SavedArticlesDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val builder: RoomDatabase.Builder<SavedArticlesDatabase> = when (file) {
            null -> Room.inMemoryDatabaseBuilder(context, SavedArticlesDatabase::class.java)
            else -> Room.databaseBuilder(
                context,
                SavedArticlesDatabase::class.java,
                file.absolutePath,
            )
        }
        return builder
            .setQueryCoroutineContext(StandardTestDispatcher(scheduler))
            .build()
            .also { opened += it }
    }

    private fun kept(database: SavedArticlesDatabase) = RoomSavedArticles(
        rows = database.saved(),
        clock = ticking(),
        // No list from a previous version to bring over: what that does instead
        // is ImportSavedArticlesTest's subject, not this one's.
        importing = ImportSavedArticles(
            file = File(folder.root, "no-list-was-left-here.json"),
            rows = database.saved(),
            clock = ticking(),
            io = UnconfinedTestDispatcher(),
        ),
    )

    @Test
    fun `something saved elsewhere turns up in the list without it being asked`() = runTest {
        val kept = kept(database(testScheduler))

        kept.saved.test {
            assertEquals(emptyList<ArticleItem>(), awaitItem())

            kept.save(article(1))

            assertEquals(listOf(ArticleId("1")), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an article that was saved can be read back`() = runTest {
        val kept = kept(database(testScheduler))

        kept.save(article(1))

        assertEquals(listOf(article(1)), kept.saved.first())
    }

    @Test
    fun `what was saved is still there for a new reader of the same database`() = runTest {
        val file = File(folder.root, "saved-articles.db")
        val first = database(testScheduler, file)
        kept(first).save(article(1))
        first.close()

        val laterRun = kept(database(testScheduler, file))

        assertEquals(listOf(article(1)), laterRun.saved.first())
    }

    @Test
    fun `saving the same article twice keeps one copy`() = runTest {
        val kept = kept(database(testScheduler))

        kept.save(article(1))
        kept.save(article(1).copy(title = "The headline changed"))

        val saved = kept.saved.first()
        assertEquals(1, saved.size)
        assertEquals("The headline changed", saved.single().title)
    }

    @Test
    fun `saving an article again moves it back to the top`() = runTest {
        val kept = kept(database(testScheduler))
        kept.save(article(1))
        kept.save(article(2))

        kept.save(article(1))

        assertEquals(listOf("1", "2"), kept.saved.first().map { it.id.value })
    }

    /**
     * Saved out of id order on purpose. Saving 1, 2, 3 and expecting 3, 2, 1
     * asserts nothing: the ORDER BY breaks ties on id descending, so that answer
     * comes back whether or not saved_at is written or read at all.
     */
    @Test
    fun `the most recently saved article comes first`() = runTest {
        val kept = kept(database(testScheduler))

        kept.save(article(2))
        kept.save(article(1))
        kept.save(article(3))

        assertEquals(listOf("3", "1", "2"), kept.saved.first().map { it.id.value })
    }

    @Test
    fun `an article that was forgotten is gone from the database too`() = runTest {
        val file = File(folder.root, "saved-articles.db")
        val first = database(testScheduler, file)
        val kept = kept(first)
        kept.save(article(1))
        kept.save(article(2))

        kept.forget(ArticleId("1"))

        assertEquals(listOf("2"), kept.saved.first().map { it.id.value })
        first.close()
        val laterRun = kept(database(testScheduler, file))
        assertEquals(listOf("2"), laterRun.saved.first().map { it.id.value })
    }

    @Test
    fun `a reader with nothing saved yet is empty rather than broken`() = runTest {
        val kept = kept(database(testScheduler, File(folder.root, "not-written-yet.db")))

        assertTrue(kept.saved.first().isEmpty())
        assertNull(kept.lastProblem.first())
    }

    @Test
    fun `a save that cannot be written says so rather than throwing`() = runTest {
        val database = database(testScheduler)
        val kept = kept(database)
        // The table is taken out from under the write. Disk-full and real
        // corruption are the failures this stands in for and neither is
        // provokable on a JVM; what is being asserted is that a refused write
        // reaches lastProblem instead of the caller's coroutine.
        database.openHelper.writableDatabase.execSQL("DROP TABLE saved_articles")

        kept.save(article(1))

        assertNotNull(kept.lastProblem.first())
    }

    @Test
    fun `a row this app could not have written is left out, not thrown`() = runTest {
        val database = database(testScheduler)
        // Through the DAO rather than through save(), because save() takes an
        // ArticleItem and an ArticleItem cannot hold a blank title. Saying so
        // out loud is better than a test that implies a reader could cause this.
        database.saved().save(
            SavedArticleEntity(
                id = "1",
                title = " ",
                summary = "S",
                source = "NASA",
                url = "https://example.test/1",
                imageUrl = null,
                publishedAt = 0L,
                savedAt = 0L,
            ),
        )
        val kept = kept(database)

        assertEquals(emptyList<ArticleItem>(), kept.saved.first())
        assertNotNull(kept.lastProblem.first())
    }

    /**
     * Two taps cannot land in the same millisecond, but three saves in a loop
     * can. The clock steps so the expected order is the code's and not SQLite's.
     */
    private fun ticking(): Clock {
        var at = Instant.parse("2026-09-01T12:00:00Z")
        return Clock { at.also { at = at.plusMillis(1) } }
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
