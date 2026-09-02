package moozy.mosaic.data.weather

import app.cash.turbine.test
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import moozy.mosaic.domain.model.Clock
import moozy.mosaic.domain.model.Sky
import moozy.mosaic.domain.model.Weather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The weather is a value that changes on its own, so the repository offers a
 * stream of it rather than a question to ask.
 *
 * Who asks and when stops being a decision anybody outside has to make. While
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

        weather.current.test {
            assertNull("nothing is known before the first answer", awaitItem())
            assertEquals(26, awaitItem()?.temperature)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `it asks the place it was given about, and for no more than the card shows`() = runTest {
        val asked = mutableListOf<HttpRequestData>()
        val weather = weather(
            MockEngine { request ->
                asked += request
                requests++
                respond(forecast(), HttpStatusCode.OK, json)
            },
        )

        weather.current.test {
            awaitItem()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        val url = asked.first().url
        assertEquals("api.open-meteo.com", url.host)
        assertEquals("25.033", url.parameters["latitude"])
        assertEquals("121.5654", url.parameters["longitude"])
        assertEquals("temperature_2m,weather_code", url.parameters["current"])
        assertEquals("temperature_2m_max,temperature_2m_min", url.parameters["daily"])
        assertEquals("1", url.parameters["forecast_days"])
    }

    @Test
    fun `the reading comes back whole, named after the place rather than the time zone`() = runTest {
        val weather = weather(alwaysAnswering())

        weather.current.test {
            awaitItem()
            val shown = awaitItem()
            cancelAndIgnoreRemainingEvents()

            assertEquals("Taipei", shown?.place)
            assertEquals(26, shown?.temperature)
            assertEquals(32, shown?.high)
            assertEquals(25, shown?.low)
            assertEquals(Sky.CLOUDY, shown?.sky)
            assertEquals(Instant.parse("2026-09-01T12:00:00Z"), shown?.measuredAt)
            // The source's own step, carried so that nothing here has to guess it.
            assertEquals(Duration.ofMinutes(15), shown?.stepsEvery)
        }
    }

    @Test
    fun `a reading written down last time is shown before anything is asked`() = runTest {
        val store = InMemoryStore()
        store.write(StoredReading(readingFromBefore(), Instant.parse("2026-09-01T11:59:00Z")))
        val weather = weather(alwaysAnswering(), store)

        weather.current.test {
            assertNull(awaitItem())
            assertEquals("the last run's answer arrives before the network's", 18, awaitItem()?.temperature)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `while watched, a new reading replaces the old one`() = runTest {
        val weather = weather(
            MockEngine {
                requests++
                respond(forecast(temperature = if (requests == 1) 25.6 else 31.4), HttpStatusCode.OK, json)
            },
        )

        weather.current.test {
            assertNull(awaitItem())
            assertEquals(26, awaitItem()?.temperature)

            // The source has stepped, so there is something new to be had.
            now = now.plusSeconds(15 * 60)
            assertEquals("the stream keeps itself current", 31, awaitItem()?.temperature)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `once nobody is watching it stops asking`() = runTest {
        val weather = weather(alwaysAnswering())

        weather.current.test {
            awaitItem()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        val whenWatched = requests
        // Long past the grace period and several steps of the source.
        now = now.plusSeconds(60 * 60)
        advanceTimeBy(60 * 60 * 1000L)
        runCurrent()

        assertEquals("an unwatched screen is not worth a request", whenWatched, requests)
    }

    @Test
    fun `a reading survives a failure rather than being replaced by one`() = runTest {
        val weather = weather(
            MockEngine {
                requests++
                if (requests == 1) {
                    respond(forecast(), HttpStatusCode.OK, json)
                } else {
                    respondError(HttpStatusCode.InternalServerError)
                }
            },
        )

        weather.current.test {
            assertNull(awaitItem())
            assertEquals(26, awaitItem()?.temperature)
            now = now.plusSeconds(15 * 60)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(
            "a failed request is not a reason to blank the card",
            26,
            weather.current.value?.temperature,
        )
    }

    @Test
    fun `nothing ever arriving is nothing rather than a guess`() = runTest {
        val weather = weather(MockEngine { requests++; respondError(HttpStatusCode.InternalServerError) })

        weather.current.test {
            assertNull(awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun TestScope.weather(engine: MockEngine, store: WeatherStore = InMemoryStore()) =
        OpenMeteoWeather(
            api = OpenMeteoApi(
                client = openMeteoClient(engine),
                place = Place(name = "Taipei", latitude = 25.033, longitude = 121.5654),
            ),
            clock = Clock { now },
            store = store,
            scope = backgroundScope,
        )

    private fun alwaysAnswering() = MockEngine {
        requests++
        respond(forecast(), HttpStatusCode.OK, json)
    }

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun forecast(temperature: Double = 25.6) = """
        {"utc_offset_seconds": 28800, "timezone": "Asia/Taipei",
         "current": {"interval": 900, "time": "2026-09-01T20:00",
                     "temperature_2m": $temperature, "weather_code": 3},
         "daily": {"temperature_2m_max": [31.8], "temperature_2m_min": [25.4]}}
    """.trimIndent()

    private fun readingFromBefore() = Weather(
        place = "Taipei",
        temperature = 18,
        high = 20,
        low = 15,
        sky = Sky.CLEAR,
        measuredAt = Instant.parse("2026-09-01T11:45:00Z"),
        stepsEvery = Duration.ofMinutes(15),
    )

    private class InMemoryStore : WeatherStore {
        private var held: StoredReading? = null
        override suspend fun read(): StoredReading? = held
        override suspend fun write(reading: StoredReading) {
            held = reading
        }
    }
}
