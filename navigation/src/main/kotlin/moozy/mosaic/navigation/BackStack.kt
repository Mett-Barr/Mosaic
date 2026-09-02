package moozy.mosaic.navigation

import androidx.navigation3.runtime.NavKey

/**
 * Go somewhere, unless the reader is already going there.
 *
 * Two taps land before the first screen has drawn, and both are handled. Without
 * this the reader arrives at the same article twice and has to press back twice
 * to leave it once -- the second press looking, from where they are, like it did
 * nothing at all.
 */
internal fun MutableList<NavKey>.goTo(key: NavKey) {
    if (lastOrNull() != key) add(key)
}

/**
 * Reading is the bottom of the stack, so going back to it is going back.
 *
 * Deliberately not a second entry pushed on top: a reader who taps Reading and
 * then presses back would otherwise land on Saved, having never chosen it.
 */
internal fun MutableList<NavKey>.goToDestination(destination: Destination) {
    when (destination) {
        Destination.READING -> while (size > 1) removeAt(size - 1)
        Destination.SAVED -> goTo(SavedKey)
    }
}
