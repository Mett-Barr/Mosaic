package moozy.mosaic.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
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
 */
@Composable
fun Mosaic(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(FeedKey)
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
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
                        onOpenArticle = { id -> backStack.goTo(ArticleKey(id.value)) },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
            // Saved carries the transitions for both directions of the bar at the
            // bottom, because it is the entry on top in both of them.
            entry<SavedKey>(metadata = LateralSwitch) {
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
                        onOpenArticle = { id -> backStack.goTo(ArticleKey(id.value)) },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
            entry<ArticleKey> { key ->
                // No destination bar here: an article is not one of the two
                // places, it is something opened from one of them, and the way
                // out of it is the way back in.
                Screen(title = "", onBack = { backStack.removeLastOrNull() }) { padding ->
                    DetailScreen(
                        id = ArticleId(key.id),
                        onBack = { backStack.removeLastOrNull() },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        },
    )
}
