package moozy.mosaic.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import moozy.mosaic.core.ui.ArticleEnd
import moozy.mosaic.core.ui.MosaicTheme
import moozy.mosaic.core.ui.sharedArticleCard
import moozy.mosaic.core.ui.sharedArticleImage
import moozy.mosaic.core.ui.sharedArticleTitle
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
        id = id,
        state = state,
        onRetry = viewModel::retry,
        onBack = onBack,
        onKeep = viewModel::keep,
        onLetGo = viewModel::letGo,
        modifier = modifier,
    )
}

/**
 * [id] is not shown. It is what the card the reader tapped is matched against, and
 * it has to be here rather than on [ArticleView] because this screen has to claim
 * those bounds before it knows what is in them -- the article is still being
 * fetched when the transition starts.
 *
 * Nothing is above this screen and no inset has been subtracted from it: the
 * frame it sits in hands it every edge (DECISIONS.md 34). So the way back is
 * drawn here, over whichever of the three states is underneath -- and here rather
 * than in that frame because whether the arrow needs protecting from a photograph
 * is a question only this screen can answer.
 */
@Composable
fun DetailScreen(
    id: ArticleId,
    state: DetailUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onKeep: () -> Unit,
    onLetGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The bounds the tapped card grows into, on the screen rather than inside any
    // one of its three states: a reader who opens an article over a slow
    // connection watches the card become a spinner and then an article, which is
    // one movement, not two.
    val container = modifier.sharedArticleCard(id, ArticleEnd.FILLING_THE_DISPLAY)
    // Two of the three states have nothing at all under the status bar, and the
    // third only has a photograph when the article came with one -- `imageUrl` is
    // nullable, and plenty of them arrive without it.
    val overPicture = state is DetailUiState.Content && state.article.imageUrl != null
    Box(container.fillMaxSize()) {
        // One branch per state, each naming what it draws: [DetailUiState] is
        // sealed, so a state added later without a picture to go with it will
        // not build.
        when (state) {
            DetailUiState.Loading -> LoadingState()

            is DetailUiState.Failed -> FailedState(state, onRetry, onBack)

            is DetailUiState.Content -> Article(id, state, onBack, onKeep, onLetGo)
        }
        WayBack(onBack = onBack, overPicture = overPicture)
    }
}

/**
 * The way out, floating over whatever the state below it drew.
 *
 * A photograph makes no promise about its own brightness, so there is no arrow
 * colour that is legible over every article. That is why the documented answer to
 * system bars over an image is a scrim and not a choice of icon colour, and the
 * arrow needs one for the same reason the icons above it do.
 *
 * **One gradient rather than two.** The status bar's icons and this arrow sit in
 * the same column of pixels, one directly under the other; two scrims stacked
 * there would add their alphas in the overlap and leave a visible step where the
 * shorter one ended. So there is a single gradient, starting above the status bar
 * and reaching transparent below the arrow.
 *
 * It is drawn from `colorScheme.scrim` and not `surfaceContainer`. Material shapes
 * this gradient out of a surface colour, but that is for protecting icons from a
 * *known* background; over a photograph the light scheme's surface would lay a
 * white veil on the picture and leave white icons exactly as illegible as they
 * were. `scrim` is the one slot that is black in both schemes, which is what a
 * photograph needs from either of them.
 *
 * [overPicture] is false in the two states that have no picture and in an article
 * that arrived without one. There the colour behind the arrow is the app's own and
 * known, a dark veil over it would be protecting the arrow from nothing, and it
 * gets `onSurface` and no scrim -- which is exactly what the bar that used to be
 * here gave it.
 */
