package moozy.mosaic.core.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.SharedTransitionScope.ResizeMode
import androidx.compose.animation.SharedTransitionScope.SharedContentState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
 * Which of the two ends of the flight a caller is drawing.
 *
 * The corners belong to the end and not to the element. A card sits inside a list
 * that has margins of its own, so it is rounded; the article fills the display, and
 * something touching the edge of the display should not be rounded -- there is no
 * background left for the corner to be cut out of. So neither end asks for a shape.
 * Each says where it is, and the one radius lives below, in one place, because the
 * two ends still have to agree about what the *other* one looks like.
 *
 * Only the container is told this. A picture inside it says where it sits instead
 * (see [PictureSeat]), because whether the container's corners reach it at all is
 * a question about the layout and not about which end of the flight this is.
 */
enum class ArticleEnd { IN_A_LIST, FILLING_THE_DISPLAY }

/**
 * Where a picture sits in the thing that holds it, which is what decides its corners.
 *
 * **A picture is cut by its container only where it actually meets the container's
 * edge.** The lead story's photograph is the top of its card: it is flush with the
 * card's left, right and top, so the two corners it needs are the card's own top
 * two, and it gets them by asking for no shape and letting the card cut it. The
 * article's photograph is the same arrangement one size up -- flush with three of
 * the display's edges, square because there is no background left for a corner to
 * be cut out of.
 *
 * A row's thumbnail is not that. It is a small square inset in its row -- four dp
 * clear of the top and the bottom, a column of words clear of the right -- and the
 * one edge it does share with the row, the left, is shared for ninety-two dp of a
 * corner twenty dp across. So three of its corners are somewhere the container's
 * clip cannot reach and the fourth is barely grazed by it, and being cut by the
 * container amounts to not being cut. It has to carry its own.
 *
 * The bottom edge is the part that makes this a seat and not a radius: both
 * pictures that meet an edge have words carrying on underneath them, and rounding
 * their bottom corners would curve a photograph away from the paragraph it belongs
 * to. That is one shape, and the thumbnail's four equal corners are another; there
 * is no single radius that is right for both, which is why the caller says where it
 * is rather than how round it is.
 */
enum class PictureSeat { MEETING_AN_EDGE, STANDING_ALONE }

/** The corners a card keeps in either list. */
private val CardCorner = 20.dp

/** No corners at all, for the end that has the display's own edge for a border. */
private val FlushCorner = 0.dp

/**
 * One curve, for every part of one movement.
 *
 * **The two halves of this transition were on two different springs, and neither
 * of them was chosen.** Each was a library default and the defaults disagree:
 * `Transition.animateDp` defaults to `spring(visibilityThreshold =
 * Dp.VisibilityThreshold)`, which is [Spring.StiffnessMedium] -- 1500 -- while
 * `SharedTransitionDefaults.BoundsTransform` is `spring(stiffness =
 * StiffnessMediumLow, visibilityThreshold = Rect.VisibilityThreshold)`, which is
 * 400. Both are critically damped, so the stiffer one is simply the faster one:
 * twenty dp of corner settling to within four tenths of a dp takes about 0.15s
 * at 1500, while a rectangle growing to the whole display and settling to within
 * a pixel takes about half a second at 400.
 *
 * So the card finished squaring itself off inside the first third of the flight
 * and then grew the rest of the way as a rectangle with sharp corners; coming
 * back it was at a full twenty dp long before it had finished shrinking. That is
 * not what DECISIONS.md 35 describes and not what a container transform is.
 *
 * [Spring.StiffnessMediumLow] is the one kept, for two reasons: it is the slower
 * of the two, and the corner has to keep up with the rectangle rather than the
 * other way round; and it is what everything else in this transition already
 * runs on -- the bounds by default, and `fadeIn()`/`fadeOut()`, whose own
 * default spec is `spring(stiffness = Spring.StiffnessMediumLow)`.
 *
 * The visibility thresholds stay per-type. They are not part of the curve: they
 * say how close to the end is close enough to stop, and "close enough" for a
 * radius in dp and for a rectangle in pixels are different questions.
 */
private val OneSpring: FiniteAnimationSpec<Dp> = spring(
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = Dp.VisibilityThreshold,
)

/**
 * The same curve again, for the rectangle the corner belongs to.
 *
 * Written out rather than left to `SharedTransitionDefaults.BoundsTransform`,
 * even though the numbers it produces are the default's numbers. A default that
 * happens to agree is not an agreement -- it is what let the corner drift onto a
 * different spring without anything in this file changing. Passed explicitly,
 * the two are one edit apart instead of one release apart.
 */
