package moozy.mosaic.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Reading and Saved, side by side, with a pill under the one the reader is on.
 *
 * Not [androidx.compose.material3.NavigationBar]: its indicator wraps the icon
 * and leaves the label outside, and in this design the pill is what the label
 * sits in.
 */
@Composable
internal fun DestinationBar(
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
