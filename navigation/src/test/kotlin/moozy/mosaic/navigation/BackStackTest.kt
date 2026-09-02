package moozy.mosaic.navigation

import androidx.navigation3.runtime.NavKey
import moozy.mosaic.core.ui.CardOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bar at the bottom is what a reader navigates *with*, so it is not one of the
 * things they navigate *between*. Something outside the screens therefore has to
 * decide when it belongs there -- and the back stack has already said: an article
 * is the only key that is pushed on top of one of the two places rather than
 * instead of one. Reading that is what saves every screen from being handed a flag
 * about its own chrome.
 */
class BackStackTest {

    @Test
    fun `the bar is there while one of the two places is on top`() {
        assertTrue("Reading", listOf<NavKey>(FeedKey).showsTheBar())
        assertTrue("Saved", listOf<NavKey>(FeedKey, SavedKey).showsTheBar())
    }

    @Test
    fun `an article is not one of the two places, so it has no bar`() {
        assertFalse(listOf<NavKey>(FeedKey, article()).showsTheBar())
        assertFalse(listOf<NavKey>(FeedKey, SavedKey, article(CardOrigin.SAVED)).showsTheBar())
    }

    /**
     * The bar has to keep saying where the reader is while it is sliding away, and
     * that is a moment when the article is what is on top of the stack.
     */
    @Test
    fun `an article does not change which of the two the reader is in`() {
        assertEquals(Destination.READING, listOf<NavKey>(FeedKey, article()).destination())
        assertEquals(
            Destination.SAVED,
            listOf<NavKey>(FeedKey, SavedKey, article(CardOrigin.SAVED)).destination(),
        )
    }

    @Test
    fun `switching between the two is what the bar reads`() {
        val stack = mutableListOf<NavKey>(FeedKey)

        stack.goToDestination(Destination.SAVED)
        assertEquals(Destination.SAVED, stack.destination())

        stack.goToDestination(Destination.READING)
        assertEquals(Destination.READING, stack.destination())
    }

    private fun article(from: CardOrigin = CardOrigin.READING) = ArticleKey("39742", from)
}
