package moozy.mosaic.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import moozy.mosaic.core.ui.CardOrigin

/**
 * The two places a reader can be. Navigation 3 restores a back stack by
 * serialising its keys, which is why they are @Serializable and why the article's
 * id crosses as a String rather than as the domain's own type: what is written to
 * a Bundle is a detail of getting back to a screen, not of the domain.
 *
 * They are internal because nothing outside this module names a screen: the
 * features take callbacks, and :app calls one composable.
 */
@Serializable
internal data object FeedKey : NavKey

/**
 * [from] is which list the reader tapped, and it is here rather than in a second
 * piece of state beside the stack because the stack is already the record of where
 * they came from. The card that grows into this screen has to be the one they
 * touched, and the same article can be sitting in both lists at once
 * (DECISIONS.md 32).
 */
@Serializable
internal data class ArticleKey(val id: String, val from: CardOrigin) : NavKey

@Serializable
internal data object SavedKey : NavKey
