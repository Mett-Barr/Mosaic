package moozy.mosaic.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay

/**
 * Reading and Saved are siblings, so switching between them slides sideways.
 *
 * The back stack still holds one of them under the other, and that is deliberate:
 * Reading is the bottom of it so a reader who taps Reading and then presses back
 * cannot land on a Saved screen they never chose. What was not deliberate is what
 * the shape of the stack was doing to the picture. Going to Saved is an add and
 * going back to Reading is a remove, and Navigation 3 animates those two
 * differently -- so one switch had two directions that looked like two different
 * gestures, one advancing and one shrinking away.
 *
 * **The modelling error was in the animation, not in the stack.** Neither of these
 * two destinations contains the other; the bar at the bottom is a switch between
 * equals. So both directions are the same lateral slide, mirrored, and the stack
 * is left exactly as it was.
 *
 * This sits on `SavedKey` and on nothing else because [NavDisplay] reads the
 * transitions of whichever entry is on top -- the one being added when the reader
 * goes to Saved, and the one being removed when they come back.
 */
internal val LateralSwitch: Map<String, Any> = metadata {
    // Start and End rather than Left and Right, because the bar the reader tapped
    // is a Row: it puts Reading first and Saved second in the reading direction,
    // whichever direction that is, and the slide has to agree with it.
    put(NavDisplay.TransitionKey) { slideAcross(SlideDirection.Start) }
    put(NavDisplay.PopTransitionKey) { slideAcross(SlideDirection.End) }
    // The gesture has to say the same thing as the tap. Predictive back is opted
    // into in the manifest, and without this line a swipe back from Saved would
    // play NavDisplay's default pop while the bar plays a slide.
    put(NavDisplay.PredictivePopTransitionKey) { _ -> slideAcross(SlideDirection.End) }
}

/**
 * One screen leaving the way the other arrives.
 *
 * The fade is there so that neither screen has a hard edge crossing the other
 * while both are on screen -- the shared-axis pairing, on the axis these two
 * destinations sit on.
 */
private fun AnimatedContentTransitionScope<Scene<*>>.slideAcross(
    towards: SlideDirection,
): ContentTransform =
    (slideIntoContainer(towards) + fadeIn()) togetherWith (slideOutOfContainer(towards) + fadeOut())
