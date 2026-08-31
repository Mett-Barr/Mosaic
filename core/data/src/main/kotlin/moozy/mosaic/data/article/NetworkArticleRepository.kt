package moozy.mosaic.data.article

import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.serialization.ContentConvertException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import moozy.mosaic.data.article.network.SpaceflightNewsApi
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleResult
import moozy.mosaic.domain.model.ArticlesResult
import moozy.mosaic.domain.model.FeedFailure
import moozy.mosaic.domain.model.PageCursor
import moozy.mosaic.domain.repository.ArticleRepository

/**
 * The boundary where a failure stops being thrown and becomes an answer.
 *
 * Everything below here reports trouble by throwing, which is right for a
 * transport: there is no sensible return value for "the socket closed". Everything
 * above here has to choose a screen, and choosing is easier from a sealed type
 * than from a catch block that has to know what Ktor calls things.
 *
 * The kinds are the ones a reader can tell apart rather than the ones the library
 * happens to have: nothing left the phone, nothing came back in time, something
 * came back and it was an error, something came back and it made no sense.
 */
internal class NetworkArticleRepository(
    private val api: SpaceflightNewsApi,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) : ArticleRepository {

    // Nothing is cached here, so there is nothing for `force` to step past.
    override suspend fun articles(after: PageCursor?, force: Boolean): ArticlesResult =
        when (val answer = asked { api.articles(limit = pageSize, after = after?.value) }) {
            is Answer.Yes -> ArticlesResult.Loaded(
                articles = answer.value.articles,
                next = answer.value.next?.let(::PageCursor),
                dropped = answer.value.droppedReasons.size,
            )

            is Answer.No -> ArticlesResult.Failed(answer.reason)
        }

    override suspend fun article(id: ArticleId): ArticleResult =
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
            Answer.No(FeedFailure.Server(server.response.status.value, server.message))
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