@Composable
private fun WayBack(
    onBack: () -> Unit,
    overPicture: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        if (overPicture) {
            val scrim = MaterialTheme.colorScheme.scrim
            Spacer(
                // Nothing clickable in it, so the article still scrolls under the
                // reader's finger everywhere the arrow itself is not.
                Modifier
                    .fillMaxWidth()
                    .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + Reach)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                scrim.copy(alpha = 0.55f),
                                scrim.copy(alpha = 0.35f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }
        IconButton(
            onClick = onBack,
            // The inset and not a number. A cutout, a tall status bar and a phone
            // with neither are three different distances, and only one of them is
            // the distance on the machine this was written on.
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to the feed",
                // White because what is behind it is black, and the black is the
                // scrim rather than the theme. Neither end of that pair is the
                // colour scheme's to choose while a photograph is underneath.
                tint = if (overPicture) Color.White else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** The article has been asked for and has not arrived. */
@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    // The system bars are stepped around rather than drawn under: there is no
    // picture here worth the edge, and there is no longer a bar above subtracting
    // them on this screen's behalf.
    Box(
        modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
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
    Box(
        modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
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
    id: ArticleId,
    state: DetailUiState.Content,
    onBack: () -> Unit,
    onKeep: () -> Unit,
    onLetGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val article = state.article
    val picture = article.imageUrl
    val uriHandler = LocalUriHandler.current
    // Nothing is padded out here. Everything this screen owes the system bars is
    // owed by the words below the picture, and it is paid inside the scroll: the
    // picture can then start at the very top, and the clearance at the bottom
    // scrolls away with the last button instead of standing there as a margin.
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (picture != null) {
            AsyncImage(
                model = picture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // This end of the flight fills the display, so the picture ends
                // it with no corners at all: it is flush to the top and to both
                // sides, and there is nothing left behind a rounded corner for
                // it to be rounded against.
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(IMAGE_RATIO)
                    .sharedArticleImage(id, ArticleEnd.FILLING_THE_DISPLAY),
            )
        } else {
            // No picture, and the arrow floating above is there all the same. This
            // is the room it needs, handed back to the article the moment the
            // reader scrolls.
            Spacer(
                Modifier.height(
                    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + Reach,
                ),
            )
        }
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                article.attribution,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                article.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.sharedArticleTitle(id),
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
            Button(
                onClick = { uriHandler.openUri(article.url) },
                modifier = Modifier.fillMaxWidth(),
            ) {
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
                Text(
                    if (state.saved) {
                        "Saved for offline — tap to remove"
                    } else {
                        "Save to read offline"
                    },
                )
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back to the feed")
            }
        }
    }
}

private const val IMAGE_RATIO = 16f / 9f

/**
 * How far past the status bar the arrow's business reaches.
 *
 * Its 48dp touch target, and enough again for the gradient to arrive at
 * transparent *below* the arrow rather than level with it -- a fade that runs out
 * at the glyph leaves the lower half of the arrow with nothing behind it. The same
 * distance is what an article without a picture keeps clear at the top, so that
 * the arrow lands in the same place whether or not there is one.
 */
private val Reach = 80.dp

private val PreviewArticleId = ArticleId("preview-observatory")

/**
 * The article the previews below are drawn from, and it has no picture.
 *
 * That is a case rather than an omission: `ArticleView.imageUrl` is nullable, and
 * an article arriving without one still has to leave the arrow somewhere legible.
 * [PreviewArticleWithPicture] is the other half of the pair.
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

/** The same article with something under the status bar. Only [WithAPicture] can draw it. */
private val PreviewArticleWithPicture = PreviewArticle.copy(
    imageUrl = "https://example.org/observatory.jpg",
)

/**
 * A stand-in photograph, so that a still render can show what floats on top of one.
 *
 * Coil has nowhere to fetch from in a preview, and until now these fixtures
 * carried no URL for exactly that reason -- a URL reserved a 16:9 hole and left it
 * empty. An empty hole is the one thing that cannot be looked at here: the scrim
 * and the arrow exist because of the picture, and over nothing they say nothing.
 * A flat colour is enough. What is being judged is the gradient's alpha, not the
 * photograph.
 */
@OptIn(ExperimentalCoilApi::class)
@Composable
private fun WithAPicture(content: @Composable () -> Unit) {
    val standIn = Color(0xFF6B7A88).toArgb()
    val handler = remember(standIn) { AsyncImagePreviewHandler { ColorImage(standIn) } }
    CompositionLocalProvider(LocalAsyncImagePreviewHandler provides handler, content = content)
}

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

/**
 * One arc in the primary colour, and the arrow that has to be reachable beside it.
 *
 * Every preview below draws the whole screen rather than a state on its own. The
 * arrow belongs to [DetailScreen] and not to any one state, and it is precisely
 * the part that changed -- a preview of a state alone would be a preview of the
 * half that did not.
 */
@Preview
@Composable
private fun LoadingStatePreview() {
    MosaicTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DetailScreen(
                id = PreviewArticleId,
                state = DetailUiState.Loading,
                onRetry = {},
                onBack = {},
                onKeep = {},
                onLetGo = {},
            )
        }
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
            DetailScreen(
                id = PreviewArticleId,
                state = state,
                onRetry = {},
                onBack = {},
                onKeep = {},
                onLetGo = {},
            )
        }
    }
}

/**
 * The article as a reader first meets it: not kept, and with no picture.
 *
 * One theme, because [ArticlePreviews] takes the same screen through both. What
 * this one is for is the pair only this state shows -- "Save to read offline" and
 * the outlined mark beside it -- and, above them, the case the immersive layout
 * has to survive anyway: no photograph, so the arrow stands on the page's own
 * colour with no scrim, and the words begin below it rather than under it.
 */
@Preview
@Composable
private fun ArticlePreview() {
    MosaicTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DetailScreen(
                id = PreviewArticleId,
                state = DetailUiState.Content(PreviewArticle),
                onRetry = {},
                onBack = {},
                onKeep = {},
                onLetGo = {},
            )
        }
    }
}

/**
 * The article kept, with its picture, in both themes.
 *
 * Both themes because this is where the scrim has to hold. It is one dark gradient
 * in either scheme, which is the opposite of what every other colour on this
 * screen does, and the light render is the one that would give it away if a
 * surface colour had been used instead. Kept rather than not for the reason it was
 * always kept here: a filled tonal button and a filled bookmark are the two slots
 * the dark scheme moves furthest from the light one.
 */
@PreviewLightDark
@Composable
private fun ArticlePreviews() {
    MosaicTheme {
        WithAPicture {
            Surface(color = MaterialTheme.colorScheme.background) {
                DetailScreen(
                    id = PreviewArticleId,
                    state = DetailUiState.Content(PreviewArticleWithPicture, saved = true),
                    onRetry = {},
                    onBack = {},
                    onKeep = {},
                    onLetGo = {},
                )
            }
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
            DetailScreen(
                id = PreviewArticleId,
                state = DetailUiState.Content(PreviewArticle, saved = true),
                onRetry = {},
                onBack = {},
                onKeep = {},
                onLetGo = {},
            )
        }
    }
}
