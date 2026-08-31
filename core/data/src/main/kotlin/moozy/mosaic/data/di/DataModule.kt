package moozy.mosaic.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import moozy.mosaic.data.article.NetworkArticleRepository
import moozy.mosaic.data.article.network.SpaceflightNewsApi
import moozy.mosaic.data.article.network.spaceflightNewsClient
import moozy.mosaic.data.saved.FileSavedArticles
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

    @Provides
    @Singleton
    fun articleRepository(api: SpaceflightNewsApi): ArticleRepository =
        NetworkArticleRepository(api)

    /**
     * The reading list lives in the app's own storage: it is the reader's, it is
     * not shared, and it should go when the app does.
     */
    @Provides
    @Singleton
    fun savedArticles(@ApplicationContext context: Context): SavedArticles =
        FileSavedArticles(File(context.filesDir, "saved-articles.json"), Dispatchers.IO)
}
