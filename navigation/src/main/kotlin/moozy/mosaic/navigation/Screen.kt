package moozy.mosaic.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The frame the two destinations sit in.
 *
 * A bar at the top that names where the reader is, and a bar at the bottom that
 * switches between them. Both bars are there to say something: which of the two
 * places this is, and how to reach the other one.
 *
 * The article no longer sits in it, and the back arrow that used to live in the
 * bar above went with it. It had no title to sit beside -- the article is already
 * on the screen -- and once the picture is allowed to reach the top edge there is
 * nothing left for a bar to hold. See [EdgeToEdgeScreen] and DECISIONS.md 34.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Screen(
    title: String,
    bar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        bottomBar = bar,
        content = content,
    )
}

/**
 * The frame for a screen that would rather draw its own edges.
 *
 * No bars, and no insets handed down -- deliberately not a [Scaffold] with its
 * bars left empty, because a Scaffold's job is to measure chrome and subtract it
 * from the content, and there is no chrome here to subtract. What this does keep
 * is the one thing the article genuinely needs from a frame: an opaque layer of
 * its own. The container transform fades exactly this layer in over the list the
 * reader came from, and a transparent article would fade in over a list still
 * legible underneath it (DECISIONS.md 33).
 *
 * Because nothing is subtracted, nothing is consumed either: the content below
 * reads `WindowInsets` itself and decides, part by part, which bars to step
 * around and which to draw under. That is the whole reason for using this rather
 * than [Screen] -- the picture wants the status bar and the buttons do not.
 */
@Composable
internal fun EdgeToEdgeScreen(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        content = content,
    )
}
