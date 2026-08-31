package moozy.mosaic.feature.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem

@Composable
fun SavedRoute(
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
    when (state) {
        SavedUiState.Empty -> Box(
            modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Nothing saved yet. Articles you save stay readable without a connection.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        is SavedUiState.Content -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.articles, key = { it.id.value }) { article ->
                SavedCard(
                    article = article,
                    onOpen = { onOpenArticle(article.id) },
                    onLetGo = { onLetGo(article.id) },
                )
            }
        }
    }
}

@Composable
private fun SavedCard(
    article: ArticleItem,
    onOpen: () -> Unit,
    onLetGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = article.source, style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = onLetGo) { Text("Remove") }
        }
    }
}
