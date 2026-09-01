package moozy.mosaic.data.weather

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import moozy.mosaic.domain.model.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The weather is a value that changes on its own, so the repository offers a
 * stream of it rather than a question to ask.
 *
 * Who asks and when stops being a decision anybody outside has to make: while
 * somebody is watching, this keeps the reading current on the source's own
 * schedule; while nobody is, it holds the last one and does nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpenMeteoStreamTest {

    private var requests = 0
    private var now: Instant = Instant.parse("2026-09-01T12:00:00Z")

    @Test
    fun `nobody watching is nobody's data spent`() = runTest {
        weather(alwaysAnswering())

        runCurrent()

        assertEquals("a repository is not a reason to make a request", 0, requests)
    }

    @Test
    fun `the first watcher gets a reading`() = runTest {
        val weather = weather(alwaysAnswering())

        val seen = watch(weather)

        assertEquals(1, requests)
        assertEquals(26, seen.last()?.temperature)
    }

    @Test
    fun `while watched, it asks again when the source has stepped`() = runTest {
        val weather = weather(alwaysAnswering())

        val watching = launch { weather.current.collect {} }
        runCurrent()
        assertEquals(1, requests)

        // The reading is stamped 12:00 and the source steps every 15 minutes.
        now = now.plusSeconds(15 * 60)
        advanceTimeBy(15 * 60 * 1000L)
        runCurrent()
        watching.cancel()

        assertEquals("the stream keeps itself current", 2, requests)
    }

    @Test
    fun `once nobody is watching it stops asking`() = runTest {
        val weather = weather(alwaysAnswering())

        val watching = launch { weather.current.collect {} }
        runCurrent()
        watching.cancel()
        // Long past the grace period and several steps of the source.
        now = now.plusSeconds(60 * 60)
        advanceTimeBy(60 * 60 * 1000L)
        runCurrent()

        assertEquals("an unwatched screen is not worth a request", 1, requests)
    }

    @Test
    fun `a reading survives a failure rather than being replaced by one`() = runTest {
        val engine = MockEngine {
            requests++
            if (requests == 1) {
                respond(forecast, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respondError(HttpStatusCode.InternalServerError)
            }
        }
        val weather = weather(engine)

        val watching = launch { weather.current.collect {} }
        runCurrent()
        now = now.plusSeconds(15 * 60)
        advanceTimeBy(15 * 60 * 1000L)
        runCurrent()
        val shown = weather.current.value
        watching.cancel()

        assertEquals("a failed request is not a reason to blank the card", 26, shown?.temperature)
    }

    @Test
    fun `nothing to show is nothing rather than a guess`() = runTest {
        val weather = weather(MockEngine { requests++; respondError(HttpStatusCode.InternalServerError) })

        val seen = watch(weather)

        assertNull(seen.last())
    }

    private fun TestScope.watch(weather: OpenMeteoWeather): List<moozy.mosaic.domain.model.Weather?> {
        val seen = mutableListOf<moozy.mosaic.domain.model.Weather?>()
        val watching = launch { weather.current.collect { seen += it } }
        runCurrent()
        watching.cancel()
        return seen
    }

    private fun TestScope.weather(engine: MockEngine) = OpenMeteoWeather(
        client = openMeteoClient(engine),
        place = Place(name = "Taipei", latitude = 25.033, longitude = 121.5654),
        clock = Clock { now },
        store = InMemoryStore(),
        scope = backgroundScope,
    )

    private fun alwaysAnswering() = MockEngine {
        requests++
        respond(forecast, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    private val forecast = """
        {"utc_offset_seconds": 28800, "timezone": "Asia/Taipei",
         "current": {"interval": 900, "time": "2026-09-01T20:00", "temperature_2m": 25.6, "weather_code": 3},
         "daily": {"temperature_2m_max": [31.8], "temperature_2m_min": [25.4]}}
    """.trimIndent()

    private class InMemoryStore : WeatherStore {
        private var held: StoredReading? = null
        override suspend fun read(): StoredReading? = held
        override suspend fun write(reading: StoredReading) {
            held = reading
        }
    }
}

