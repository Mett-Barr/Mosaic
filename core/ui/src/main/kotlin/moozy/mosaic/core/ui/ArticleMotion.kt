package moozy.mosaic.core.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import moozy.mosaic.domain.model.ArticleId

/**
 * Hand the screens below the two scopes a shared element needs.
 *
 * Called by whoever owns the transition between screens, which here is `:navigation`
 * and nothing else. [origin] says which of the two lists the cards underneath belong
 * to; for the article screen it says which list the reader opened it from.
 */
@Composable
fun ProvideArticleMotion(
    origin: CardOrigin,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) {
    val motion = remember(origin, sharedTransitionScope, animatedVisibilityScope) {
        ArticleMotion(origin, sharedTransitionScope, animatedVisibilityScope)
    }
    CompositionLocalProvider(LocalArticleMotion provides motion, content = content)
}

/**
 * The card the reader tapped, becoming the article they tapped it for.
 *
 * Bounds rather than an element, because the two ends hold different things: a
 * title beside a thumbnail on one side, a whole article on the other. What carries
 * across is the rectangle, and the contents cross-fade inside it.
 *
 * [SharedTransitionScope.sharedBounds] already defaults `enter` and `exit` to
 * these two, and the container transform in the Compose documentation passes them
 * anyway. So does this, for the reason that example does: **this** is the only
 * cross-fade in the transition. The screens themselves no longer fade past each
 * other (see `CardBecomesArticle` in `:navigation`), so the one place a card's
 * contents become an article's contents is inside the rectangle carrying them,
 * and it should be readable here rather than inherited from a default.
 *
 * A caller puts its size modifiers *after* this one. That is what the Compose
 * documentation asks for -- *"Place size modifiers after the shared element
 * modifiers"* -- and, more to the point, it has to be the same on both sides:
 * modifiers before this one decide the bounds it animates, modifiers after it
 * measure what sits inside them, so a card sized before and an article sized
 * after are two different measurements of one rectangle.
 */
@Composable
fun Modifier.sharedArticleCard(id: ArticleId): Modifier {
    val motion = LocalArticleMotion.current ?: return this
    return with(motion.scope) {
        this@sharedArticleCard.sharedBounds(
            sharedContentState = rememberSharedContentState(motion.key(id, ArticlePart.CARD)),
            animatedVisibilityScope = motion.visibility,
            enter = fadeIn(),
            exit = fadeOut(),
            resizeMode = ResizeMode.scaleToBounds(),
        )
    }
}

/**
 * The article's picture, moving to where the article screen puts it.
 *
 * An element and not bounds: it is the same photograph at both ends, so there is
 * nothing to cross-fade between -- only a rectangle to travel and a crop to grow.
 */
@Composable
fun Modifier.sharedArticleImage(id: ArticleId): Modifier {
    val motion = LocalArticleMotion.current ?: return this
    return with(motion.scope) {
        this@sharedArticleImage.sharedElement(
            sharedContentState = rememberSharedContentState(motion.key(id, ArticlePart.IMAGE)),
            animatedVisibilityScope = motion.visibility,
        )
    }
}

/**
 * The article's title, moving to where the article screen puts it.
 *
 * Bounds and not an element, even though the words are the same: the card allows
 * the title two or three lines and the article allows it all of them, so a shared
 * element would re-flow the text mid-flight. [ResizeMode.scaleToBounds] measures
 * it once at the size it is going to be and scales that, which is what the Compose
 * documentation recommends for text and is the reason this one is not [sharedElement].
 */
@Composable
fun Modifier.sharedArticleTitle(id: ArticleId): Modifier {
    val motion = LocalArticleMotion.current ?: return this
    return with(motion.scope) {
        this@sharedArticleTitle.sharedBounds(
            sharedContentState = rememberSharedContentState(motion.key(id, ArticlePart.TITLE)),
            animatedVisibilityScope = motion.visibility,
            resizeMode = ResizeMode.scaleToBounds(),
        )
    }
}

/**
 * The scopes a shared element needs, from whoever is animating between screens.
 *
 * They travel down the composition rather than through Gradle, which is what lets
 * a feature take part in a transition without depending on the library running it:
 * [SharedTransitionScope] and [AnimatedVisibilityScope] are Compose types, and
 * Compose is something every screen here already has. See DECISIONS.md 32.
 */
private class ArticleMotion(
    val origin: CardOrigin,
    val scope: SharedTransitionScope,
    val visibility: AnimatedVisibilityScope,
)

/**
 * Null unless someone provided it, and null is not a failure.
 *
 * A `@Preview` and a test have no transition to take part in, so the three
 * modifiers above add nothing at all rather than throwing. A screen that could
 * only be drawn while it was moving would be a screen nobody could look at.
 */
private val LocalArticleMotion = compositionLocalOf<ArticleMotion?> { null }

/** Which part of an article the two screens are holding up against each other. */
private enum class ArticlePart { CARD, IMAGE, TITLE }

/**
 * What a shared element is matched by.
 *
 * A type and not a string, as the Compose documentation asks: two screens in two
 * modules have to arrive at the same key, and `"article-image-$id"` makes that a
 * spelling agreement the compiler has no opinion about. The id is in it because a
 * reader can open a second article before the first has finished leaving, and
 * without it the wrong picture would fly.
 */
private data class ArticleMotionKey(
    val id: ArticleId,
    val part: ArticlePart,
    val origin: CardOrigin,
)

private fun ArticleMotion.key(id: ArticleId, part: ArticlePart) =
    ArticleMotionKey(id = id, part = part, origin = origin)
