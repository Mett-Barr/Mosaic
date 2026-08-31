package moozy.mosaic.data.weather

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import moozy.mosaic.domain.model.FeedFailure
import moozy.mosaic.domain.model.WeatherResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The weather has its own cadence, so it also has its own reason to stop asking:
 * a card at the top of a feed is not worth a request every time the reader looks
 * at the screen.
 */
class OpenMeteoWeatherTest {

    private val requests = mutableListOf<HttpRequestData>()
    private var now: Instant = Instant.parse("2026-09-01T12:00:00Z")
    private var metered = false

    private fun weather(engine: MockEngine) = OpenMeteoWeather(
        client = openMeteoClient(engine),
        place = Place(name = "Taipei", latitude = 25.033, longitude = 121.5654),
        clock = { now },
        dataCost = { metered },
    )

    private fun answering(body: String) = weather(
        MockEngine { request ->
            requests += request
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    )

    private val forecast = """
        {"utc_offset_seconds": 28800, "timezone": "Asia/Taipei",
         "current": {"time": "2026-09-01T20:00", "temperature_2m": 25.6, "weather_code": 3},
         "daily": {"temperature_2m_max": [31.8], "temperature_2m_min": [25.4]}}
    """.trimIndent()

    @Test
    fun `it asks the place it was given about`() = runTest {
        answering(forecast).current()

        val url = requests.single().url
        assertEquals("api.open-meteo.com", url.host)
        assertEquals("25.033", url.parameters["latitude"])
        assertEquals("121.5654", url.parameters["longitude"])
    }

    @Test
    fun `the reading comes back named after the place, not the time zone`() = runTest {
        val result = answering(forecast).current()

        assertEquals("Taipei", (result as WeatherResult.Loaded).weather.place)
    }

    @Test
    fun `a reading taken minutes ago is not asked for again`() = runTest {
        val weather = answering(forecast)

        weather.current()
        now = now.plusSeconds(9 * 60)
        weather.current()

        assertEquals(1, requests.size)
    }

    @Test
    fun `a reading older than the window is asked for again`() = runTest {
        val weather = answering(forecast)

        weather.current()
        now = now.plusSeconds(11 * 60)
        weather.current()

        assertEquals(2, requests.size)
    }

    @Test
    fun `on mobile data the same reading lasts three times as long`() = runTest {
        val weather = answering(forecast)

        weather.current()
        metered = true
        now = now.plusSeconds(11 * 60)
        weather.current()

        assertEquals("the reader is paying for this one", 1, requests.size)
    }

    @Test
    fun `a failure is an answer, not something thrown`() = runTest {
        val result = weather(MockEngine { throw IOException("no route to host") }).current()

        assertTrue("expected a failure, got $result", result is WeatherResult.Failed)
        assertTrue((result as WeatherResult.Failed).reason is FeedFailure.Offline)
    }

    @Test
    fun `a reading that failed is not remembered as if it had worked`() = runTest {
        var attempts = 0
        val weather = weather(
            MockEngine {
                attempts++
                respondError(HttpStatusCode.InternalServerError)
            },
        )

        weather.current()
        now = now.plusSeconds(60)
        weather.current()

        assertEquals("a failure is not a reading worth keeping", 2, attempts)
    }
}
