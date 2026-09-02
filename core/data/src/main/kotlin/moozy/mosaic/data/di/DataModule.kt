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
import java.time.ZoneId
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import moozy.mosaic.data.BuildConfig
import moozy.mosaic.data.article.SavedFirstArticleRepository
import moozy.mosaic.data.article.network.SpaceflightNewsApi
import moozy.mosaic.data.article.network.spaceflightNewsClient
import moozy.mosaic.data.movie.FileTrendingStore
import moozy.mosaic.data.movie.NoMovies
import moozy.mosaic.data.movie.TmdbApi
import moozy.mosaic.data.movie.TmdbTrending
import moozy.mosaic.data.movie.tmdbClient
import moozy.mosaic.data.saved.ImportSavedArticles
import moozy.mosaic.data.saved.RoomSavedArticles
import moozy.mosaic.data.saved.SavedArticleDao
import moozy.mosaic.data.saved.SavedArticlesDatabase
import moozy.mosaic.data.weather.FileWeatherStore
import moozy.mosaic.data.weather.OpenMeteoApi
import moozy.mosaic.data.weather.OpenMeteoWeather
import moozy.mosaic.data.weather.Place
import moozy.mosaic.data.weather.openMeteoClient
import moozy.mosaic.domain.model.Clock
import moozy.mosaic.domain.repository.ArticleRepository
import moozy.mosaic.domain.repository.MovieRepository
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
     * The source, and nothing between it and the feed.
     *
     * There is no cache here on purpose. Pages already loaded stay in memory for
     * as long as the screen's view model does, which covers rotation, the trip to
     * an article and time in the background. What a file would add is the case
     * where the process was killed -- and a list restored from disk cannot be
     * handed to Paging as a starting point, so it would be shown and then
     * replaced, which is a flicker rather than a feature.
     *
     * One article is the exception, and it is the repository's business rather
     * than a screen's: the table of kept articles is handed over here so that
     * "where does this article come from" is answered in one place. The dao does
     * not leave this module -- what the app asks for is still the interface
     * `:core:domain` declared.
     */
    @Provides
    @Singleton
    fun articleRepository(api: SpaceflightNewsApi, kept: SavedArticleDao): ArticleRepository =
        SavedFirstArticleRepository(api = api, kept = kept)

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
            api = OpenMeteoApi(
                client = openMeteoClient(OkHttp.create()),
                place = Place(name = "Taipei", latitude = 25.033, longitude = 121.5654),
            ),
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
     * The third source, when this build has a key to ask it with.
     *
     * The one place the token's absence is decided. A checkout without one is
     * ordinary rather than broken -- the assignment requires a single command to
     * build from clean, and a token cannot be committed -- so a blank field
     * builds [NoMovies] and no TMDB request is ever made. The screen has one case
     * to draw either way: nothing, the same as a weather card with no reading.
     *
     * The zone is the device's, because the day this asks about is the reader's
     * day. Nothing further down reads a clock or a calendar for itself.
     */
    @Provides
    @Singleton
    fun movieRepository(@ApplicationContext context: Context): MovieRepository {
        val token = BuildConfig.TMDB_TOKEN
        if (token.isBlank()) return NoMovies
        return TmdbTrending(
            api = TmdbApi(client = tmdbClient(OkHttp.create()), token = token),
            clock = Clock { Instant.now() },
            zone = ZoneId.systemDefault(),
            // A scope that outlives every screen, for the reason the weather's
            // does: the stream is shared by all of them and must not end when one
            // of them does. The process ending is what ends it.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            // cacheDir, beside the weather reading. A day's list is reproducible
            // by asking again, so the system may reclaim it and all that is lost
            // is one request -- which is exactly what the file was saving.
            store = FileTrendingStore(
                File(context.cacheDir, "trending-movies.json"),
                Dispatchers.IO,
            ),
        )
    }

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
     * One dao, because two callers now read the same table and a table with two
     * doors is how the two of them start disagreeing about what is in it.
     */
    @Provides
    @Singleton
    fun savedArticleDao(database: SavedArticlesDatabase): SavedArticleDao = database.saved()

    /**
     * databaseBuilder does no I/O -- SQLite opens on the first query -- so this
     * singleton stays free to build on whichever thread first asks for it. The
     * file version read itself in its constructor, which did not.
     */
    @Provides
    @Singleton
    fun savedArticles(
        rows: SavedArticleDao,
        @ApplicationContext context: Context,
    ): SavedArticles {
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

