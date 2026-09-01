package moozy.mosaic.domain.model

import java.time.Duration
import java.time.Instant

/**
 * How long an answer stays good enough to reuse.
 *
 * What this measures is **when it was asked for**, not when the thing it
 * describes happened. "Fifteen minutes" is a promise not to ask again inside
 * fifteen minutes; it is not a promise the answer is at most fifteen minutes
 * old. For the weather the two differ: Open-Meteo's current conditions come
 * from a model that steps every fifteen minutes, so a reading fetched a moment
 * ago may already describe a moment further back. The card says what it was
 * told; the policy governs the asking, which is the one of the two this app can
 * control.
 *
 * Two windows, because the question has two answers. On a connection the reader
 * is not paying for, "reasonably fresh" is the only consideration. On mobile
 * data every refresh is a small charge on somebody's plan, and a feed fifteen
 * minutes out of date is a much smaller problem than a feed that quietly spends
 * their allowance while they read it.
 *
 * The two are one type rather than two settings so that they cannot drift
 * apart, and so that the metered window cannot accidentally be made the shorter
 * one -- which would spend more data than having no policy at all.
 */
data class Freshness(
    val staleAfter: Duration,
    val staleAfterOnMeteredData: Duration,
) {
    init {
        require(!staleAfter.isNegative && !staleAfter.isZero) {
            "Content that is stale the moment it arrives has no window at all."
        }
        require(staleAfterOnMeteredData >= staleAfter) {
            "Refreshing sooner on metered data spends more of it, not less."
        }
    }

    /**
     * [fetchedAt] is null for content nobody has ever asked for, which is stale by
     * definition: there is nothing to show.
     *
     * A [fetchedAt] in the future is treated as fresh rather than as an error. A
     * phone's clock moves -- time zones, corrections, the date line -- and the
     * alternative is a feed that refuses to stop refreshing until the clock
     * catches up with a stamp it wrote itself.
     */
    fun isStale(fetchedAt: Instant?, now: Instant, metered: Boolean): Boolean {
        if (fetchedAt == null) return true
        val window = if (metered) staleAfterOnMeteredData else staleAfter
        return Duration.between(fetchedAt, now) >= window
    }
}

/**
 * What each kind of content's window actually is.
 *
 * The numbers are the argument, so they are written down where they can be
 * argued with rather than spread through the code that uses them. The README
 * carries the reasoning; this carries the decision.
 */
object Cadence {

    /**
     * Articles are published through the day rather than through the minute. A
     * quarter of an hour is short enough that a reader coming back after lunch
     * sees new ones, and long enough that flicking in and out of the app costs
     * nothing. On mobile data the same list is worth an hour: a feed an hour old
     * is still a feed, and four avoided refreshes is real.
     */
    val ARTICLES = Freshness(
        staleAfter = Duration.ofMinutes(15),
        staleAfterOnMeteredData = Duration.ofHours(1),
    )

}
