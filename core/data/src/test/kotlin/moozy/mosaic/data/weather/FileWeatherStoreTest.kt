package moozy.mosaic.data.weather

import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import moozy.mosaic.domain.model.ForecastDay
import moozy.mosaic.domain.model.Sky
import moozy.mosaic.domain.model.Weather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A reading is worth keeping across a launch for exactly as long as the freshness
 * policy says it is worth showing. Those are two different questions, and holding
 * the reading only in memory answered the second one by accident.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FileWeatherStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun store(file: File) = FileWeatherStore(file, UnconfinedTestDispatcher())

    @Test
    fun `a reading written down is there for the next run`() = runTest {
        val file = folder.newFile("weather.json")
        val fetchedAt = Instant.parse("2026-09-01T12:01:00Z")

        store(file).write(StoredReading(weather(), fetchedAt))

        val read = store(file).read()
        assertEquals(weather(), read?.weather)
        assertEquals(fetchedAt, read?.fetchedAt)
    }

    @Test
    fun `a cache nobody has written to has nothing in it`() = runTest {
        assertNull(store(File(folder.root, "never-written.json")).read())
    }

    @Test
    fun `a mangled cache reads as nothing rather than as a crash`() = runTest {
        val file = folder.newFile("weather.json")
        file.writeText("half a file")

        assertNull(store(file).read())
    }

    @Test
    fun `a cache whose values the domain refuses reads as nothing`() = runTest {
        val file = folder.newFile("weather.json")
        // Valid JSON, and a day that is colder at its warmest than at its coldest.
        file.writeText(
            """{"place":"Taipei","temperature":26,"high":10,"low":30,"sky":"CLOUDY",
               "measured_at":"2026-09-01T02:30:00Z","steps_every_seconds":900,
               "fetched_at":"2026-09-01T12:15:00Z"}"""
                .trimIndent(),
        )

        assertNull(store(file).read())
    }

    @Test
    fun `a time that is not a time reads as nothing rather than as a throw`() = runTest {
        val file = folder.newFile("weather.json")
        file.writeText(
            """{"place":"Taipei","temperature":26,"high":32,"low":25,"sky":"CLOUDY",
               "measured_at":"the day before yesterday","steps_every_seconds":900,
               "fetched_at":"2026-09-01T12:15:00Z"}"""
                .trimIndent(),
        )

        assertNull(store(file).read())
    }

    @Test
    fun `an unreadable moment to ask again reads as nothing too`() = runTest {
        val file = folder.newFile("weather.json")
        file.writeText(
            """{"place":"Taipei","temperature":26,"high":32,"low":25,"sky":"CLOUDY",
               "measured_at":"2026-09-01T02:30:00Z","steps_every_seconds":900,
               "fetched_at":"soon"}"""
                .trimIndent(),
        )

        assertNull(store(file).read())
    }

    @Test
    fun `the days ahead survive the trip through the file`() = runTest {
        val file = folder.newFile("weather.json")
        val forecast = weather().copy(days = threeDays())

        store(file).write(StoredReading(forecast, Instant.parse("2026-09-01T12:01:00Z")))

        // The whole reading, not only the strip: a store that dropped the days
        // and a store that kept them would both pass an assertion on the place.
        assertEquals(forecast, store(file).read()?.weather)
    }

    @Test
    fun `a reading written before there was a strip reads back without one`() = runTest {
        // Exactly what this store wrote until today. Losing it would cost a
        // launch's worth of card for no reason anybody could act on.
        val file = folder.newFile("weather.json")
        file.writeText(
            """{"place":"Taipei","temperature":26,"high":32,"low":25,"sky":"CLOUDY",
               "measured_at":"2026-09-01T02:30:00Z","steps_every_seconds":900,
               "fetched_at":"2026-09-01T12:15:00Z"}"""
                .trimIndent(),
        )

        assertEquals(weather(), store(file).read()?.weather)
    }

    @Test
    fun `a stored day whose date is not one reads as nothing rather than as a throw`() = runTest {
        val file = folder.newFile("weather.json")
        file.writeText(
            """{"place":"Taipei","temperature":26,"high":32,"low":25,"sky":"CLOUDY",
               "measured_at":"2026-09-01T02:30:00Z","steps_every_seconds":900,
               "fetched_at":"2026-09-01T12:15:00Z",
               "days":[{"date":"the day after next","high":32,"low":25,"sky":"CLOUDY"}]}"""
                .trimIndent(),
        )

        assertNull(store(file).read())
    }

    private fun threeDays() = listOf(
        ForecastDay(LocalDate.parse("2026-09-01"), high = 32, low = 25, sky = Sky.CLOUDY),
        ForecastDay(LocalDate.parse("2026-09-02"), high = 29, low = 24, sky = Sky.RAIN),
        ForecastDay(LocalDate.parse("2026-09-03"), high = 33, low = 26, sky = Sky.CLEAR),
    )

    private fun weather() = Weather(
        place = "Taipei",
        temperature = 26,
        high = 32,
        low = 25,
        sky = Sky.CLOUDY,
        measuredAt = Instant.parse("2026-09-01T02:30:00Z"),
        stepsEvery = Duration.ofMinutes(15),
    )
}
