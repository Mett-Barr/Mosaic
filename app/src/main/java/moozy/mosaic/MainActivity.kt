package moozy.mosaic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.feature.detail.DetailRoute
import moozy.mosaic.feature.feed.FeedRoute
import moozy.mosaic.feature.saved.SavedRoute

/**
 * The two places a reader can be. Navigation 3 restores a back stack by
 * serialising its keys, which is why they are @Serializable and why the article's
 * id crosses as a String rather than as the domain's own type: what is written to
 * a Bundle is a detail of getting back to a screen, not of the domain.
 */
@Serializable
private data object FeedKey : NavKey

@Serializable
private data class ArticleKey(val id: String) : NavKey

@Serializable
private data object SavedKey : NavKey

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Mosaic()
                }
            }
        }
    }
}

@Composable
private fun Mosaic() {
    val backStack = rememberNavBackStack(FeedKey)
    NavDisplay(
        backStack = backStack,
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
                    title = "Mosaic",
                    action = { TextButton(onClick = { backStack.add(SavedKey) }) { Text("Saved") } },
                ) { padding ->
                    FeedRoute(
                        onOpenArticle = { id -> backStack.add(ArticleKey(id.value)) },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
            entry<SavedKey> {
                Screen(title = "Saved", onBack = { backStack.removeLastOrNull() }) { padding ->
                    SavedRoute(
                        onOpenArticle = { id -> backStack.add(ArticleKey(id.value)) },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
            entry<ArticleKey> { key ->
                Screen(title = "", onBack = { backStack.removeLastOrNull() }) { padding ->
                    DetailRoute(
                        id = ArticleId(key.id),
                        onBack = { backStack.removeLastOrNull() },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        },
    )
}

/**
 * The frame every screen sits in.
 *
 * A bar with a way back at the top, because the reader expects it there and
 * because a button at the bottom of a long article is not a way back for anyone
 * who has not reached the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screen(
    title: String,
    onBack: (() -> Unit)? = null,
    action: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    // A word rather than an arrow: the arrow lives in a separate
                    // artifact, and one button does not justify shipping the set.
                    onBack?.let { TextButton(onClick = it) { Text("Back") } }
                },
                actions = { action() },
            )
        },
        content = content,
    )
}
