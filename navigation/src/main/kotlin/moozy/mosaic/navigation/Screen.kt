package moozy.mosaic.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable

/**
 * The frame the two destinations sit in.
 *
 * A bar at the top that names where the reader is, and nothing else. The bar at
 * the bottom used to be here too, and it was the wrong place for it: everything
 * in this frame is inside a `NavEntry`, so `NavDisplay` moved the switch between
 * the two places along with the place being switched away from. It now stands
 * outside `NavDisplay`, in [Mosaic] (DECISIONS.md 42).
 *
 * The article no longer sits in it, and the back arrow that used to live in the
 * bar above went with it. It had no title to sit beside -- the article is already
 * on the screen -- and once the picture is allowed to reach the top edge there is
 * nothing left for a bar to hold. See DECISIONS.md 34.
 *
 * It has no frame of its own here either, not even an empty one. The frame that
 * used to stand in for it held one thing, an opaque layer, and that layer has
 * moved inside the rectangle the card grows into -- where it travels with it
 * instead of standing still behind it (DECISIONS.md 38).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Screen(
    title: String,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // The sides, and only the sides. Every inset is paid once and this frame
        // owns one of the three: the top is the app bar's, which pads itself for
        // the status bar, and the bottom belongs to whatever scrolls in here --
        // the destination bar's height is measured a level up and handed to the
        // screen as a number, not subtracted from this frame, so that a list can
        // reach the display's edge and scroll behind the bar (DECISIONS.md 45).
        // Left at its default, this Scaffold would pay the navigation bar a second
        // time on top of that number, which is the gap double payment looks like.
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        content = content,
    )
}
