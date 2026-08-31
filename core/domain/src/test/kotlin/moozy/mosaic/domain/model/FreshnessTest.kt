package moozy.mosaic.domain.model

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Fresh" is not one number. It is a number that changes when asking again would
 * cost the reader money, and a different number for content that changes at a
 * different speed. This is the type that says so.
 */
class FreshnessTest {

    private val quarterHour = Freshness(
        staleAfter = Duration.ofMinutes(15),
        staleAfterOnMeteredData = Duration.ofHours(1),
    )
    private val noon: Instant = Instant.parse("2026-09-01T12:00:00Z")

    @Test
    fun `something fetched a moment ago is worth showing`() {
        assertFalse(quarterHour.isStale(fetchedAt = noon.minusSeconds(60), now = noon, metered = false))
    }

    @Test
    fun `something older than the window is worth asking about again`() {
        assertTrue(quarterHour.isStale(fetchedAt = noon.minusSeconds(16 * 60), now = noon, metered = false))
    }

    @Test
    fun `the same age is still fresh when asking would cost the reader money`() {
        val twentyMinutesOld = noon.minusSeconds(20 * 60)

        assertTrue(quarterHour.isStale(fetchedAt = twentyMinutesOld, now = noon, metered = false))
        assertFalse(quarterHour.isStale(fetchedAt = twentyMinutesOld, now = noon, metered = true))
    }

    @Test
    fun `content nobody has ever fetched is stale`() {
        assertTrue(quarterHour.isStale(fetchedAt = null, now = noon, metered = false))
    }

    @Test
    fun `the moment the window closes counts as stale`() {
        assertTrue(quarterHour.isStale(fetchedAt = noon.minusSeconds(15 * 60), now = noon, metered = false))
    }

    @Test
    fun `a clock that went backwards does not make content newer than fresh`() {
        // Phones change time zones, get their clock corrected, and cross the
        // international date line. Content stamped in the future is not a reason
        // to stop refreshing forever.
        assertFalse(quarterHour.isStale(fetchedAt = noon.plusSeconds(3600), now = noon, metered = false))
    }

    @Test
    fun `a policy that spends more of the reader's data than it saves cannot be built`() {
        assertThrows(IllegalArgumentException::class.java) {
            Freshness(
                staleAfter = Duration.ofHours(1),
                staleAfterOnMeteredData = Duration.ofMinutes(15),
            )
        }
    }

    @Test
    fun `a window of no time at all is not a window`() {
        assertThrows(IllegalArgumentException::class.java) {
            Freshness(staleAfter = Duration.ZERO, staleAfterOnMeteredData = Duration.ofHours(1))
        }
    }

    @Test
    fun `the article cadence is the one the feed uses`() {
        // Articles arrive through the day, not through the minute; a quarter of an
        // hour is short enough that a reader who comes back after lunch sees new
        // ones, and long enough that flicking in and out of the app costs nothing.
        assertTrue(Cadence.ARTICLES.staleAfter <= Duration.ofMinutes(30))
        assertTrue(Cadence.ARTICLES.staleAfterOnMeteredData >= Cadence.ARTICLES.staleAfter)
    }
}
