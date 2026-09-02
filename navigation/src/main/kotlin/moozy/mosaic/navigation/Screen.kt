package moozy.mosaic.navigation

import androidx.compose.foundation.layout.PaddingValues
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
 * A bar at the top that names where the reader is, and a bar at the bottom that
 * switches between them. Both bars are there to say something: which of the two
 * places this is, and how to reach the other one.
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
