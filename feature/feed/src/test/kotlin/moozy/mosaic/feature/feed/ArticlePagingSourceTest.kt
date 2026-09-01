package moozy.mosaic.feature.feed

import androidx.paging.PagingSource
import androidx.paging.PagingState
import java.time.Instant
import kotlinx.coroutines.test.runTest
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.ArticleResult
import moozy.mosaic.domain.model.ArticlesResult
import moozy.mosaic.domain.model.FeedFailure
import moozy.mosaic.domain.model.PageCursor
import moozy.mosaic.domain.repository.ArticleRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One generation of the feed.
 *
 * The key is the cursor the source hands back, not a number this app works out:
 * an offset computed here would be a guess about a list that moves.
 *
 * De-duplication lives here rather than in the view model because a generation
 * is exactly one instance of this class. The set of what has already been given
 * out is born and dies with the generation it describes, so there is nothing to
 * clear and no flag saying whether it is still valid.
 */
class ArticlePagingSourceTest {

    @Test
    fun `the first page is asked for without a cursor`() = runTest {
        val repository = FakeArticles(firstPage = page(1, 2, next = NEXT))

        val result = ArticlePagingSource(repository).load(refresh())

        val page = result as PagingSource.LoadResult.Page
        assertEquals(listOf("1", "2"), page.data.map { it.id.value })
        assertEquals(PageCursor(NEXT), page.nextKey)
        assertNull("nothing comes before the top of the list", page.prevKey)
    }

    @Test
    fun `a later page is asked for with the cursor the last one gave`() = runTest {
        val repository = FakeArticles(firstPage = page(1), nextPages = mapOf(NEXT to page(3, 4)))
        val source = ArticlePagingSource(repository)
        source.load(refresh())

        val result = source.load(append(PageCursor(NEXT)))

        assertEquals(listOf("3", "4"), (result as PagingSource.LoadResult.Page).data.map { it.id.value })
    }

    @Test
    fun `an article this generation has already given is not given again`() = runTest {
        // Measured: the same cutoff's count grew by one within a day, so an
        // article can be backfilled into the middle of the list between two
        // requests. Offsets shift, the pages overlap, and a LazyColumn keyed by
        // id throws on the repeat rather than drawing one of them.
        val repository = FakeArticles(firstPage = page(1, 2, next = NEXT), nextPages = mapOf(NEXT to page(2, 3)))
        val source = ArticlePagingSource(repository)
        source.load(refresh())

        val result = source.load(append(PageCursor(NEXT)))

        assertEquals(listOf("3"), (result as PagingSource.LoadResult.Page).data.map { it.id.value })
    }

    @Test
    fun `each generation forgets what the last one gave`() = runTest {
        val repository = FakeArticles(firstPage = page(1, 2))

        val first = ArticlePagingSource(repository).load(refresh())
        val second = ArticlePagingSource(repository).load(refresh())

        // A new generation is a new list, not a continuation of the old one.
        assertEquals(
            (first as PagingSource.LoadResult.Page).data.map { it.id.value },
            (second as PagingSource.LoadResult.Page).data.map { it.id.value },
        )
    }

    @Test
    fun `a failure is an error result, not something thrown`() = runTest {
        val repository = FakeArticles(firstPage = ArticlesResult.Failed(FeedFailure.Offline()))

        val result = ArticlePagingSource(repository).load(refresh())

        assertTrue("expected an error, got $result", result is PagingSource.LoadResult.Error)
    }

    @Test
    fun `refreshing starts at the top rather than where the reader was`() {
        val source = ArticlePagingSource(FakeArticles(firstPage = page(1)))

        val key = source.getRefreshKey(PagingState(emptyList(), null, config, 0))

        // There is no resuming from the middle: a new generation is a new list,
        // and its first page is the top of it.
        assertNull(key)
    }

    private fun refresh() = PagingSource.LoadParams.Refresh<PageCursor>(null, PAGE_SIZE, false)

    private fun append(key: PageCursor) = PagingSource.LoadParams.Append(key, PAGE_SIZE, false)

    private val config = androidx.paging.PagingConfig(PAGE_SIZE)

    private fun page(vararg ids: Int, next: String? = null) = ArticlesResult.Loaded(
        articles = ids.map { article(it) },
        next = next?.let(::PageCursor),
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

    private class FakeArticles(
        private val firstPage: ArticlesResult,
        private val nextPages: Map<String, ArticlesResult> = emptyMap(),
    ) : ArticleRepository {
        override suspend fun articles(after: PageCursor?): ArticlesResult =
            if (after == null) firstPage
            else nextPages[after.value] ?: error("nobody prepared a page after ${after.value}")

        override suspend fun article(id: ArticleId): ArticleResult =
            error("the list does not ask about one article")
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val NEXT = "https://api.spaceflightnewsapi.net/v4/articles/?limit=20&offset=20"
    }
}
