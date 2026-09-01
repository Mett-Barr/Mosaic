package moozy.mosaic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable
import moozy.mosaic.core.ui.MosaicTheme
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
            MosaicTheme {
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
                    title = "Today",
                    bar = {
                        DestinationBar(
                            current = Destination.READING,
                            onGo = { backStack.goToDestination(it) },
                        )
                    },
                ) { padding ->
                    FeedRoute(
                        onOpenArticle = { id -> backStack.goTo(ArticleKey(id.value)) },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
            entry<SavedKey> {
                Screen(
                    title = "Saved",
                    bar = {
                        DestinationBar(
                            current = Destination.SAVED,
                            onGo = { backStack.goToDestination(it) },
                        )
                    },
                ) { padding ->
                    SavedRoute(
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

/** The two places the bar at the bottom switches between. */
private enum class Destination(val label: String) {
    READING("Reading"),
    SAVED("Saved"),
}

/**
 * Go somewhere, unless the reader is already going there.
 *
 * Two taps land before the first screen has drawn, and both are handled. Without
 * this the reader arrives at the same article twice and has to press back twice
 * to leave it once -- the second press looking, from where they are, like it did
 * nothing at all.
 */
private fun MutableList<NavKey>.goTo(key: NavKey) {
    if (lastOrNull() != key) add(key)
}

/**
 * Reading is the bottom of the stack, so going back to it is going back.
 *
 * Deliberately not a second entry pushed on top: a reader who taps Reading and
 * then presses back would otherwise land on Saved, having never chosen it.
 */
private fun MutableList<NavKey>.goToDestination(destination: Destination) {
    when (destination) {
        Destination.READING -> while (size > 1) removeAt(size - 1)
        Destination.SAVED -> goTo(SavedKey)
    }
}

/**
 * The frame every screen sits in.
 *
 * A bar at the top that names where the reader is, and -- on the two places they
 * can choose between -- a bar at the bottom that switches. An article gets a way
 * back instead, because a button at the bottom of a long article is not a way
 * back for anyone who has not reached the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screen(
    title: String,
    onBack: (() -> Unit)? = null,
    bar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    onBack?.let {
                        IconButton(onClick = it) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to the feed",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = bar,
        content = content,
    )
}

/**
 * Reading and Saved, side by side, with a pill under the one the reader is on.
 *
 * Not [androidx.compose.material3.NavigationBar]: its indicator wraps the icon
 * and leaves the label outside, and in this design the pill is what the label
 * sits in.
 */
@Composable
private fun DestinationBar(
    current: Destination,
    onGo: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Destination.entries.forEach { destination ->
                DestinationButton(
                    destination = destination,
                    isCurrent = destination == current,
                    onGo = { onGo(destination) },
                )
            }
        }
    }
}

@Composable
private fun DestinationButton(
    destination: Destination,
    isCurrent: Boolean,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onGo,
        // Not disabled when selected: both destinations are idempotent -- going
        // to Reading from Reading pops nothing, going to Saved from Saved pushes
        // nothing -- and a greyed-out tab reads as broken rather than current.
        modifier = modifier.semantics { selected = isCurrent },
        shape = RoundedCornerShape(percent = 50),
        color = if (isCurrent) scheme.primaryContainer else Color.Transparent,
        contentColor = if (isCurrent) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
    ) {
        Column(
            Modifier.padding(horizontal = 28.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(imageVector = destination.icon(isCurrent), contentDescription = null)
            Text(destination.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Filled where the reader is, outlined where they are not. */
private fun Destination.icon(isCurrent: Boolean): ImageVector = when (this) {
    Destination.READING ->
        if (isCurrent) Icons.Filled.AutoStories else Icons.Outlined.AutoStories

    Destination.SAVED ->
        if (isCurrent) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder
}
