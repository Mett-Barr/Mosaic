package moozy.mosaic.navigation

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.padding
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
 */
@Composable
fun Mosaic(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(FeedKey)
    // One layout over the whole graph: a shared element is matched inside a single
    // SharedTransitionScope, and a card in the feed and the article it opens are
    // never in the same entry.
    SharedTransitionLayout(modifier) {
        val moving = this
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
                        Screen(
                            title = "Today",
                            bar = {
                                DestinationBar(
                                    current = Destination.READING,
                                    onGo = { backStack.goToDestination(it) },
                                )
                            },
                        ) { padding ->
                            FeedScreen(
                                onOpenArticle = { id ->
                                    backStack.goTo(ArticleKey(id.value, CardOrigin.READING))
                                },
                                modifier = Modifier.padding(padding),
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
                        Screen(
                            title = "Saved",
                            bar = {
                                DestinationBar(
                                    current = Destination.SAVED,
                                    onGo = { backStack.goToDestination(it) },
                                )
                            },
                        ) { padding ->
                            SavedScreen(
                                onOpenArticle = { id ->
                                    backStack.goTo(ArticleKey(id.value, CardOrigin.SAVED))
                                },
                                modifier = Modifier.padding(padding),
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
                        // places, so there is nothing to switch to at the bottom;
                        // and at the top the picture is the first thing under the
                        // status bar, so the way out has to float on the picture
                        // rather than sit in a bar above it. The screen draws that
                        // arrow itself, because only it knows whether there is a
                        // photograph behind it (DECISIONS.md 34).
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
