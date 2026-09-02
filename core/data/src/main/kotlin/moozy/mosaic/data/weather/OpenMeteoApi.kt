package moozy.mosaic.data.weather

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import moozy.mosaic.domain.model.Weather

/** Where the weather is being asked about, and what to call it. */
internal data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * The engine is a parameter rather than something this module picks, so a test can
 * hand over a MockEngine and the app can hand over one that knows about the
 * platform's connection pool, without either having to know the other exists.
 *
 * `expectSuccess` is on for the same reason the article client has it on: a 500
 * whose body does not parse is better raised as the error it is than as a
 * complaint about response types.
 */
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
 * Asks Open-Meteo what the weather is.
 *
 * Everything about the request lives here -- the address, the fields worth
 * asking for, and the shape they come back in -- so that the repository above is
 * left with the only question that is actually its own: when to ask again.
 *
 * This throws when a request does not produce a reading, in the same way
 * [moozy.mosaic.data.article.network.SpaceflightNewsApi] does. Deciding what a
 * failure means to the app is not a decision that can be made from here: the
 * feed turns one into a screen, and the weather turns one into no card at all.
 *
 * `forecast_days` is 1 because the card shows today. The daily block is asked
 * for anyway: the high and low are today's, and the current temperature on its
 * own does not say whether 29 degrees is the top of the day or the bottom.
 */
internal class OpenMeteoApi(
    private val client: HttpClient,
    private val place: Place,
) {

    suspend fun forecast(): Weather {
        val forecast: ForecastDto = client.get(FORECAST_URL) {
            parameter("latitude", place.latitude)
            parameter("longitude", place.longitude)
            parameter("current", "temperature_2m,weather_code")
            parameter("daily", "temperature_2m_max,temperature_2m_min")
            parameter("timezone", "auto")
            parameter("forecast_days", 1)
        }.body()
        return forecast.toWeather(place.name)
    }

    private companion object {
        const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
    }
}

private const val REQUEST_TIMEOUT_MILLIS = 15_000L
private const val CONNECT_TIMEOUT_MILLIS = 10_000L
private const val SOCKET_TIMEOUT_MILLIS = 15_000L
