package moozy.mosaic.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import moozy.mosaic.core.ui.MosaicTheme
import moozy.mosaic.domain.model.MovieId

/**
 * The third kind of cell: films, sideways.
 *
 * It scrolls across while everything around it scrolls down, and that is the
 * point of it rather than a flourish -- the weather card is told apart from a
 * story by its colour, and this is told apart from both by moving the other way.
 * A reader does not have to read a poster to know it is not an article.
 *
 * The posters are 2:3 and the stories' pictures are 16:9, which is the second
 * half of the same idea. Nothing in this feed of three sources shares a shape
 * with anything else in it.
 *
 * There is no "See All" here, unlike the reference. It would lead nowhere: this
 * app has one film screen and it is this strip. An affordance that opens nothing
 * is worse than an absent one (`DECISIONS.md` 40).
 */
@Composable
internal fun MovieStrip(movies: ImmutableList<MoviePoster>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Trending Movies",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Keyed by the film, so a day turning over while somebody is looking
            // replaces the posters rather than shuffling them.
            items(movies, key = { film -> film.id.value }) { film -> Poster(film) }
        }
    }
}

/**
 * One film: the picture, the score over it, the title under it.
 *
 * Not clickable. There is nothing on the other side of a tap -- no film screen
 * exists -- and a card that darkens under a finger and then does nothing is a
 * promise this app cannot keep.
 */
@Composable
private fun Poster(film: MoviePoster, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(POSTER_WIDTH),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(POSTER_RATIO)
                .clip(MaterialTheme.shapes.medium)
                // Painted whether or not there is a picture, so the strip keeps
                // its rhythm: a film with no poster on file is a tile with a
                // symbol in it, not a hole the row closes over.
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (film.posterUrl == null) {
                Icon(
                    imageVector = Icons.Outlined.Movie,
                    // The title is directly below and says which film this is.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).size(NO_POSTER_ICON),
                )
            } else {
                AsyncImage(
                    model = film.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Only when the source has one. A film released today is trending
            // before anybody has rated it, and a badge reading "0.0" would be a
            // review rather than an absence.
            film.rating?.let { score ->
                Score(
                    rating = score,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                )
            }
        }
        Text(
            text = film.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            // Two, so a long title does not make one card taller than its
            // neighbours and leave the row uneven.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * How well it was received, over the corner of the poster.
 *
 * On an opaque surface rather than a translucent one: the thing behind it is a
 * different photograph on every card, and a scrim that reads on one poster is
 * unreadable on the next.
 */
@Composable
private fun Score(rating: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                // The only thing here that says what the number is. Without it a
                // screen reader announces "8.1" beside a film title and leaves
                // the reader to guess what was counted -- the same case as the
                // weather strip's columns, and the same answer.
                contentDescription = "Rated",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(STAR),
            )
            Text(
                text = rating,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Narrow enough that a third poster is visibly cut off, which is what says the row scrolls. */
private val POSTER_WIDTH = 116.dp

/** The 2:3 a film poster has been for a century, and the shape TMDB crops to. */
private const val POSTER_RATIO = 2f / 3f

/** Big enough to read as a symbol at the centre of an empty tile. */
private val NO_POSTER_ICON = 28.dp

/** Beside a label, so smaller than anything else this file draws. */
private val STAR = 12.dp

/**
 * Three films that disagree with each other.
 *
 * No poster URLs: Coil has nowhere to fetch from in a still render, so what these
 * show is the tile a film with no poster on file gets -- which is the half of
 * this layout worth looking at without a device. The three differ in the two ways
 * the card can: a title that needs both its lines, and a film with no score.
 */
private val PreviewFilms = persistentListOf(
    MoviePoster(
        id = MovieId(1087192),
        title = "How to Train Your Dragon",
        rating = "8.1",
        posterUrl = null,
    ),
    MoviePoster(
        id = MovieId(1233413),
        title = "A Title Long Enough That It Has To Stop Somewhere",
        rating = "7.9",
        posterUrl = null,
    ),
    MoviePoster(
        id = MovieId(803796),
        title = "Out Today",
        rating = null,
        posterUrl = null,
    ),
)

/**
 * The strip in both themes.
 *
 * Two renders because the tile, the badge and the page are three greys that have
 * to stay apart, and the two schemes stack `surfaceContainerHigh` and
 * `surfaceContainerHighest` in opposite directions from `background`.
 */
@PreviewLightDark
@Composable
private fun MovieStripPreviews() {
    MosaicTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            MovieStrip(PreviewFilms, Modifier.padding(16.dp))
        }
    }
}

/**
 * One film, at the size it is actually drawn.
 *
 * Worth its own render because everything this file decides is decided at this
 * width: whether two lines of title fit, whether the badge crowds the corner,
 * whether the symbol reads at the centre of a tile 116dp across.
 */
@Preview
@Composable
private fun PosterPreview() {
    MosaicTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Poster(PreviewFilms.first(), Modifier.padding(16.dp))
        }
    }
}
