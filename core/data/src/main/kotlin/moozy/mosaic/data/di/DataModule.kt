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
import androidx.room.Room
import java.io.File
import java.time.Instant
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import moozy.mosaic.data.article.NetworkArticleRepository
import moozy.mosaic.data.article.ArticlesWithAFallback
import moozy.mosaic.data.article.FileArticleCache
import moozy.mosaic.data.article.network.SpaceflightNewsApi
import moozy.mosaic.data.article.network.spaceflightNewsClient
import moozy.mosaic.data.saved.ImportSavedArticles
import moozy.mosaic.data.saved.RoomSavedArticles
import moozy.mosaic.data.saved.SavedArticlesDatabase
import moozy.mosaic.data.weather.FileWeatherStore
import moozy.mosaic.data.weather.OpenMeteoWeather
import moozy.mosaic.data.weather.Place
import moozy.mosaic.data.weather.openMeteoClient
import moozy.mosaic.domain.model.Clock
import moozy.mosaic.domain.repository.ArticleRepository
import moozy.mosaic.domain.repository.SavedArticles
import moozy.mosaic.domain.repository.WeatherRepository

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
    fun spaceflightNewsApi(client: HttpClient): SpaceflightNewsApi =
        SpaceflightNewsApi(client, Clock { Instant.now() })

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
    ): ArticleRepository = ArticlesWithAFallback(
        network = NetworkArticleRepository(api),
        cache = FileArticleCache(File(context.cacheDir, "articles.json"), Dispatchers.IO),
    )

    /**
     * Taipei, because the app has no location permission and asking for one to
     * put a card at the top of a feed is a poor trade. Where the reader is comes
     * with a permission dialog, a rationale, a denial path and a settings screen;
     * a fixed place needs none of that and is honest about what it shows.
     */
    @Provides
    @Singleton
    fun weatherRepository(@ApplicationContext context: Context): WeatherRepository =
        OpenMeteoWeather(
            client = openMeteoClient(OkHttp.create()),
            place = Place(name = "Taipei", latitude = 25.033, longitude = 121.5654),
            clock = Clock { Instant.now() },
            // A scope that outlives every screen, because the stream is shared
            // by all of them and must not end when one of them does. It is
            // never cancelled: the process ending is what ends it.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            // cacheDir, alongside the articles, because a reading is reproducible
            // by asking again: the system may delete it when storage runs short
            // and nothing is lost but one request. filesDir would have claimed it
            // was the reader's, which it is not.
            store = FileWeatherStore(File(context.cacheDir, "weather.json"), Dispatchers.IO),
        )

    /**
     * The reading list lives in the app's own storage: it is the reader's, it is
     * not shared, and it should go when the app does. `databases/` for the same
     * reason `filesDir` was chosen before it -- the system must not be able to
     * reclaim something the reader kept on purpose.
     *
     * No fallbackToDestructiveMigration. Version 1 has nothing to fall back
     * from, and adding it now would pre-authorise silently deleting a reader's
     * list at some future version bump.
     */
    @Provides
    @Singleton
    fun savedArticlesDatabase(@ApplicationContext context: Context): SavedArticlesDatabase =
        Room.databaseBuilder(context, SavedArticlesDatabase::class.java, "saved-articles.db")
            .build()

    /**
     * databaseBuilder does no I/O -- SQLite opens on the first query -- so this
     * singleton stays free to build on whichever thread first asks for it. The
     * file version read itself in its constructor, which did not.
     */
    @Provides
    @Singleton
    fun savedArticles(
        database: SavedArticlesDatabase,
        @ApplicationContext context: Context,
    ): SavedArticles {
        val rows = database.saved()
        val clock = Clock { Instant.now() }
        return RoomSavedArticles(
            rows = rows,
            clock = clock,
            importing = ImportSavedArticles(
                // The list the previous version wrote, read once and let go.
                file = File(context.filesDir, "saved-articles.json"),
                rows = rows,
                clock = clock,
                io = Dispatchers.IO,
            ),
        )
    }
}

