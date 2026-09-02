package moozy.mosaic.core.ui

/**
 * The two lists a card is drawn in, and so the two places an article is opened from.
 *
 * It is part of every shared element key because both lists are on screen at once
 * while the bar at the bottom slides between them, and an article kept from the
 * feed is in both of them. One key across that switch would match a card against
 * itself and fly it from its place in one list to its place in the other -- a
 * transition nobody asked for, in the middle of one they did.
 *
 * Only `:navigation` ever names it. The screens ask for `sharedArticleCard(id)`
 * and are told which list they are in by whoever is animating them.
 */
enum class CardOrigin { READING, SAVED }
