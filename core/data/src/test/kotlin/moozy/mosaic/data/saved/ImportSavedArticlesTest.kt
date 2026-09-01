package moozy.mosaic.data.saved

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The reading list the file version wrote is the only part of this change that
 * can lose anything: everything else is a swap behind an interface that did not
 * move. So it is read once, on the way in, and these are the tests that say what
 * "once" and "read" mean.
 *
 * The forgiving read that DECISIONS 18 was written about did not disappear when
 * the list became a table. It moved here, to the one boundary that still has
 * bytes on the other side that this app did not write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportSavedArticlesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val opened = mutableListOf<SavedArticlesDatabase>()

    @After
    fun closeDatabases() = opened.forEach { it.close() }

    private fun database(scheduler: TestCoroutineScheduler): SavedArticlesDatabase =
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            SavedArticlesDatabase::class.java,
        )
            .setQueryCoroutineContext(StandardTestDispatcher(scheduler))
            .build()
            .also { opened += it }

    private fun kept(database: SavedArticlesDatabase, file: File): RoomSavedArticles {
        val clock = Clock { NOW }
        return RoomSavedArticles(
            rows = database.saved(),
            clock = clock,
            importing = ImportSavedArticles(
                file = file,
                rows = database.saved(),
                clock = clock,
                io = UnconfinedTestDispatcher(),
            ),
        )
    }

    private fun file(contents: String): File =
        File(folder.root, "saved-articles.json").apply { writeText(contents) }

    @Test
    fun `a reading list left by the previous version turns up in the database`() = runTest {
        val kept = kept(database(testScheduler), file(listOf(row("1")).json()))

        assertEquals(listOf("1"), kept.saved.first().map { it.id.value })
    }

    @Test
    fun `the order the previous version kept is the order that survives`() = runTest {
        // The file is newest-first and has no timestamps of its own, so the
        // position in the array is the only record of the order there is.
        val file = file(listOf(row("3"), row("2"), row("1")).json())

        val kept = kept(database(testScheduler), file)

        assertEquals(listOf("3", "2", "1"), kept.saved.first().map { it.id.value })
    }

    @Test
    fun `a row with a time that is not a time is left out of the import`() = runTest {
        val file = file(
            listOf(row("1", publishedAt = "last Tuesday"), row("2")).json(),
        )

        val kept = kept(database(testScheduler), file)

        assertEquals(listOf("2"), kept.saved.first().map { it.id.value })
        assertNotNull(kept.lastProblem.first())
    }

    @Test
    fun `a row the domain refuses is left out of the import`() = runTest {
        val file = file(listOf(row(""), row("2")).json())

        val kept = kept(database(testScheduler), file)

        assertEquals(listOf("2"), kept.saved.first().map { it.id.value })
        assertNotNull(kept.lastProblem.first())
    }

    @Test
    fun `the old file is gone once it has been read`() = runTest {
        val file = file(listOf(row("1")).json())

        kept(database(testScheduler), file).saved.first()

        assertFalse(file.exists())
    }

    @Test
    fun `a list that has already been imported is not imported twice`() = runTest {
        val file = file(listOf(row("1")).json())
        val kept = kept(database(testScheduler), file)
        kept.saved.first()

        // Whatever is at that path afterwards is not the previous version's
        // list; it was already read, and reading it again would put back rows
        // the reader has since forgotten.
        file.writeText(listOf(row("2")).json())

        assertEquals(listOf("1"), kept.saved.first().map { it.id.value })
        assertTrue(file.exists())
    }

    @Test
    fun `an import that could not finish leaves the file for the next launch`() = runTest {
        val database = database(testScheduler)
        val file = file(listOf(row("1")).json())
        val kept = kept(database, file)
        database.openHelper.writableDatabase.execSQL("DROP TABLE saved_articles")

        kept.save(article("2"))

        assertTrue("the bytes are the only copy until they are in the table", file.exists())
        assertNotNull(kept.lastProblem.first())
    }

    @Test
    fun `a file that got mangled is set aside, not deleted`() = runTest {
        val file = file("this is not json, and half of it is missing")

        val kept = kept(database(testScheduler), file)

        assertEquals(emptyList<ArticleItem>(), kept.saved.first())
        assertNotNull(kept.lastProblem.first())
        // Not deleted: it is the reader's, and unreadable is not the same as
        // worthless. Renamed, so it is not re-parsed on every launch for the
        // rest of this install's life.
        assertFalse(file.exists())
        assertTrue(File(folder.root, "saved-articles.json.unreadable").exists())
    }

    private fun row(id: String, publishedAt: String = "2026-08-31T10:00:00Z") =
        """{"id":"$id","title":"Article $id","summary":"S","source":"NASA",
           "url":"https://example.test/$id","published_at":"$publishedAt"}"""

    private fun List<String>.json() = joinToString(prefix = "[", postfix = "]")

    private fun article(id: String) = ArticleItem(
        id = ArticleId(id),
        title = "Article $id",
        summary = "S",
        source = "NASA",
        url = "https://example.test/$id",
        imageUrl = null,
        publishedAt = Instant.parse("2026-08-31T10:00:00Z"),
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-01T12:00:00Z")
    }
}
