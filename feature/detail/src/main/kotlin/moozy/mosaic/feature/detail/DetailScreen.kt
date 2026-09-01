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
import moozy.mosaic.domain.model.ArticleId

@Composable
fun DetailRoute(
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
    when (state) {
        DetailUiState.Loading -> Centred(modifier) { CircularProgressIndicator() }

        is DetailUiState.Failed -> Centred(modifier) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(state.message, style = MaterialTheme.typography.titleMedium)
                Text(state.hint, style = MaterialTheme.typography.bodySmall)
                if (state.canRetry) {
                    Button(onClick = onRetry) { Text("Try again") }
                }
                OutlinedButton(onClick = onBack) { Text("Back to the feed") }
            }
        }

        is DetailUiState.Content -> Article(state, onBack, onKeep, onLetGo, modifier)
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
                article.attribution,
                style = MaterialTheme.typography.labelMedium,
            )
            if (article.summary.isNotBlank()) {
                Text(article.summary, style = MaterialTheme.typography.bodyLarge)
            }
            // The API carries a summary, not the article. Reading the whole thing
            // means leaving, and saying so is better than a truncated page that
            // looks like the article and is not.
            Button(onClick = { uriHandler.openUri(article.url) }) {
                Text(article.readFullLabel)
            }
            // Kept articles stay readable with no network, which is the whole
            // reason the button is here rather than a bookmark somewhere else.
            OutlinedButton(onClick = if (state.saved) onLetGo else onKeep) {
                Text(if (state.saved) "Saved for offline — tap to remove" else "Save to read offline")
            }
            OutlinedButton(onClick = onBack) { Text("Back to the feed") }
        }
    }
}

@Composable
private fun Centred(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}