private val OneSpringForBounds = BoundsTransform { _, _ ->
    spring(
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = Rect.VisibilityThreshold,
    )
}

/**
 * The corners a card is drawn with, for the surface painting them behind itself.
 *
 * Here rather than in the feed because it is one half of an agreement: the shape a
 * card's [androidx.compose.material3.Surface] paints has to be the shape
 * [sharedArticleCard] clips to while that card is standing still, or the background
 * and the contents inside it would round by different amounts. The travelling half
 * of the agreement is [ArticleEnd]'s.
 */
val CardShape: Shape = RoundedCornerShape(CardCorner)

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
 *
 * The clip declared here is not only this rectangle's. It is also the shape
 * anything nested inside it is cut to while the transition runs, which is what a
 * picture flush with this rectangle's edge leans on instead of declaring corners
 * of its own -- and, for one that is nowhere near it, why that is not enough
 * (see [PictureSeat]).
 *
 * **The two ends are lined up by their top edges and not by their middles.**
 * `scaleToBounds` measures each end once at the size it will finish at and then
 * scales that one drawing into whatever the bounds are on this frame; with the
 * default `ContentScale.FillWidth`, an article measured for the whole display and
 * scaled to a card's width is several times taller than the card it has to fit
 * in. The default [Alignment] of `Center` would therefore park the middle of the
 * article inside the card and push the picture off the top -- while the picture
 * is flying separately to the top of that same card, so the two would spend the
 * whole transition disagreeing about where the top is. Top edge to top edge is
 * the one alignment they both mean.
 *
 * **Scaled and not remeasured**, even though this rectangle now paints the
 * article's background (DECISIONS.md 38) and the documentation says
 * [ResizeMode.RemeasureToBounds] *"works best for background"*. The rest of that
 * sentence is why not: it *"does not work well for layouts with specific size
 * requirements. Such layouts include Text, and bespoke layouts that could result
 * in overlapping children when constrained to too small of a size"* -- which is
 * the rest of what is in here, a whole scrollable article, and it would be
 * re-measured against a card's constraints on every frame. The background is the
 * smaller half of the cargo.
 */
@Composable
fun Modifier.sharedArticleCard(id: ArticleId, at: ArticleEnd): Modifier {
    val motion = LocalArticleMotion.current
        ?: return this.clip(RoundedCornerShape(at.corner(CardCorner)))
    val radius = motion.corner(at, CardCorner)
    return with(motion.scope) {
        val card = rememberSharedContentState(motion.key(id, ArticlePart.CARD))
        val overlay = remember(radius) { OverlayClip(TravellingCorner(radius)) }
        this@sharedArticleCard
            .sharedBounds(
                sharedContentState = card,
                animatedVisibilityScope = motion.visibility,
                enter = fadeIn(),
                exit = fadeOut(),
                boundsTransform = OneSpringForBounds,
                resizeMode = ResizeMode.scaleToBounds(alignment = Alignment.TopCenter),
                clipInOverlayDuringTransition = overlay,
            )
            .roundedBy(radius, standingStill = at.corner(CardCorner), whileMatched = card)
    }
}

/**
 * The article's picture, moving to where the article screen puts it.
 *
 * An element and not bounds: it is the same photograph at both ends, so there is
 * nothing to cross-fade between -- only a rectangle to travel and a crop to grow.
 *
 * **[at] decides whether it asks for a shape, and most of the time it does not.**
 * A picture flush with the edge of what holds it is cut by that container, and the
 * container is the one thing that knows how round its own corners currently are.
 *
 * That holds while the picture is in the air as well, which is the part worth
 * saying out loud. The overlay a shared element is lifted into *"will escape the
 * parent's bounds and its layer transformations"*, so a container's own clip stops
 * applying for the length of the flight -- but `clipInOverlayDuringTransition`
 * defaults to the parent's, which the Compose documentation states as *"the
 * [SharedTransitionScope.sharedElement] is clipped by the
 * `clipInOverlayDuringTransition` of its parent [SharedTransitionScope.sharedBounds]"*.
 * The parent here is [sharedArticleCard], whose clip is the one already travelling
 * between the two radii. Not passing one is therefore not an omission: it is how
 * the picture is cut by the card on every frame rather than on most of them.
 *
 * **[PictureSeat.STANDING_ALONE] is where that whole argument stops applying**, and
 * the clip is the picture's own. Inheriting a clip is only worth anything to
 * something the clip reaches; a thumbnail inset in a row is not reached by the row's
 * corners standing still or flying, so it is handed the shape the design gives an
 * inset picture and keeps it either way.
 */
