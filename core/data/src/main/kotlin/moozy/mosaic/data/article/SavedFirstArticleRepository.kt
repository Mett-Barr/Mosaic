package moozy.mosaic.data.article

import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.ContentConvertException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import moozy.mosaic.data.article.network.SpaceflightNewsApi
import moozy.mosaic.data.saved.SavedArticleDao
import moozy.mosaic.data.saved.toArticle
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.ArticleResult
import moozy.mosaic.domain.model.ArticlesResult
import moozy.mosaic.domain.model.FeedFailure
import moozy.mosaic.domain.model.PageCursor
import moozy.mosaic.domain.repository.ArticleRepository

/**
 * Where an article comes from, decided in one place.
 *
 * The two questions have different numbers of sources, and the name says which
 * way the split falls. [article] has three: the copy the reader kept answers
 * first, then the copy the feed is already showing, and the network is asked only
 * when there is neither. [articles] has one and is not getting a second -- the
 * feed is deliberately not offline-first (DECISIONS 25, 28), so a page is always
 * the network's answer.
 *
 * The third source is filled by the second question rather than by a caller: a
 * page handed to the feed is a screenful of articles this app now has, and the
 * screen that opens one of them should not have to ask for it back
 * (DECISIONS 41). Nothing outside this module knows that happens, which is the
 * point -- `ArticlePagingSource` asks for a page and gets one, exactly as before.
 *
 * Not `OfflineFirstArticleRepository`, which is Now in Android's name for this
 * role: that name would claim something only half of this class does. Saved-first
 * is the honest half, and it degenerates to network-first for the feed, where
 * nothing is ever kept.
 *
 * It is also the boundary where a failure stops being thrown and becomes an
 * answer. Everything below here reports trouble by throwing, which is right for
 * a transport: there is no sensible return value for "the socket closed".
 * Everything above here has to choose a screen, and choosing is easier from a
 * sealed type than from a catch block that has to know what Ktor calls things.
 *
 * The kinds are the ones a reader can tell apart rather than the ones the library
 * happens to have: nothing left the phone, nothing came back in time, something
 * came back and it was an error, something came back and it made no sense.
 */
