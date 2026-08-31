package moozy.mosaic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
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
                FeedRoute(onOpenArticle = { id -> backStack.add(ArticleKey(id.value)) })
            }
            entry<ArticleKey> { key ->
                DetailRoute(id = ArticleId(key.id), onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}
