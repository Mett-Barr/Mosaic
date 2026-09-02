package moozy.mosaic.feature.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import moozy.mosaic.core.ui.MosaicTheme
import moozy.mosaic.domain.model.ArticleId

/**
 * The kept articles, holding on to a view model.
 *
 * The same name as the composable below rather than `SavedRoute`, for the reason
 * the other two screens carry one name each: a second word for the same screen is
 * something more to know and nothing more to understand.
 */
@Composable
fun SavedScreen(
    onOpenArticle: (ArticleId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SavedScreen(
        state = state,
        onOpenArticle = onOpenArticle,
        onLetGo = viewModel::letGo,
        modifier = modifier,
    )
}

@Composable
fun SavedScreen(
    state: SavedUiState,
    onOpenArticle: (ArticleId) -> Unit,
    onLetGo: (ArticleId) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Two branches because [SavedUiState] has two answers, and each one names
    // what it draws rather than describing it in place.
    when (state) {
        SavedUiState.Empty -> EmptyState(modifier)

        is SavedUiState.Content -> SavedList(state, onOpenArticle, onLetGo, modifier)
    }
}

/** Nothing kept yet, and what keeping something is for. */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Nothing saved yet. Articles you save stay readable without a connection.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Everything the reader kept, newest first, as the view model ordered it. */
@Composable
private fun SavedList(
    state: SavedUiState.Content,
    onOpenArticle: (ArticleId) -> Unit,
    onLetGo: (ArticleId) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Standing rather than conditional: this screen cannot tell whether
        // there is a connection, and what it says is true either way. The
        // design's "you're offline" wording is a claim about right now, and
        // making it would mean [SavedUiState] carrying a connection it does
        // not have -- so the banner says the part that is always true.
        item(key = "offline-note") { OfflineNote() }

        items(state.articles, key = { it.id.value }) { article ->
            SavedCard(
                article = article,
                onOpen = { onOpenArticle(article.id) },
                onLetGo = { onLetGo(article.id) },
            )
        }
    }
}

@Composable
private fun OfflineNote(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "These stay readable with no connection.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A kept article, drawn as the thing that keeps it.
 *
 * The filled bookmark is the remove control. It is not decoration next to a
 * button: on this screen every row is saved, so the only thing the mark can
 * usefully do when tapped is stop being true.
 */
@Composable
private fun SavedCard(
    article: SavedRow,
    onOpen: () -> Unit,
    onLetGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = article.source,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onLetGo) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = "Remove from saved",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Three kept articles, the last long enough to reach the three lines a card allows. */
private val PreviewKept = SavedUiState.Content(
    persistentListOf(
        SavedRow(
            id = ArticleId("preview-first"),
            title = "A quiet redesign of the thing everyone already knew how to use",
            source = "The Verge",
        ),
        SavedRow(
            id = ArticleId("preview-second"),
            title = "Rail operators settle on one timetable format after nine years",
            source = "Reuters",
        ),
        SavedRow(
            id = ArticleId("preview-third"),
            title = "The observatory that keeps working because nobody ever funded the " +
                "replacement that was supposed to have switched it off by now",
            source = "Nature",
        ),
    ),
)

/**
 * One theme: a centred paragraph of `onSurfaceVariant`, which is the pairing
 * [SavedListPreviews] already holds up to both schemes.
 */
@Preview
@Composable
private fun EmptyStatePreview() {
    MosaicTheme {
        Surface(color = MaterialTheme.colorScheme.background) { EmptyState() }
    }
}

/**
 * The kept list in both themes.
 *
 * Worth two renders because three fills sit on top of one another here -- the
 * note on `surfaceContainerHigh`, the cards on `surfaceContainer`, both on
 * `background` -- and a scheme that flattens them takes the cards' edges with it.
 *
 * The app's own theme, now that this module can reach it. Until this commit these
 * two previews drew Material's default palette, because `MosaicTheme` was on the
 * other side of a module edge that did not exist -- so what they checked was
 * layout and contrast, and the green was somebody else's to get wrong.
 */
@PreviewLightDark
@Composable
private fun SavedListPreviews() {
    MosaicTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SavedList(state = PreviewKept, onOpenArticle = {}, onLetGo = {})
        }
    }
}