@Composable
fun Modifier.sharedArticleImage(id: ArticleId, at: PictureSeat): Modifier {
    val motion = LocalArticleMotion.current ?: return this.ownCorners(at)
    return with(motion.scope) {
        this@sharedArticleImage
            .sharedElement(
                sharedContentState = rememberSharedContentState(motion.key(id, ArticlePart.IMAGE)),
                animatedVisibilityScope = motion.visibility,
                boundsTransform = OneSpringForBounds,
            )
            .ownCorners(at)
    }
}

/**
 * The corners a picture cuts for itself, which is none unless nothing else will.
 *
 * `medium` and not the card's radius, because it is not the card: the design draws
 * an inset thumbnail with the same corner it gives every other small inset thing,
 * and [CardCorner] is the radius of the rectangle around all of them.
 *
 * Inside the shared element rather than in front of it, so that it is the picture
 * being clipped and not the element carrying it. In front, the clip would be an
 * ancestor of the shared element -- and an element lifted into the overlay
 * *"will escape the parent's bounds and its layer transformations"*, so the
 * corners would be there while the picture sat still and gone for the length of
 * every flight. Behind it, the picture is measured to whatever bounds it has this
 * frame and then cut, which is the same sentence standing still and moving.
 */
@Composable
private fun Modifier.ownCorners(at: PictureSeat): Modifier =
    if (at == PictureSeat.STANDING_ALONE) clip(MaterialTheme.shapes.medium) else this

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
            boundsTransform = OneSpringForBounds,
            resizeMode = ResizeMode.scaleToBounds(),
        )
    }
}

/**
 * Where the article came from and when, moving to where the article screen puts it.
 *
 * The title's sibling, and shaped like it for the same reasons: one line of the
 * same words at both ends, so bounds rather than an element, and scaled rather
 * than remeasured because it is text.
 *
 * **Not every list can call this, and that is the point of it being a separate
 * modifier rather than something the card does for whatever is inside it.** The
 * two ends have to be holding up the *same* line. The feed's cards and the
 * article both write source and time together; the Saved list has only ever kept
 * the source, so its line is a shorter one that happens to start with the same
 * word. Matching those two would measure one and scale it to the other's width,
 * which is a source name stretching as it flies (DECISIONS.md 39).
 */
@Composable
fun Modifier.sharedArticleAttribution(id: ArticleId): Modifier {
    val motion = LocalArticleMotion.current ?: return this
    return with(motion.scope) {
        this@sharedArticleAttribution.sharedBounds(
            sharedContentState = rememberSharedContentState(
                motion.key(id, ArticlePart.ATTRIBUTION),
            ),
            animatedVisibilityScope = motion.visibility,
            boundsTransform = OneSpringForBounds,
            resizeMode = ResizeMode.scaleToBounds(),
        )
    }
}

/**
 * The radius this end is drawing right now, somewhere between the two ends.
 *
 * **There is no *automatic* shape animation in Compose, which is not the same as
 * there being none.** [OverlayClip] is an interface handed the *current* animated
 * bounds on every frame, so what it clips to is free to be a function of where the
 * transition has got to. This one drives that function off the transition itself:
 * [AnimatedVisibilityScope.transition] runs `PreEnter -> Visible` on the end that is
 * arriving and `Visible -> PostExit` on the end that is leaving, so `Visible` means
 * *this* end and either of the others means *the other* end. Both ends are children
 * of one `AnimatedContent` transition and both compute the same number from it, so
 * they agree frame for frame without either one knowing what the other is.
 *
 * **The alternative was to derive the radius from the bounds' width**, which the
 * [OverlayClip] interface also allows and which would need no state at all. It was
 * not taken because width does not separate the two ends here: the lead story's
 * picture is already the display width less the list's 16dp of padding, so a radius
 * read off the width would have to fall from full to nothing inside those last few
 * dp -- a jump wearing an animation's clothes -- or round the lead card's picture
 * almost square while it is sitting still. The transition's progress is the thing
 * that actually runs from one end to the other; the width only nearly does.
 *
 * **[OneSpring] is passed rather than left to `animateDp`'s default**, because the
 * default is a faster curve than the rectangle's and a corner that arrives first
 * is a corner that spends the rest of the flight being wrong.
 */
