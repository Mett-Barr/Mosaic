package moozy.mosaic.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import moozy.mosaic.core.ui.MosaicTheme
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.FeedFailure

/**
 * The article, holding on to a view model.
 *
 * The same name as the composable below rather than `DetailRoute`, because they
 * are the same screen: which overload a caller reaches is decided by whether it
 * already has the state, and that is the whole of the difference.
 */
@Composable
fun DetailScreen(
    id: ArticleId,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(id) { viewModel.open(id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    DetailScreen(
        state = state,
        onRetry = viewModel::retry,
        onBack = onBack,
        onKeep = viewModel::keep,
        onLetGo = viewModel::letGo,
        modifier = modifier,
    )
}

@Composable
fun DetailScreen(
    state: DetailUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onKeep: () -> Unit,
    onLetGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // One branch per state, each naming what it draws: [DetailUiState] is sealed,
    // so a state added later without a picture to go with it will not build.
    when (state) {
        DetailUiState.Loading -> LoadingState(modifier)

        is DetailUiState.Failed -> FailedState(state, onRetry, onBack, modifier)

        is DetailUiState.Content -> Article(state, onBack, onKeep, onLetGo, modifier)
    }
}

/** The article has been asked for and has not arrived. */
@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * The article could not be shown, and what there is left to do about it.
 *
 * Whether to offer another go is [DetailUiState.Failed.canRetry]'s to say, not
 * this composable's: the way back to the feed is underneath either way, and it
 * is the only thing on offer when trying again cannot work.
 */
@Composable
private fun FailedState(
    state: DetailUiState.Failed,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    state.message,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    state.hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (state.canRetry) {
                    Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Try again")
                    }
                }
                TextButton(onClick = onBack) { Text("Back to the feed") }
            }
        }
    }
}

@Composable
private fun Article(
    state: DetailUiState.Content,
    onBack: () -> Unit,
    onKeep: () -> Unit,
    onLetGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val article = state.article
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        article.imageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(IMAGE_RATIO)
                    .clip(MaterialTheme.shapes.large),
            )
        }
        Text(
            article.attribution,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            article.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (article.summary.isNotBlank()) {
            Text(
                article.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The API carries a summary, not the article. Reading the whole thing
        // means leaving, and saying so is better than a truncated page that
        // looks like the article and is not.
        Button(onClick = { uriHandler.openUri(article.url) }, modifier = Modifier.fillMaxWidth()) {
            Text(article.readFullLabel)
        }
        // Kept articles stay readable with no network, which is the whole
        // reason the button is here rather than a bookmark somewhere else.
        // Filled mark for kept, outlined for not: the same pair the Saved
        // screen and the bar at the bottom use.
        FilledTonalButton(
            onClick = if (state.saved) onLetGo else onKeep,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = if (state.saved) {
                    Icons.Filled.Bookmark
                } else {
                    Icons.Outlined.BookmarkBorder
                },
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(if (state.saved) "Saved for offline — tap to remove" else "Save to read offline")
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back to the feed")
        }
    }
}

private const val IMAGE_RATIO = 16f / 9f

/**
 * The article the previews below are drawn from.
 *
 * No image URL, for the reason the feed's fixtures carry none: Coil has nowhere
 * to fetch from in a still render and this screen sets no placeholder, so a URL
 * would reserve a 16:9 hole and leave it empty.
 */
private val PreviewArticle = ArticleView(
    title = "The observatory that keeps working because nobody funded its replacement",
    attribution = "Nature · Yesterday",
    summary = "Forty years past the date it was due to be switched off, the array is still " +
        "returning usable data -- and has now outlived three of the instruments built to " +
        "replace it.",
    imageUrl = null,
    url = "https://example.org/observatory",
    readFullLabel = "Read the full article at Nature",
)

/**
 * A failure with the words the app would actually give it.
 *
 * The message is [headline]'s to choose, the hint is [hint]'s, and whether there
 * is a button at all is [worthTryingAgain]'s. A fixture spelling any of the three
 * out here would go stale the first time one of them changed.
 */
private fun failed(reason: FeedFailure) = DetailUiState.Failed(
    message = reason.headline(),
    hint = reason.hint(),
    canRetry = reason.worthTryingAgain(),
)

/** The pair worth seeing together: one offers another go, the other deliberately does not. */
private class DetailFailures : PreviewParameterProvider<DetailUiState.Failed> {

    // Backed by a list rather than built lazily: the tooling walks the sequence
    // once to count it and again to render, and a sequence computed on the fly
    // is empty the second time.
    private val states = listOf(
        "Offline" to failed(FeedFailure.Offline()),
        "Gone" to failed(FeedFailure.Missing()),
    )

    override val values = states.map { (_, state) -> state }.asSequence()

    override fun getDisplayName(index: Int) = states[index].first
}

/** One arc in the primary colour. There is nothing here a second theme would show. */
@Preview
@Composable
private fun LoadingStatePreview() {
    MosaicTheme {
        Surface(color = MaterialTheme.colorScheme.background) { LoadingState() }
    }
}

/** Both failures, so the card that offers another go reads against the one that cannot. */
@Preview
@Composable
private fun FailedStatePreview(
    @PreviewParameter(DetailFailures::class) state: DetailUiState.Failed,
) {
    MosaicTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            FailedState(state = state, onRetry = {}, onBack = {})
        }
    }
}

/**
 * The article as a reader first meets it: not kept.
 *
 * One theme, because [ArticlePreviews] takes the same screen through both. What
 * this one is for is the pair only this state shows -- "Save to read offline"
 * and the outlined mark beside it.
 */
@Preview
@Composable
private fun ArticlePreview() {
    MosaicTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Article(
                state = DetailUiState.Content(PreviewArticle),
                onBack = {},
                onKeep = {},
                onLetGo = {},
            )
        }
    }
}

/**
 * The article kept, in both themes.
 *
 * Kept rather than not because that is the state with the colour in it: a filled
 * tonal button and a filled bookmark, which are the two slots the dark scheme
 * moves furthest from the light one.
 */
@PreviewLightDark
@Composable
private fun ArticlePreviews() {
    MosaicTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Article(
                state = DetailUiState.Content(PreviewArticle, saved = true),
                onBack = {},
                onKeep = {},
                onLetGo = {},
            )
        }
    }
}

/**
 * The one place in the app that carries `@PreviewFontScale`.
 *
 * Seven renders, which is why it is here and nowhere else: this is the only
 * screen whose buttons are labelled with sentences rather than words, and
 * "Saved for offline — tap to remove" is the label that stops fitting first when
 * somebody has their text at 200%.
 */
@PreviewFontScale
@Composable
private fun ArticleFontScalePreviews() {
    MosaicTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Article(
                state = DetailUiState.Content(PreviewArticle, saved = true),
                onBack = {},
                onKeep = {},
                onLetGo = {},
            )
        }
    }
}