internal class SavedFirstArticleRepository(
    private val api: SpaceflightNewsApi,
    private val kept: SavedArticleDao,
    private val showed: ArticlesTheFeedShowed = ArticlesTheFeedShowed(),
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) : ArticleRepository {

    override suspend fun articles(after: PageCursor?): ArticlesResult =
        when (val answer = asked { api.articles(limit = pageSize, after = after?.value) }) {
            is Answer.Yes -> {
                // A null cursor is the top of the list, which is the reader or the
                // app asking for the feed again. That is the one event this app
                // treats as "the list starts over" (DECISIONS 25), so it is the
                // one event that empties what the last list left behind.
                showed.handedOut(answer.value.articles, fromTheTop = after == null)
                ArticlesResult.Loaded(
                    articles = answer.value.articles,
                    next = answer.value.next?.let(::PageCursor),
                    dropped = answer.value.droppedReasons.size,
                )
            }

            is Answer.No -> ArticlesResult.Failed(answer.reason)
        }

    /**
     * An article the reader kept is theirs already: it is served from the phone
     * and no request is made at all. The API only ever returns the summary this
     * app already wrote down -- there is no full body to come back for -- so
     * asking again would spend a request and a spinner on the one article they
     * said they wanted available without either. What it costs is that a kept
     * article stops following edits made at the source; DECISIONS 30 is the
     * record of that trade.
     *
     * The copy the feed is showing comes after it and not before. What a reader
     * kept is the article (DECISIONS 30) and a page that happens to be carrying
     * the same id does not get to overrule that -- otherwise which copy they saw
     * would depend on whether the feed had scrolled past it, which is not a rule
     * anybody could hold in their head. What it does replace is the request:
     * an article the reader is already looking at a card of is in hand, and the
     * only thing asking again buys is a spinner in front of it (DECISIONS 41).
     */
    override suspend fun article(id: ArticleId): ArticleResult =
        keptCopyOf(id)?.let(ArticleResult::Loaded)
            ?: showed.find(id)?.let(ArticleResult::Loaded)
            ?: fromTheNetwork(id)

    /**
     * The copy the reader kept, if there is one this app can still read.
     *
     * Trouble here is "there is nothing kept" rather than a failure of its own,
     * because there is still a source left to ask. A row that will not become an
     * article cannot be produced by anything that writes this table, but a row
     * that arrived some other way should cost the reader a local answer, not the
     * article. It goes through the same [asked] chain as a request so that the
     * promise this class makes -- failures become answers -- covers the source
     * that was added after the promise was written.
     */
    private suspend fun keptCopyOf(id: ArticleId): ArticleItem? =
        when (val answer = asked { kept.find(id.value)?.toArticle() }) {
            is Answer.Yes -> answer.value
            is Answer.No -> null
        }

    private suspend fun fromTheNetwork(id: ArticleId): ArticleResult =
        when (val answer = asked { api.article(id.value) }) {
            // The request worked and what came back is not an article this app can
            // show. That is not the same as the article being missing, which
            // arrives as a 404 and becomes a Server failure below.
            is Answer.Yes -> answer.value.articles.firstOrNull()
                ?.let(ArticleResult::Loaded)
                ?: ArticleResult.Failed(
                    FeedFailure.Unreadable(answer.value.droppedReasons.firstOrNull()),
                )

            is Answer.No -> ArticleResult.Failed(answer.reason)
        }

    /**
     * Runs a request and says how it went.
     *
     * Both methods fail in exactly the same ways, so they ask the same question
     * here rather than each keeping a copy of the catch chain. Two copies drift,
     * and the one that drifts is the one nobody was looking at.
     *
     * Catching Exception is the point of this class rather than an oversight: the
     * promise is that a caller is told what happened instead of being interrupted,
     * and a promise with "unless it is something I did not think of" in it is not
     * one. Cancellation is the exception -- it is not a failure, it is the caller
     * changing its mind, and turning it into a value would leave coroutines
     * running that were asked to stop.
     */
    @Suppress("TooGenericExceptionCaught", "RethrowCaughtException")
    private suspend fun <T> asked(request: suspend () -> T): Answer<T> =
        try {
            Answer.Yes(request())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (server: ResponseException) {
            Answer.No(server.asFailure())
        } catch (unreadable: ContentConvertException) {
            Answer.No(FeedFailure.Unreadable(unreadable.message))
        } catch (unreadable: NoTransformationFoundException) {
            Answer.No(FeedFailure.Unreadable(unreadable.message))
        } catch (unreadable: SerializationException) {
            Answer.No(FeedFailure.Unreadable(unreadable.message))
        } catch (network: IOException) {
            Answer.No(network.asFailure())
        } catch (unexpected: Exception) {
            Answer.No(FeedFailure.Unexpected(unexpected.message))
        }

    private sealed interface Answer<out T> {
        data class Yes<T>(val value: T) : Answer<T>
        data class No(val reason: FeedFailure) : Answer<Nothing>
    }

    /**
     * A 404 is not an error the reader can wait out; it is the answer. Everything
     * else keeps its status, because there is nothing more specific to say.
     */
    private fun ResponseException.asFailure(): FeedFailure =
        if (response.status == HttpStatusCode.NotFound) {
            FeedFailure.Missing(message)
        } else {
            FeedFailure.Server(response.status.value, message)
        }

    /**
     * Everything that goes wrong on the wire arrives as an [IOException]; the
     * useful question is whether anything got out and whether anything came back.
     */
    private fun IOException.asFailure(): FeedFailure = when (this) {
        is HttpRequestTimeoutException,
        is ConnectTimeoutException,
        is SocketTimeoutException,
        -> FeedFailure.Timeout(message)

        else -> FeedFailure.Offline(message)
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
