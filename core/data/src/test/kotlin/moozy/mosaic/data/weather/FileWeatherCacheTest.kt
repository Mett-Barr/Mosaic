package moozy.mosaic.data.weather

import java.io.File
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
class FileWeatherCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun cache(file: File) = FileWeatherCache(file, UnconfinedTestDispatcher())

    @Test
    fun `a reading written down is there for the next run`() = runTest {
        val file = folder.newFile("weather.json")
        val askAgainAt = Instant.parse("2026-09-01T12:15:00Z")

        cache(file).write(CachedWeather(weather(), askAgainAt))

        val read = cache(file).read()
        assertEquals(weather(), read?.weather)
        assertEquals(askAgainAt, read?.askAgainAt)
    }

    @Test
    fun `a cache nobody has written to has nothing in it`() = runTest {
        assertNull(cache(File(folder.root, "never-written.json")).read())
    }

    @Test
    fun `a mangled cache reads as nothing rather than as a crash`() = runTest {
        val file = folder.newFile("weather.json")
        file.writeText("half a file")

        assertNull(cache(file).read())
    }

    @Test
    fun `a cache whose values the domain refuses reads as nothing`() = runTest {
        val file = folder.newFile("weather.json")
        // Valid JSON, and a day that is colder at its warmest than at its coldest.
        file.writeText(
            """{"place":"Taipei","temperature":26,"high":10,"low":30,"sky":"CLOUDY",
               "measured_at":"2026-09-01T02:30:00Z","steps_every_seconds":900,
               "ask_again_at":"2026-09-01T12:15:00Z"}"""
                .trimIndent(),
        )

        assertNull(cache(file).read())
    }

    @Test
    fun `a time that is not a time reads as nothing rather than as a throw`() = runTest {
        val file = folder.newFile("weather.json")
        file.writeText(
            """{"place":"Taipei","temperature":26,"high":32,"low":25,"sky":"CLOUDY",
               "measured_at":"the day before yesterday","steps_every_seconds":900,
               "ask_again_at":"2026-09-01T12:15:00Z"}"""
                .trimIndent(),
        )

        assertNull(cache(file).read())
    }

    @Test
    fun `an unreadable moment to ask again reads as nothing too`() = runTest {
        val file = folder.newFile("weather.json")
        file.writeText(
            """{"place":"Taipei","temperature":26,"high":32,"low":25,"sky":"CLOUDY",
               "measured_at":"2026-09-01T02:30:00Z","steps_every_seconds":900,
               "ask_again_at":"soon"}"""
                .trimIndent(),
        )

        assertNull(cache(file).read())
    }

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
