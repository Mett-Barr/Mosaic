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
import moozy.mosaic.domain.model.Sky
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
    private val cache = RememberedInMemory()

    private fun weather(engine: MockEngine) = OpenMeteoWeather(
        client = openMeteoClient(engine),
        place = Place(name = "Taipei", latitude = 25.033, longitude = 121.5654),
        clock = { now },
        cache = cache,
    )

    private fun answering(body: String) = weather(
        MockEngine { request ->
            requests += request
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    )

    private val forecast = """
        {"utc_offset_seconds": 28800, "timezone": "Asia/Taipei",
         "current": {"interval": 900, "time": "2026-09-01T20:00", "temperature_2m": 25.6, "weather_code": 3},
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
    fun `the reading comes back whole, named after the place rather than the time zone`() = runTest {
        val result = answering(forecast).current()

        val reading = (result as WeatherResult.Loaded).weather
        assertEquals("Taipei", reading.place)
        assertEquals(26, reading.temperature)
        assertEquals(32, reading.high)
        assertEquals(25, reading.low)
        assertEquals(Sky.CLOUDY, reading.sky)
        assertEquals(Instant.parse("2026-09-01T12:00:00Z"), reading.measuredAt)
    }

    @Test
    fun `it asks for exactly the fields the card needs and no more`() = runTest {
        answering(forecast).current()

        val url = requests.single().url
        assertEquals("temperature_2m,weather_code", url.parameters["current"])
        assertEquals("temperature_2m_max,temperature_2m_min", url.parameters["daily"])
        assertEquals("1", url.parameters["forecast_days"])
    }

    @Test
    fun `before the source has produced a new reading, nothing is asked`() = runTest {
        val weather = answering(forecast)

        weather.current()
        // The reading is stamped 12:00 and the source steps every 15 minutes,
        // so nothing new exists until 12:15 whatever the clock here says.
        now = now.plusSeconds(14 * 60)
        weather.current()

        assertEquals("asking sooner cannot produce a value that does not exist", 1, requests.size)
    }

    @Test
    fun `once the source has stepped, it is asked again`() = runTest {
        val weather = answering(forecast)

        weather.current()
        now = now.plusSeconds(16 * 60)
        weather.current()

        assertEquals(2, requests.size)
    }

    @Test
    fun `mobile data does not buy the reader a staler reading`() = runTest {
        val weather = answering(forecast)

        weather.current()
        now = now.plusSeconds(16 * 60)
        weather.current()

        // There is no metered window any more. Every request this makes now
        // returns something the reader does not already have, and one costs
        // 300 bytes against the 247 kilobytes of a single article image.
        assertEquals("the saving was in the wrong place", 2, requests.size)
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

    @Test
    fun `a reading from the last run is used instead of a request`() = runTest {
        val engine = MockEngine { request ->
            requests += request
            respond(forecast, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        weather(engine).current()
        assertEquals(1, requests.size)

        // A new instance, as after the system reclaimed the app, sharing only
        // what was written down.
        now = now.plusSeconds(60)
        val reading = weather(engine).current()

        assertEquals(1, requests.size)
        assertTrue(reading is WeatherResult.Loaded)
    }
}

/** Enough of a cache to test against: it keeps what it is given, and no file. */
private class RememberedInMemory : WeatherCache {
    private var held: CachedWeather? = null
    override suspend fun read(): CachedWeather? = held
    override suspend fun write(weather: CachedWeather) {
        held = weather
    }
}
