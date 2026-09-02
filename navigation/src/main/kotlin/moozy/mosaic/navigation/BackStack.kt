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
 *
 * So the two directions really are an add and a remove, and they used to look
 * like it. They no longer do: [LateralSwitch] gives Saved the same slide either
 * way, because these two are siblings and only the stack has an opinion about
 * which sits under which.
 */
internal fun MutableList<NavKey>.goToDestination(destination: Destination) {
    when (destination) {
        Destination.READING -> while (size > 1) removeAt(size - 1)
        Destination.SAVED -> goTo(SavedKey)
    }
}

/**
 * Whether the bar at the bottom belongs on screen right now.
 *
 * The bar is what a reader navigates *with*, so it is not one of the things they
 * navigate *between* -- it lives outside `NavDisplay` and does not move when the
 * screens do (DECISIONS.md 42). But an article is not one of the two places it
 * switches between, and there is nothing for it to say there.
 *
 * Which of those two cases this is, is written on top of the stack already: an
 * article is the only key that is pushed *on top of* a destination rather than
 * instead of one. Reading that is why no screen has to be handed a flag about its
 * own chrome -- a screen that carried one could disagree with the stack, and the
 * stack is the thing that is actually true.
 */
internal fun List<NavKey>.showsTheBar(): Boolean = lastOrNull()?.place() != null

/**
 * Which of the two places the reader is in, article on top or not.
 *
 * Scanned from the top rather than taken from it. The bar has to keep saying
 * where the reader is while it slides away under an article, and while it slides
 * back, and in both of those moments the article is what is on top.
 *
 * [Destination.READING] answers a stack with neither key in it. That stack cannot
 * occur -- [FeedKey] is what the back stack is built on and [goToDestination]
 * keeps it at the bottom -- and it is the honest answer to the impossible case
 * for exactly that reason.
 */
internal fun List<NavKey>.destination(): Destination =
    asReversed().firstNotNullOfOrNull { it.place() } ?: Destination.READING

/** Which of the two a key is, or nothing for a key that is neither. */
private fun NavKey.place(): Destination? = when (this) {
    FeedKey -> Destination.READING
    SavedKey -> Destination.SAVED
    else -> null
}
