package moozy.mosaic.domain.model

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
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
    fun `the articles' cadence is the pair the README argues for`() {
        // Articles only. The weather had a pair here and does not any more:
        // every Open-Meteo reading says how often the source produces a new
        // one, so that window is the source's rather than one this app has to
        // defend. The articles have no such signal, so their two numbers stay
        // an argument -- and stay pinned, because an argument that can drift
        // without a test noticing is not an argument.
        // Pinned rather than bounded. A range lets somebody change the policy to
        // something the README no longer describes and still be green, and these
        // numbers are the answer to the assignment's question -- they are not an
        // implementation detail.
        assertEquals(Duration.ofMinutes(15), Cadence.ARTICLES.staleAfter)
        assertEquals(Duration.ofHours(1), Cadence.ARTICLES.staleAfterOnMeteredData)
    }
}
