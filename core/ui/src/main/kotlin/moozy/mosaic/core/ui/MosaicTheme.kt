package moozy.mosaic.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * The one place that decides what the app looks like.
 *
 * It is applied once, at the composition root, which is why nothing below it has
 * to take a theme: `MaterialTheme.colorScheme` and `MaterialTheme.typography` read
 * what is set here. That is also why the feature modules do not depend on this
 * one -- they read the theme through Compose, not through Gradle.
 *
 * Deliberately not dynamic colour. A feed of photographs from other people's
 * websites already brings its own palette; letting the wallpaper decide the rest
 * makes the cards harder to tell apart, and the weather card is meant to be
 * obviously not an article.
 */
@Composable
fun MosaicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
