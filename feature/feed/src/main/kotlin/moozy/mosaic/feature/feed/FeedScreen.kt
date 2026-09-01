package moozy.mosaic.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import moozy.mosaic.domain.model.ArticleId

@Composable
fun FeedRoute(
    onOpenArticle: (ArticleId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FeedScreen(
        state = state,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onRefresh = viewModel::refresh,
        onOpenArticle = onOpenArticle,
        modifier = modifier,
    )
}

/**
 * Every state the feed can be in has somewhere to go here, and the compiler is the
 * one checking that: [FeedUiState] is sealed, so a state added later without a
 * branch to draw it will not build.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    state: FeedUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onOpenArticle: (ArticleId) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The gesture belongs to the whole screen, not only the list: a reader who
    // pulls an "you are offline" screen down is asking the same question.
    PullToRefreshBox(
        isRefreshing = (state as? FeedUiState.Content)?.refreshing == true,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        FeedContent(state, onRetry, onLoadMore, onOpenArticle)
    }
}

@Composable
private fun FeedContent(
    state: FeedUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenArticle: (ArticleId) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        FeedUiState.Loading -> Centred(modifier) { CircularProgressIndicator() }

        FeedUiState.Empty -> Centred(modifier) {
            Text("No articles yet.", style = MaterialTheme.typography.bodyLarge)
        }

        FeedUiState.Offline -> Centred(modifier) {
            Retryable(
                message = "You appear to be offline.",
                hint = "The feed will be here when the connection is.",
                onRetry = onRetry,
            )
        }

        is FeedUiState.Error -> Centred(modifier) {
            Retryable(message = state.message, hint = state.hint, onRetry = onRetry)
        }

        is FeedUiState.Content -> ArticleList(state, onLoadMore, onOpenArticle, modifier)
    }
}

@Composable
private fun ArticleList(
    state: FeedUiState.Content,
    onLoadMore: () -> Unit,
    onOpenArticle: (ArticleId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LoadMoreWhenNearTheEnd(
        listState = listState,
        // Not after a failure. The effect restarts when loadingMore goes back to
        // false, and the reader is still at the bottom, so an unguarded condition
        // asks again immediately -- and again -- which is how an app spends
        // somebody's mobile data on a request that just failed. After a failure
        // the next attempt is the reader's to make, with the button below.
        enabled = state.canLoadMore && !state.loadingMore && state.moreFailed == null,
        onLoadMore = onLoadMore,
    )

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.weather?.let { weather ->
            item(key = "weather") { WeatherCard(weather) }
        }

        items(state.articles, key = { it.id.value }) { article ->
            ArticleCard(article, onOpen = { onOpenArticle(article.id) })
        }

        if (state.loadingMore) {
            item { Centred(Modifier.fillMaxWidth()) { CircularProgressIndicator() } }
        }

        state.moreFailed?.let { failure ->
            item {
                Retryable(message = "Could not load more.", hint = failure, onRetry = onLoadMore)
            }
        }

        if (!state.canLoadMore && state.moreFailed == null) {
            item {
                Text(
                    "That is everything.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ArticleCard(article: ArticleRow, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        article.imageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = article.attribution,
                style = MaterialTheme.typography.labelMedium,
            )
            if (article.summary.isNotBlank()) {
                Text(
                    text = article.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Asks for the next page when the reader is close enough to the bottom that they
 * would otherwise be waiting for it.
 */
@Composable
private fun LoadMoreWhenNearTheEnd(
    listState: LazyListState,
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    val nearTheEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= info.totalItemsCount - ITEMS_BEFORE_THE_END
        }
    }
    LaunchedEffect(listState, enabled) {
        snapshotFlow { nearTheEnd }.collect { close -> if (close && enabled) onLoadMore() }
    }
}

@Composable
private fun Centred(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Retryable(message: String, hint: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium)
        Text(hint, style = MaterialTheme.typography.bodySmall)
        Button(onClick = onRetry) { Text("Try again") }
    }
}



private const val ITEMS_BEFORE_THE_END = 3
