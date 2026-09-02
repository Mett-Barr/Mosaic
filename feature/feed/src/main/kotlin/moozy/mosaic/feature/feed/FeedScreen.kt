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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import moozy.mosaic.core.ui.MosaicTheme
import moozy.mosaic.core.ui.ArticleEnd
import moozy.mosaic.core.ui.CardShape
import moozy.mosaic.core.ui.sharedArticleCard
import moozy.mosaic.core.ui.sharedArticleImage
import moozy.mosaic.core.ui.sharedArticleTitle
import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.FeedFailure
import moozy.mosaic.domain.model.Sky

/**
 * The feed, holding on to a view model.
 *
 * The same name as the composable below rather than `FeedRoute`, because they are
 * the same screen: one of them knows where the state comes from and the other is
 * handed it. A caller picks by what it has, not by learning a second word.
 */
@Composable
fun FeedScreen(
    onOpenArticle: (ArticleId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val stories = viewModel.stories.collectAsLazyPagingItems()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    FeedScreen(
        stories = stories,
        weather = weather,
        onRefresh = viewModel::refresh,
        onOpenArticle = onOpenArticle,
        modifier = modifier,
    )
}

/**
 * Every state the feed can be in has somewhere to go here, and the compiler is the
 * one checking that: [FeedPhase] is sealed, so a state added later without a
 * branch to draw it will not build.
 *
 * The phase is worked out by [feedPhase] rather than held anywhere. Nothing in
 * this file decides which screen this is -- that decision has tests, and a
 * decision made inside a composable would not.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    stories: LazyPagingItems<ArticleRow>,
    weather: WeatherHeadline?,
    onRefresh: () -> Unit,
    onOpenArticle: (ArticleId) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The gesture belongs to the whole screen, not only the list: a reader who
    // pulls an "you are offline" screen down is asking the same question.
    PullToRefreshBox(
        // The pull rebuilds the generation, so Paging's own refresh state is
        // what the spinner follows -- there is no second flag saying the same
        // thing in this app's words.
        isRefreshing = stories.loadState.refresh is LoadState.Loading && stories.itemCount > 0,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        // Which screen this is, worked out by a function with tests rather than by
        // a `when` down here where nothing in this project could check it. Each
        // branch names what it draws, so the phase and the picture read alike.
        when (val phase = feedPhase(stories.loadState, stories.itemCount)) {
            FeedPhase.Loading -> LoadingState()

            FeedPhase.Empty -> EmptyState()

            is FeedPhase.Failed -> FailedState(phase = phase, onRetry = stories::retry)

            FeedPhase.Ready -> ArticleList(stories, weather, onOpenArticle)
        }
    }
}

/** Nothing to show yet, and a reason to wait. */
@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** The feed answered and there was genuinely nothing in it. */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            "No articles yet.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Nothing to show, and a reason for it the reader can act on.
 *
 * The icon follows [FeedPhase.Failed.offline] rather than the words: reading the
 * message back to decide which picture goes with it would be the same decision
 * made a second time, in the one place nothing here can check it.
 */
@Composable
private fun FailedState(
    phase: FeedPhase.Failed,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Notice(
            icon = if (phase.offline) Icons.Filled.CloudOff else Icons.Outlined.ErrorOutline,
            message = phase.message,
            hint = phase.hint,
            onRetry = onRetry,
        )
    }
}

