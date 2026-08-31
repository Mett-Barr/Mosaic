package moozy.mosaic.domain.model

import java.time.Instant

/**
 * What time it is, as something that can be handed over.
 *
 * A policy that reads the clock itself cannot be tested at a time other than now,
 * and freshness is entirely about times other than now.
 */
fun interface Clock {
    fun now(): Instant
}

/**
 * Whether reaching the network right now costs the reader money.
 *
 * The domain does not know what a mobile connection is; it knows that some
 * refreshes are free and some are charged, and that the difference should change
 * what the app does.
 */
fun interface DataCost {
    fun isMetered(): Boolean
}
