package moozy.mosaic.navigation

/**
 * The two places the bar at the bottom switches between.
 *
 * Its own type rather than two of the keys, because the bar and the back stack
 * have to agree on what "where the reader is" means, and a key is only where
 * one entry is.
 */
internal enum class Destination(val label: String) {
    READING("Reading"),
    SAVED("Saved"),
}