@Composable
private fun ArticleMotion.corner(at: ArticleEnd, inAList: Dp): State<Dp> =
    visibility.transition.animateDp(
        transitionSpec = { OneSpring },
        label = "article corner",
    ) { state ->
        val drawing = if (state == EnterExitState.Visible) at else at.other
        drawing.corner(inAList)
    }

/** [inAList] at one end, and the display's own edge at the other. */
private fun ArticleEnd.corner(inAList: Dp): Dp =
    if (this == ArticleEnd.IN_A_LIST) inAList else FlushCorner

private val ArticleEnd.other: ArticleEnd
    get() = if (this == ArticleEnd.IN_A_LIST) {
        ArticleEnd.FILLING_THE_DISPLAY
    } else {
        ArticleEnd.IN_A_LIST
    }

/**
 * The same corner as the overlay's, for the frames the element is not in the overlay.
 *
 * A layer block rather than [Modifier.clip], because `clip` takes the shape once at
 * composition and would keep clipping to the radius the first frame happened to
 * have. Read inside the block, the radius reaches the layer on every frame it
 * changes without recomposing anything.
 *
 * **[whileMatched] is why twenty untouched cards do not square their corners off
 * when one of them is tapped.** The transition driving [radius] belongs to the
 * whole entry, not to the card that was tapped: every card in the list runs
 * `Visible -> PostExit` on the way out and `PreEnter -> Visible` on the way back,
 * so every card was computing the same travelling radius and drawing it. Only the
 * card with a match is going anywhere, and only it should be moving; the rest sit
 * at [standingStill], which is the radius the end they are at asks for.
 *
 * **The gate is read here and not in composition, and that is the whole trick.**
 * `isMatchFound` is false during the composition that declares an element and only
 * becomes true once the other end has been composed -- for a card in a `LazyColumn`
 * that happens inside the measure pass, which the property's own documentation
 * spells out. Compose's `sharedBounds` hits exactly this and answers it by never
 * reading the flag in composition either: it passes `isEnabled =
 * { sharedContentState.isMatchFound }` as a lambda, *"defer[ring] the decision to
 * enable or disable content scaling until later in the frame"*. A layer block is
 * already later in the frame. Choosing between the two radii up in composition
 * would instead give the arriving end one frame at the radius it is going to
 * finish at, before the animation it should have started from was allowed to
 * exist -- which is a jump at the exact moment a reader is watching for movement.
 *
 * **[radius] is read before the gate rather than inside the branch it belongs to,
 * and that is not an accident.** A layer block re-runs when something it read last
 * time changes, so what it reads decides what can wake it. `isMatchFound` is a
 * getter over three things and only two of them are snapshot state: the third,
 * `activeMatchDeferred`, is a plain field that can turn the flag true while
 * nothing observable moves. Read inside the `if`, the unmatched branch never
 * touches [radius], so a card whose match arrives by that door has nothing left to
 * wake it and stays at [standingStill]. Read first, the animated value is on the
 * block's read set for every card, and every card is therefore re-run on each
 * frame of a transition -- which is when the flag can change and the only time
 * this costs anything. It is one comparison per card per frame, and it buys the
 * property the paragraph above claims to have.
 */
private fun Modifier.roundedBy(
    radius: State<Dp>,
    standingStill: Dp,
    whileMatched: SharedContentState,
): Modifier = graphicsLayer {
    val travelling = radius.value
    clip = true
    shape = RoundedCornerShape(if (whileMatched.isMatchFound) travelling else standingStill)
}

/**
 * A rounded corner asked for at draw time instead of at composition time.
 *
 * `OverlayClip(shape)` calls [Shape.createOutline] from the overlay's draw, once per
 * frame with the bounds the element currently has. Reading the animated radius in
 * there rather than closing over a number is what makes one [OverlayClip] instance
 * enough for the whole flight.
 */
private class TravellingCorner(private val radius: State<Dp>) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = RoundedCornerShape(radius.value).createOutline(size, layoutDirection, density)
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
 * A `@Preview` and a test have no transition to take part in, so the modifiers
 * above add nothing but the corners the end they were given asks for, rather than
 * throwing. A screen that could only be drawn while it was moving would be a
 * screen nobody could look at.
 */
private val LocalArticleMotion = compositionLocalOf<ArticleMotion?> { null }

/** Which part of an article the two screens are holding up against each other. */
private enum class ArticlePart { CARD, IMAGE, TITLE, ATTRIBUTION }

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
