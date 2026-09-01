package moozy.mosaic.domain.model

import java.time.Instant

/**
 * What time it is, as something that can be handed over.
 *
 * Code that reads the clock itself cannot be tested at a time other than now,
 * and both of the things that use this -- pinning the window a page is read in,
 * and knowing when the weather source will next have a reading -- are entirely
 * about times other than now.
 */
fun interface Clock {
    fun now(): Instant
}
