package moozy.mosaic.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem
import moozy.mosaic.domain.model.FeedFailure

@Composable
fun DetailRoute(
    id: ArticleId,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(id) { viewModel.open(id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    DetailScreen(state = state, onRetry = viewModel::retry, onBack = onBack, modifier = modifier)
}

@Composable
fun DetailScreen(
    state: DetailUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        DetailUiState.Loading -> Centred(modifier) { CircularProgressIndicator() }

        is DetailUiState.Failed -> Centred(modifier) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(state.reason.headline(), style = MaterialTheme.typography.titleMedium)
                Text(state.reason.hint(), style = MaterialTheme.typography.bodySmall)
                if (state.reason.worthTryingAgain()) {
                    Button(onClick = onRetry) { Text("Try again") }
                }
                OutlinedButton(onClick = onBack) { Text("Back to the feed") }
            }
        }

        is DetailUiState.Content -> Article(state.article, onBack, modifier)
    }
}

@Composable
private fun Article(article: ArticleItem, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        article.imageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(article.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${article.source} · ${readableTime.format(article.publishedAt)}",
                style = MaterialTheme.typography.labelMedium,
            )
            if (article.summary.isNotBlank()) {
                Text(article.summary, style = MaterialTheme.typography.bodyLarge)
            }
            // The API carries a summary, not the article. Reading the whole thing
            // means leaving, and saying so is better than a truncated page that
            // looks like the article and is not.
            Button(onClick = { uriHandler.openUri(article.url) }) {
                Text("Read the full article at ${article.source}")
            }
            OutlinedButton(onClick = onBack) { Text("Back to the feed") }
        }
    }
}

@Composable
private fun Centred(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

private fun FeedFailure.headline(): String = when (this) {
    is FeedFailure.Server -> if (status == NOT_FOUND) "This article is gone." else "Something went wrong."
    is FeedFailure.Offline -> "You appear to be offline."
    else -> "Something went wrong."
}

private fun FeedFailure.hint(): String = when (this) {
    is FeedFailure.Offline -> "The article will open when the connection is back."
    is FeedFailure.Timeout -> "The article took too long to arrive."
    is FeedFailure.Server ->
        if (status == NOT_FOUND) "It is no longer where the feed said it was." else "Error $status."
    is FeedFailure.Unreadable -> "What arrived was not something this app can read."
    is FeedFailure.Unexpected -> "Something unexpected happened."
}

/** A 404 will be a 404 next time too; everything else might not be. */
private fun FeedFailure.worthTryingAgain(): Boolean =
    !(this is FeedFailure.Server && status == NOT_FOUND)

private val readableTime: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault())

private const val NOT_FOUND = 404
