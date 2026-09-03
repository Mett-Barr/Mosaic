package moozy.mosaic.data.movie

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import moozy.mosaic.domain.model.Movie

/**
 * The engine is a parameter rather than something this module picks, so a test can
 * hand over a MockEngine and the app can hand over one that knows about the
 * platform's connection pool, without either having to know the other exists.
 *
 * `expectSuccess` is on for the reason the other two clients have it on: a 401
 * from a token that has been revoked is better raised as the error it is than as
 * a complaint about response types.
 */
internal fun tmdbClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = true
    install(ContentNegotiation) { json(TmdbJson) }
    install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
        socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
    }
}

/**
 * Asks TMDB what is trending today.
 *
 * The address is the whole of the freshness policy's evidence: `day` rather than
 * `week` is a path segment, so the unit the source recomputes on is stated by the
 * request even though nothing in the response repeats it. The repository above is
 * left with the only question that is its own -- whether today's answer is
 * already held.
 *
 * [token] is TMDB's v4 read access token and travels in the `Authorization`
 * header rather than as a query parameter, which is both what TMDB documents and
 * the reason it never reaches a URL that something along the way might write down.
 * It arrives here from a build config field, so a checkout without one is a
 * blank string rather than a build failure -- and blank never reaches this class,
 * because the module that assembles the data layer builds a repository that asks
 * for nothing instead.
 *
 * This throws when a request does not produce a list, in the same way
 * [moozy.mosaic.data.weather.OpenMeteoApi] does. What a failure means to the app
 * is not decidable from here.
 */
internal class TmdbApi(
    private val client: HttpClient,
    private val token: String,
) {

    suspend fun trending(): List<Movie> {
        val page: TrendingPageDto = client.get(TRENDING_URL) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body()
        return page.toMovies()
    }

    private companion object {
        /** Today's list. `week` is the other one the endpoint takes, and it is not this app's unit. */
        const val TRENDING_URL = "https://api.themoviedb.org/3/trending/movie/day"
    }
}

private const val REQUEST_TIMEOUT_MILLIS = 15_000L
private const val CONNECT_TIMEOUT_MILLIS = 10_000L
private const val SOCKET_TIMEOUT_MILLIS = 15_000L
