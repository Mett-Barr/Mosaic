package moozy.mosaic.data.weather

import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import moozy.mosaic.domain.model.Clock
import moozy.mosaic.domain.model.FeedFailure
import moozy.mosaic.domain.model.WeatherResult
import moozy.mosaic.domain.repository.WeatherRepository

/** Where the weather is being asked about, and what to call it. */
internal data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

internal fun openMeteoClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = true
    install(ContentNegotiation) { json(OpenMeteoJson) }
    install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
        socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
    }
}

/**
 * The current weather for one place, asked for no more often than it changes.
 *
 * When to ask again is the source's answer, not this app's: every reading
 * carries its own stamp and the interval the source produces them on, so the
 * next moment worth a request is the source's next step. Asking sooner cannot
 * produce a value that does not exist yet.
 *
 * The reading is kept by a [WeatherCache] rather than in a field, so that the
 * system reclaiming the app does not turn a reading still worth reusing into
 * another request.
 *
 * The cache is a constructor argument rather than a decorator, which is where
 * the articles put theirs. There the cache answers a different question -- what
 * to show when a request fails -- and so it wraps whatever the source is. Here
 * there is one source and the reading is the whole of what is kept, so a layer
 * between them would only be a layer.
 */
internal class OpenMeteoWeather(
    private val client: HttpClient,
    private val place: Place,
    private val clock: Clock,
    private val cache: WeatherCache,
) : WeatherRepository {

    private var remembered: CachedWeather? = null

    override suspend fun current(): WeatherResult {
        val known = remembered ?: cache.read()?.also { remembered = it }
        // No metered window. A request that returns something new is not waste,
        // and one that returns what is already held is -- which is the class of
        // request the source's own schedule removes. Measured: a reading costs
        // 300 bytes on the wire and one article image costs 247 kilobytes.
        if (known != null && clock.now() < known.askAgainAt) {
            return WeatherResult.Loaded(known.weather)
        }
        return fetch()
    }

    @Suppress("TooGenericExceptionCaught", "RethrowCaughtException")
    private suspend fun fetch(): WeatherResult =
        try {
            val forecast: ForecastDto = client.get(FORECAST_URL) {
                parameter("latitude", place.latitude)
                parameter("longitude", place.longitude)
                parameter("current", "temperature_2m,weather_code")
                parameter("daily", "temperature_2m_max,temperature_2m_min")
                parameter("timezone", "auto")
                parameter("forecast_days", 1)
            }.body()
            val weather = forecast.toWeather(place.name)
            // Only a reading is kept. A failure that was kept would stop this
            // asking again at exactly the moment it should.
            val reading = CachedWeather(weather, weather.askAgainAfter(clock.now()))
            remembered = reading
            cache.write(reading)
            WeatherResult.Loaded(weather)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (server: ResponseException) {
            WeatherResult.Failed(FeedFailure.Server(server.response.status.value, server.message))
        } catch (unreadable: ContentConvertException) {
            WeatherResult.Failed(FeedFailure.Unreadable(unreadable.message))
        } catch (unreadable: NoTransformationFoundException) {
            WeatherResult.Failed(FeedFailure.Unreadable(unreadable.message))
        } catch (unreadable: SerializationException) {
            WeatherResult.Failed(FeedFailure.Unreadable(unreadable.message))
        } catch (network: IOException) {
            WeatherResult.Failed(network.asFailure())
        } catch (unexpected: Exception) {
            WeatherResult.Failed(FeedFailure.Unexpected(unexpected.message))
        }

    private fun IOException.asFailure(): FeedFailure = when (this) {
        is HttpRequestTimeoutException,
        is ConnectTimeoutException,
        is SocketTimeoutException,
        -> FeedFailure.Timeout(message)

        else -> FeedFailure.Offline(message)
    }

    private companion object {
        const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
    }
}

private const val REQUEST_TIMEOUT_MILLIS = 15_000L
private const val CONNECT_TIMEOUT_MILLIS = 10_000L
private const val SOCKET_TIMEOUT_MILLIS = 15_000L
