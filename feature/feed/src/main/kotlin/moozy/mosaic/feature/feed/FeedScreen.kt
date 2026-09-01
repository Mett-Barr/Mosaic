package moozy.mosaic.feature.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
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
            Text(
                "No articles yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FeedUiState.Offline -> Centred(modifier) {
            Notice(
                icon = Icons.Filled.CloudOff,
                message = "You appear to be offline.",
                hint = "The feed will be here when the connection is.",
                onRetry = onRetry,
            )
        }

        is FeedUiState.Error -> Centred(modifier) {
            Notice(
                icon = Icons.Outlined.ErrorOutline,
                message = state.message,
                hint = state.hint,
                onRetry = onRetry,
            )
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        state.weather?.let { weather ->
            item(key = "weather") { WeatherCard(weather) }
        }

        if (state.articles.isNotEmpty()) {
            item(key = "stories") {
                Text(
                    "Top Stories",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        // The first story gets the whole width and its picture above it; the rest
        // get a thumbnail beside them. That is the reference's own arrangement,
        // and it earns its keep: the top of a feed is where a reader decides
        // whether to keep going, and a wall of identical rows makes that harder.
        itemsIndexed(state.articles, key = { _, article -> article.id.value }) { index, article ->
            val open = { onOpenArticle(article.id) }
            if (index == 0) LeadStory(article, open) else StoryRow(article, open)
        }

        if (state.loadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        state.moreFailed?.let { failure ->
            item {
                Notice(
                    icon = Icons.Outlined.ErrorOutline,
                    message = "Could not load more.",
                    hint = failure,
                    onRetry = onLoadMore,
                )
            }
        }

        if (!state.canLoadMore && state.moreFailed == null) {
            item {
                Text(
                    "That is everything.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }
}

/** The story at the top of the feed: picture first, then the words. */
@Composable
private fun LeadStory(article: ArticleRow, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            article.imageUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(LEAD_IMAGE_RATIO),
                )
            }
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Attribution(article.attribution)
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (article.summary.isNotBlank()) {
                    Text(
                        text = article.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Every story after the first. No container of its own -- the picture and the
 * white space are enough to separate one from the next, and a card around each
 * of twenty rows turns a feed into a stack of boxes.
 */
@Composable
private fun StoryRow(article: ArticleRow, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onOpen)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        article.imageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(THUMBNAIL).clip(MaterialTheme.shapes.medium),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Attribution(article.attribution)
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                // Two, so a row stays the height of its thumbnail and the list
                // keeps the rhythm the design gets from every row being alike.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Where a story came from and when, in the one line the row model carries. */
@Composable
private fun Attribution(attribution: String, modifier: Modifier = Modifier) {
    Text(
        text = attribution,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
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

/**
 * Something went wrong, and what to do about it.
 *
 * The same shape whether it replaces the feed or sits at the bottom of one, so a
 * reader who has seen it once already knows what it is the second time.
 */
@Composable
private fun Notice(
    icon: ImageVector,
    message: String,
    hint: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Text(
                message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                Text("Try again")
            }
        }
    }
}

private const val ITEMS_BEFORE_THE_END = 3
private const val LEAD_IMAGE_RATIO = 16f / 9f
private val THUMBNAIL = 92.dp
