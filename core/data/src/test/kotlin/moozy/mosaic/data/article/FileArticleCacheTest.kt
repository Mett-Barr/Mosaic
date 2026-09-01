package moozy.mosaic.data.article

import java.io.File
import java.time.Instant
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.PageCursor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The cache only saves data across launches if it survives one, so every case
 * here reads with an object that did not do the writing.
 */
@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FileArticleCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun cache(file: File) = FileArticleCache(file, UnconfinedTestDispatcher())

    @Test
    fun `a time that is not a time reads as nothing rather than as a throw`() = runTest {
        val file = folder.newFile("feed.json")
        file.writeText(
            """{"articles":[{"id":"1","title":"T","summary":"S","source":"NASA",
               "url":"https://example.test/1","published_at":"once upon a time"}],
               "fetched_at":"2026-09-01T12:00:00Z"}"""
                .trimIndent(),
        )

        assertNull(cache(file).read())
    }

    @Test
    fun `an unreadable moment of fetching reads as nothing too`() = runTest {
        val file = folder.newFile("feed.json")
        file.writeText(
            """{"articles":[],"fetched_at":"recently"}"""
        )

        assertNull(cache(file).read())
    }

    @Test
    fun `a page written down is there for the next run`() = runTest {
        val file = folder.newFile("feed.json")
        val fetchedAt = Instant.parse("2026-09-01T12:00:00Z")

        cache(file).write(CachedArticles(listOf(article(1)), PageCursor(NEXT), fetchedAt))

        val read = cache(file).read()
        assertEquals(listOf("1"), read?.articles?.map { it.id.value })
        assertEquals(PageCursor(NEXT), read?.next)
        assertEquals(fetchedAt, read?.fetchedAt)
    }

    @Test
    fun `a cache nobody has written to yet has nothing in it`() = runTest {
        assertNull(cache(File(folder.root, "never-written.json")).read())
    }

    @Test
    fun `a mangled cache reads as nothing rather than as a crash`() = runTest {
        val file = folder.newFile("feed.json")
        file.writeText("half a file")

        assertNull(cache(file).read())
    }

    @Test
    fun `a cache that is json but nonsense reads as nothing rather than as a crash`() = runTest {
        val file = folder.newFile("feed.json")
        // Valid JSON, valid shape, values the domain refuses: a blank id and a
        // timestamp that is not one. Parsing succeeded, so the serialization
        // catch never sees it.
        file.writeText(
            """
            {"articles": [{"id": "", "title": "t", "summary": "", "source": "s",
              "url": "u", "published_at": "not a time"}],
             "next": null, "fetched_at": "2026-09-01T12:00:00Z"}
            """.trimIndent(),
        )

        assertNull(cache(file).read())
    }

    @Test
    fun `how many rows the page lost is remembered too`() = runTest {
        val file = folder.newFile("feed.json")
        val fetchedAt = Instant.parse("2026-09-01T12:00:00Z")

        cache(file).write(CachedArticles(listOf(article(1)), null, fetchedAt, dropped = 2))

        assertEquals(2, cache(file).read()?.dropped)
    }

    @Test
    fun `the last page written is the one that is read`() = runTest {
        val file = folder.newFile("feed.json")
        val cache = cache(file)
        cache.write(CachedArticles(listOf(article(1)), null, Instant.parse("2026-09-01T12:00:00Z")))

        cache.write(CachedArticles(listOf(article(2)), null, Instant.parse("2026-09-01T13:00:00Z")))

        assertEquals(listOf("2"), cache(file).read()?.articles?.map { it.id.value })
    }

    private fun article(id: Int) = ArticleItem(
        id = ArticleId("$id"),
        title = "Article $id",
        summary = "",
        source = "NASA",
        url = "https://example.com/$id",
        imageUrl = null,
        publishedAt = Instant.parse("2026-09-01T10:00:00Z"),
    )

    private companion object {
        const val NEXT = "https://api.spaceflightnewsapi.net/v4/articles/?limit=20&offset=20"
    }
}
