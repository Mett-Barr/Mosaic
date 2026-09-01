package moozy.mosaic.domain.model

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A reading is one step of a series the source produces on its own schedule, and
 * it says what that schedule is. So the moment it is worth asking again is not a
 * number this app chooses -- it is the source's next step.
 *
 * Choosing a number instead guarantees waste: Open-Meteo steps every fifteen
 * minutes, so a ten-minute window means a third of all requests return the
 * reading already held.
 */
class WeatherTest {

    @Test
    fun `the next time worth asking is the source's next step`() {
        // Fetched a minute after the source published 12:30.
        val reading = readingAt("2026-09-01T12:30:00Z")

        val again = reading.askAgainAfter(fetchedAt = Instant.parse("2026-09-01T12:31:00Z"))

        assertEquals(Instant.parse("2026-09-01T12:45:00Z"), again)
    }

    @Test
    fun `asking just before a step waits only until that step`() {
        val reading = readingAt("2026-09-01T12:30:00Z")

        val again = reading.askAgainAfter(fetchedAt = Instant.parse("2026-09-01T12:44:00Z"))

        assertEquals(
            "a minute away is a minute to wait, not another whole step",
            Instant.parse("2026-09-01T12:45:00Z"),
            again,
        )
    }

    @Test
    fun `a source publishing late does not become a request loop`() {
        // Measured: asked at 12:52, answered with the 12:30 reading. The source
        // publishes behind its own grid. Naively measuredAt + interval is 12:45,
        // already past, so the reading would be stale on arrival and ask again
        // for the same value forever.
        val reading = readingAt("2026-09-01T12:30:00Z")

        val again = reading.askAgainAfter(fetchedAt = Instant.parse("2026-09-01T12:52:00Z"))

        assertEquals(
            "the wait absorbs the source's lag rather than fighting it",
            Instant.parse("2026-09-01T13:00:00Z"),
            again,
        )
    }

    @Test
    fun `a reading landing exactly on a step still waits a whole step`() {
        val reading = readingAt("2026-09-01T12:30:00Z")

        val again = reading.askAgainAfter(fetchedAt = Instant.parse("2026-09-01T12:45:00Z"))

        assertEquals(Instant.parse("2026-09-01T13:00:00Z"), again)
    }

    @Test
    fun `a reading from a clock that ran ahead is not asked about again immediately`() {
        val reading = readingAt("2026-09-01T13:00:00Z")

        val again = reading.askAgainAfter(fetchedAt = Instant.parse("2026-09-01T12:31:00Z"))

        assertEquals(
            "a stamp in the future is still one step's worth of reading",
            Instant.parse("2026-09-01T13:15:00Z"),
            again,
        )
    }

    private fun readingAt(measured: String) = Weather(
        place = "Taipei",
        temperature = 30,
        high = 31,
        low = 26,
        sky = Sky.CLOUDY,
        measuredAt = Instant.parse(measured),
        stepsEvery = Duration.ofMinutes(15),
    )
}
