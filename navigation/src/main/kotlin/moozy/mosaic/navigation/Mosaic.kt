package moozy.mosaic.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import moozy.mosaic.core.ui.CardOrigin
import moozy.mosaic.core.ui.ProvideArticleMotion
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.feature.detail.DetailScreen
import moozy.mosaic.feature.feed.FeedScreen
import moozy.mosaic.feature.saved.SavedScreen

/**
 * Every screen, and which one leads to which.
 *
 * The only thing this module makes public, and the only thing :app calls. The
 * screens themselves take plain callbacks and know nothing about navigation --
 * the knowledge of who connects to whom is what this module is for.
 *
 * It is also the only module that knows how they move. A card growing into an
 * article needs both ends of the transition in scope, and the two ends are two
 * entries; the scopes that make that possible are read here and handed down
 * through :core:ui, so no screen has to name the library moving it
 * (DECISIONS.md 32).
 *
 * And it is where the bar at the bottom lives, outside [NavDisplay] rather than
 * inside each entry. The bar is the fixed thing the reader navigates *with*; an
 * entry is one of the things they navigate *between*, and a bar inside one is
 * something `NavDisplay` transitions. See DECISIONS.md 42.
 */
@Composable
fun Mosaic(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(FeedKey)
    // One layout over the whole graph: a shared element is matched inside a single
    // SharedTransitionScope, and a card in the feed and the article it opens are
    // never in the same entry. Outside the Scaffold as well as outside NavDisplay,
    // so the overlay a shared element flies in is the whole display -- the article
    // finishes over where the bar is, and a card on its way there has to be able
    // to cross it.
    SharedTransitionLayout(modifier) {
        val moving = this
        Scaffold(
            // Zero, so that what this Scaffold measures below is the bar's height
            // and nothing else -- and zero when there is no bar, which is the case
            // the article needs (DECISIONS.md 34). Left at its default, `systemBars`
            // would fold the navigation bar's inset into that number a second time;
            // the bar already contains it, because the bar pads itself for it.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                // Whether the bar belongs on screen is a question about the back
                // stack rather than a flag handed down: an article is opened on top
                // of one of the two places rather than instead of it, so the top of
                // the stack says whether there is a bar and what is under it says
                // which of the two is lit.
                //
                // **It slides rather than vanishing.** A bar that disappeared on the
                // frame the article was tapped would blink out while the card it
                // grew from was still card-sized, which is the loudest thing on
                // screen during a transition meant to be about one rectangle. A
                // slide also keeps the bar's height reserved for the whole of its
                // exit, so the list underneath does not reflow while the reader can
                // still see it: the layout only changes when the bar is gone, and by
                // then the article's own opaque rectangle is the whole display.
                AnimatedVisibility(
                    visible = backStack.showsTheBar(),
                    enter = slideInVertically { height -> height },
                    exit = slideOutVertically { height -> height },
                ) {
                    DestinationBar(
                        current = backStack.destination(),
                        onGo = { backStack.goToDestination(it) },
                    )
                }
            },
        ) { padding ->
            // The bar's height, measured here and spent further down rather than
            // subtracted here. Subtracting it is what made an article open in two
            // movements: `NavDisplay` was then the display minus the bar, so the
            // rectangle the card grows into reached *that* edge, waited for the bar
            // to finish sliding, and only then grew the rest of the way. The
            // article's target has to be the whole display from the first frame, so
            // this padding is not applied to `NavDisplay` at all. Google's own
            // Navigation 3 recipe for this layout does the same thing with it --
            // `nav3-recipes`, `commonui/CommonUiActivity.kt` names the content
            // lambda's parameter `_`. See DECISIONS.md 45.
            val underTheBar = padding.calculateBottomPadding()
            NavDisplay(
                backStack = backStack,
                sharedTransitionScope = moving,
                onBack = { backStack.removeLastOrNull() },
                // Without these, every screen shares the activity's ViewModelStore: the
                // article screen would be one object for all articles, and going back to
                // the previous article would find the view model still holding the one
                // after it. A screen's state belongs to the entry that put it there.
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider {
                    entry<FeedKey> {
                        // LocalNavAnimatedContentScope is read here and in the two
                        // entries below, and nowhere else in the app. It is Navigation
                        // 3's type; a feature that imported it would know again that it
                        // is being navigated, which is the line DECISIONS 31 drew.
                        ProvideArticleMotion(
                            origin = CardOrigin.READING,
                            sharedTransitionScope = moving,
                            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                        ) {
                            // The frame pays the top and the sides as padding, because
                            // the bar above is opaque and the list has to start below
                            // it. The bottom is handed over as a number instead: down
                            // there the list has to reach the display's edge and let
                            // its last card scroll out from under the bar, which is
                            // `contentPadding`'s job and not a parent's.
                            Screen(title = "Today") { insets ->
                                FeedScreen(
                                    onOpenArticle = { id ->
                                        backStack.goTo(ArticleKey(id.value, CardOrigin.READING))
                                    },
                                    modifier = Modifier.padding(insets),
                                    bottomInset = underTheBar,
                                )
                            }
                        }
                    }
                    // Saved carries the transitions for both directions of the bar at the
                    // bottom, because it is the entry on top in both of them.
                    entry<SavedKey>(metadata = LateralSwitch) {
                        ProvideArticleMotion(
                            origin = CardOrigin.SAVED,
                            sharedTransitionScope = moving,
                            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                        ) {
                            Screen(title = "Saved") { insets ->
                                SavedScreen(
                                    onOpenArticle = { id ->
                                        backStack.goTo(ArticleKey(id.value, CardOrigin.SAVED))
                                    },
                                    modifier = Modifier.padding(insets),
                                    bottomInset = underTheBar,
                                )
                            }
                        }
                    }
                    entry<ArticleKey>(metadata = CardBecomesArticle) { key ->
                        // The origin comes off the key, so the article matches the card
                        // in the list the reader actually tapped -- not the copy of it
                        // sitting in the other list.
                        ProvideArticleMotion(
                            origin = key.from,
                            sharedTransitionScope = moving,
                            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                        ) {
                            // No bar at either end. An article is not one of the two
                            // places, so there is nothing to switch to at the bottom
                            // -- which the bar above reads off this key's presence
                            // rather than being told; and at the top the picture is
                            // the first thing under the status bar, so the way out
                            // has to float on the picture rather than sit in a bar
                            // above it. The screen draws that arrow itself, because
                            // only it knows whether there is a photograph behind it
                            // (DECISIONS.md 34).
                            //
                            // And no frame around it either. The one this entry used
                            // to wrap the article in was a full-screen opaque layer,
                            // and full-screen is exactly what the growing rectangle is
                            // not for most of the transition -- so the screen paints
                            // that layer inside its own bounds instead, where it
                            // shrinks back to the card with them (DECISIONS.md 38).
                            DetailScreen(
                                id = ArticleId(key.id),
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }
                    }
                },
            )
        }
    }
}