@Composable
private fun ArticleList(
    stories: LazyPagingItems<ArticleRow>,
    weather: WeatherHeadline?,
    onOpenArticle: (ArticleId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Asking for the next page when the reader nears the end is Paging's job
    // now, and so is not asking again after one failed: an unguarded retry at
    // the bottom of a list is how an app spends somebody's data on a request
    // that has just failed, and the library declines by itself.

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        weather?.let { reading ->
            item(key = "weather") { WeatherCard(reading) }
        }

        if (stories.itemCount > 0) {
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
        items(stories.itemCount, key = stories.itemKey { it.id.value }) { index ->
            val article = stories[index] ?: return@items
            val open = { onOpenArticle(article.id) }
            if (index == 0) LeadStory(article, open) else StoryRow(article, open)
        }

        if (stories.loadState.append is LoadState.Loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        (stories.loadState.append as? LoadState.Error)?.let { failed ->
            item {
                Notice(
                    icon = Icons.Outlined.ErrorOutline,
                    message = "Could not load more.",
                    hint = failed.error.hint(),
                    onRetry = stories::retry,
                )
            }
        }

        if (stories.loadState.append.endOfPaginationReached) {
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

/**
 * The story at the top of the feed: picture first, then the words.
 *
 * The card is what the article screen grows out of, and the picture and the title
 * travel there rather than fading out here and back in there. What that costs this
 * file is three modifiers; who is animating them, and whether anyone is, is not
 * something this module is told.
 */
@Composable
private fun LeadStory(article: ArticleRow, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onOpen,
        // The bounds first and the width after it. The article screen sizes itself
        // after its own bounds as well, and matching items have to agree about
        // that: what comes before the shared modifier decides the rectangle that
        // travels, what comes after it measures the contents that sit inside.
        // The shared modifier is told which end this is, and rounds the card
        // itself. While the transition runs this rectangle is lifted into an
        // overlay and leaves the Surface behind, so corners declared only there
        // round nothing for the length of the flight.
        modifier = modifier.sharedArticleCard(article.id, ArticleEnd.IN_A_LIST).fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            article.imageUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(LEAD_IMAGE_RATIO)
                        .sharedArticleImage(article.id, ArticleEnd.IN_A_LIST),
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
                    modifier = Modifier.sharedArticleTitle(article.id),
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
        // The bounds first, then the width. The clip is gone from here: it is the
        // shared modifier's now, because the corners have to survive the overlay.
        modifier = modifier
            .sharedArticleCard(article.id, ArticleEnd.IN_A_LIST)
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        article.imageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(THUMBNAIL)
                    .sharedArticleImage(article.id, ArticleEnd.IN_A_LIST),
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
                modifier = Modifier.sharedArticleTitle(article.id),
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

private const val LEAD_IMAGE_RATIO = 16f / 9f
private val THUMBNAIL = 92.dp

/**
 * The stories the previews below are drawn from.
 *
 * No image URLs: Coil has nowhere to fetch from in a still render and this screen
 * sets no placeholder, so a URL would reserve a 16:9 hole and leave it empty --
 * which says less about the layout than the words do.
 */
private val PreviewStories = listOf(
    ArticleRow(
        id = ArticleId("preview-lead"),
        title = "A quiet redesign of the thing everyone already knew how to use",
        summary = "The team spent a year moving one button, and told nobody until it shipped.",
        attribution = "The Verge · 2 hours ago",
        imageUrl = null,
    ),
    ArticleRow(
        id = ArticleId("preview-second"),
        title = "Rail operators settle on one timetable format after nine years",
        summary = "",
        attribution = "Reuters · 5 hours ago",
        imageUrl = null,
    ),
    ArticleRow(
        id = ArticleId("preview-third"),
        title = "The observatory that keeps working because nobody funded its replacement",
        summary = "Forty years past its retirement date, it has outlived three successors.",
        attribution = "Nature · Yesterday",
        imageUrl = null,
    ),
)

private val PreviewWeather = WeatherHeadline(
    place = "Taipei",
    temperature = "28°",
    conditions = "Cloudy · 31° / 24°",
    sky = Sky.CLOUDY,
    // The card's own previews live beside it; this one is here so the list
    // preview shows the weather cell at the height it actually has.
    days = persistentListOf(
        DayHeadline(day = "Tue", temperature = "32°", sky = Sky.CLOUDY),
        DayHeadline(day = "Wed", temperature = "29°", sky = Sky.RAIN),
        DayHeadline(day = "Thu", temperature = "33°", sky = Sky.CLEAR),
    ),
)

/**
 * The stories as Paging would hand them over.
 *
 * [LazyPagingItems] cannot be constructed, so a preview of [ArticleList] goes the
 * long way round: a [PagingData] carrying the load states the list reads. The
 * append state says the feed has run out because, of the three things that can
 * sit at the bottom of the list -- a spinner, a failure, or the line that closes
 * it -- that is the only one nothing else in this file already draws.
 */
private val PreviewPaging = PagingData.from(
    data = PreviewStories,
    sourceLoadStates = LoadStates(
        refresh = LoadState.NotLoading(endOfPaginationReached = false),
        prepend = LoadState.NotLoading(endOfPaginationReached = true),
        append = LoadState.NotLoading(endOfPaginationReached = true),
    ),
)

/** Any server error will do; the screen says the number it arrived as. */
private const val SAMPLE_STATUS = 503

/** The two failures this screen draws differently: the one with its own icon, and the rest. */
private class FeedFailures : PreviewParameterProvider<FeedPhase.Failed> {

    // Backed by a list rather than built lazily: the tooling walks the sequence
    // once to count it and again to render, and a sequence computed on the fly
    // is empty the second time.
    private val phases = listOf(
        "Offline" to refused(FeedFailure.Offline()),
        "Anything else" to refused(FeedFailure.Server(status = SAMPLE_STATUS)),
    )

    override val values = phases.map { (_, phase) -> phase }.asSequence()

    override fun getDisplayName(index: Int) = phases[index].first

    /**
     * A failure with the words the app would actually give it.
     *
     * Run through [feedPhase] rather than written out here, so what a preview
     * shows is what a reader would see -- and follows it when it changes.
     */
    private fun refused(reason: FeedFailure): FeedPhase.Failed {
        val error = LoadState.Error(FeedRefused(reason))
        val idle = LoadState.NotLoading(endOfPaginationReached = false)
        val source = LoadStates(refresh = error, prepend = idle, append = idle)
        val phase = feedPhase(
            load = CombinedLoadStates(error, idle, idle, source, mediator = null),
            itemCount = 0,
        )
        // Nothing on screen and a refresh that failed is the input feedPhase
        // answers Failed to. A cast that stopped holding would mean that
        // function changed and this fixture has to change with it.
        return phase as FeedPhase.Failed
    }
}

/** One arc in the primary colour. There is nothing here a second theme would show. */
@Preview
@Composable
private fun LoadingStatePreview() {
    MosaicTheme {
        Surface(color = MaterialTheme.colorScheme.background) { LoadingState() }
    }
}

/**
 * One theme, because this is one line of `onSurfaceVariant` on `background` and
 * [ArticleListPreviews] already holds that pairing up to both schemes.
 */
@Preview
@Composable
private fun EmptyStatePreview() {
    MosaicTheme {
        Surface(color = MaterialTheme.colorScheme.background) { EmptyState() }
    }
}

/** Both failures, so the icon and the words can be read against each other. */
@Preview
@Composable
private fun FailedStatePreview(
    @PreviewParameter(FeedFailures::class) phase: FeedPhase.Failed,
) {
    MosaicTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            FailedState(phase = phase, onRetry = {})
        }
    }
}

/**
 * The list in both themes, weather card included.
 *
 * The one preview in this file worth two renders, because this is where the
 * palette is: the card's green gradient, the raised story surfaces, and body text
 * on a page that is not white in either scheme.
 */
@PreviewLightDark
@Composable
private fun ArticleListPreviews() {
    // A MutableStateFlow rather than flowOf: LazyPagingItems seeds itself from a
    // shared flow's replay cache, so the stories are there in the first
    // composition instead of after a coroutine a still render may never reach.
    val stories = remember { MutableStateFlow(PreviewPaging) }
    MosaicTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ArticleList(
                stories = stories.collectAsLazyPagingItems(),
                weather = PreviewWeather,
                onOpenArticle = {},
            )
        }
    }
}
