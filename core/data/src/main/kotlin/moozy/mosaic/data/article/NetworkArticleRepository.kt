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
import moozy.mosaic.domain.model.ArticlesResult
import moozy.mosaic.domain.model.FeedFailure
import moozy.mosaic.domain.model.PageCursor
import moozy.mosaic.domain.repository.ArticleRepository

/**
 * The boundary where a failure stops being thrown and becomes an answer.
 *
 * Everything below here reports trouble by throwing, which is the right shape for
 * a transport: there is no sensible value for "the socket closed". Everything
 * above here has to choose a screen, and choosing is easier from a sealed type
 * than from a catch block that has to know what Ktor calls things.
 *
 * The exceptions are caught by kind rather than by name, and the kinds are the
 * ones the reader can tell apart: nothing left the phone, nothing came back in
 * time, something came back and it was an error, something came back and it made
 * no sense.
 */
internal class NetworkArticleRepository(
    private val api: SpaceflightNewsApi,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) : ArticleRepository {

    // Catching Exception is the point of this class rather than an oversight: the
    // promise is that a caller is told what happened instead of being interrupted,
    // and a promise with "unless it is something I did not think of" in it is not
    // one. Cancellation is the exception to that -- it is not a failure, it is the
    // caller changing its mind, and turning it into a value would leave coroutines
    // running that were asked to stop.
    @Suppress("TooGenericExceptionCaught", "RethrowCaughtException")
    override suspend fun articles(after: PageCursor?): ArticlesResult =
        try {
            val page = api.articles(limit = pageSize, after = after?.value)
            ArticlesResult.Loaded(
                articles = page.articles,
                next = page.next?.let(::PageCursor),
                dropped = page.droppedReasons.size,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (server: ResponseException) {
            failed(FeedFailure.Server(server.response.status.value, server.message))
        } catch (unreadable: ContentConvertException) {
            failed(FeedFailure.Unreadable(unreadable.message))
        } catch (unreadable: NoTransformationFoundException) {
            failed(FeedFailure.Unreadable(unreadable.message))
        } catch (unreadable: SerializationException) {
            failed(FeedFailure.Unreadable(unreadable.message))
        } catch (network: IOException) {
            failed(network.asFailure())
        } catch (unexpected: Exception) {
            failed(FeedFailure.Unexpected(unexpected.message))
        }

    private fun failed(reason: FeedFailure) = ArticlesResult.Failed(reason)

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
