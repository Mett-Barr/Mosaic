package moozy.mosaic.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import java.io.File
import java.time.Instant
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import moozy.mosaic.data.article.NetworkArticleRepository
import moozy.mosaic.data.article.CachingArticles
import moozy.mosaic.data.article.FileArticleCache
import moozy.mosaic.data.article.network.SpaceflightNewsApi
import moozy.mosaic.data.article.network.spaceflightNewsClient
import moozy.mosaic.data.saved.FileSavedArticles
import moozy.mosaic.domain.model.Clock
import moozy.mosaic.domain.model.DataCost
import moozy.mosaic.domain.repository.ArticleRepository
import moozy.mosaic.domain.repository.SavedArticles

/**
 * Where the data layer is assembled.
 *
 * The module is `internal` and so is everything it builds: the rest of the app can
 * ask for an [ArticleRepository] and gets one, and cannot reach for the api, the
 * client or the mapper behind it. The one type that crosses the module boundary is
 * the interface `:core:domain` declared.
 *
 * The client is a singleton because it owns a connection pool and a thread pool.
 * Building one per call would give every request a cold connection, which on a
 * phone is the difference between a feed that appears and a feed that arrives.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DataModule {

    @Provides
    @Singleton
    fun httpClient(): HttpClient = spaceflightNewsClient(OkHttp.create())

    @Provides
    @Singleton
    fun spaceflightNewsApi(client: HttpClient): SpaceflightNewsApi = SpaceflightNewsApi(client)

    /**
     * The repository the app sees is the network one wrapped in the freshness
     * policy. Nothing above here knows a cache exists; it asks for articles and
     * sometimes the answer costs nothing.
     */
    @Provides
    @Singleton
    fun articleRepository(
        api: SpaceflightNewsApi,
        @ApplicationContext context: Context,
    ): ArticleRepository = CachingArticles(
        network = NetworkArticleRepository(api),
        cache = FileArticleCache(File(context.cacheDir, "articles.json"), Dispatchers.IO),
        clock = Clock { Instant.now() },
        dataCost = DataCost { context.isOnMeteredConnection() },
    )

    /**
     * The reading list lives in the app's own storage: it is the reader's, it is
     * not shared, and it should go when the app does.
     */
    @Provides
    @Singleton
    fun savedArticles(@ApplicationContext context: Context): SavedArticles =
        FileSavedArticles(File(context.filesDir, "saved-articles.json"), Dispatchers.IO)
}

/**
 * Whether this connection is one the reader is paying for.
 *
 * Unknown counts as metered. Guessing wrong in that direction costs a slightly
 * staler feed; guessing wrong the other way spends somebody's data.
 */
private fun Context.isOnMeteredConnection(): Boolean =
    getSystemService<ConnectivityManager>()?.isActiveNetworkMetered ?: true
