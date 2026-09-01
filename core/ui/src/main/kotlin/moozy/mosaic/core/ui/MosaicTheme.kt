package moozy.mosaic.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The one place that decides what the app looks like.
 *
 * It is applied once, at the composition root, which is why nothing below it has
 * to take a theme: `MaterialTheme.colorScheme` and `MaterialTheme.typography` read
 * what is set here. That is also why a screen can be styled without its module
 * gaining an edge to this one -- Compose carries the theme down, Gradle does not.
 *
 * Every colour the design uses is a slot of the Material scheme rather than a new
 * channel of its own, for that reason. `:feature:saved` is allowed to depend on
 * `:core:domain` and nothing else; a palette shipped as a CompositionLocal here
 * would have been a palette that screen could not read.
 *
 * Deliberately not dynamic colour. A feed of photographs from other people's
 * websites already brings its own palette; letting the wallpaper decide the rest
 * makes the cards harder to tell apart, and the weather card is meant to be
 * obviously not an article.
 */
@Composable
fun MosaicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) MosaicDarkColors else MosaicLightColors,
        typography = MosaicTypography,
        shapes = MosaicShapes,
        content = content,
    )
}

// The green the app is named after in the reader's eye: dark enough to carry a
// title or a label on the page, with the bright one kept for fills.
private val ForestGreen = Color(0xFF016E2A)
private val SignalGreen = Color(0xFF0CC756)
private val DeepGreen = Color(0xFF004C16)
private val MistGreen = Color(0xFFC3E8CE)

// Not white. The reference sits every card on a barely-there lavender, which is
// what lets a white-ish card read as raised without a shadow under it.
private val PaperLavender = Color(0xFFFDF8FE)
private val CardLavender = Color(0xFFF1ECF2)
private val SunkLavender = Color(0xFFE9E4E8)

private val MosaicLightColors = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    // The pill under the selected destination, and the top of the weather card.
    primaryContainer = SignalGreen,
    onPrimaryContainer = DeepGreen,
    secondary = ForestGreen,
    onSecondary = Color.White,
    // Where the weather card's gradient lands.
    secondaryContainer = MistGreen,
    onSecondaryContainer = DeepGreen,
    // The one colour in the design that is not green: a source, called out.
    tertiary = Color(0xFF104972),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF7DB2DE),
    onTertiaryContainer = Color(0xFF0E3E63),
    background = PaperLavender,
    onBackground = Color(0xFF1A181D),
    surface = PaperLavender,
    onSurface = Color(0xFF1A181D),
    surfaceVariant = CardLavender,
    onSurfaceVariant = Color(0xFF4A5155),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F2F8),
    surfaceContainer = CardLavender,
    surfaceContainerHigh = SunkLavender,
    surfaceContainerHighest = Color(0xFFE2DCE2),
    outline = Color(0xFF7A757E),
    outlineVariant = Color(0xFFDCD5DD),
    error = Color(0xFFA4232B),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD8),
    onErrorContainer = Color(0xFF410006),
)

private val MosaicDarkColors = darkColorScheme(
    primary = Color(0xFF3FD478),
    onPrimary = Color(0xFF00320D),
    primaryContainer = Color(0xFF0A7A31),
    onPrimaryContainer = Color(0xFFB8F3CC),
    secondary = Color(0xFF3FD478),
    onSecondary = Color(0xFF00320D),
    secondaryContainer = Color(0xFF0B4A22),
    onSecondaryContainer = Color(0xFFCBF2D8),
    tertiary = Color(0xFF8FC3EA),
    onTertiary = Color(0xFF0A344F),
    tertiaryContainer = Color(0xFF1E4A6B),
    onTertiaryContainer = Color(0xFFCDE5F8),
    background = Color(0xFF121014),
    onBackground = Color(0xFFE8E4EC),
    surface = Color(0xFF121014),
    onSurface = Color(0xFFE8E4EC),
    surfaceVariant = Color(0xFF1D1A20),
    onSurfaceVariant = Color(0xFFB7B2BC),
    surfaceContainerLowest = Color(0xFF0B090D),
    surfaceContainerLow = Color(0xFF17151A),
    surfaceContainer = Color(0xFF1D1A20),
    surfaceContainerHigh = Color(0xFF272329),
    surfaceContainerHighest = Color(0xFF322D34),
    outline = Color(0xFF8F8A93),
    outlineVariant = Color(0xFF3A363F),
    error = Color(0xFFFFB4AA),
    onError = Color(0xFF690007),
    errorContainer = Color(0xFF930010),
    onErrorContainer = Color(0xFFFFDAD8),
)

/**
 * Corners, as the design draws them.
 *
 * Larger than Material's defaults across the board, because the reference has no
 * shadows: a card is told apart from the page by its radius and its fill, and a
 * 12dp corner on a 340dp card does not read as a card at all.
 */
private val MosaicShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * The type scale, weighted the way the design weights it.
 *
 * Only the six styles the screens actually use are restated; the rest stay
 * Material's. Titles are bold rather than medium, and the temperature is given a
 * size no other text in the app comes near -- it is the one number a reader is
 * meant to take in without reading.
 */
private val MosaicTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(
            fontSize = 64.sp,
            lineHeight = 68.sp,
            fontWeight = FontWeight.Normal,
        ),
        titleLarge = base.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(
            fontSize = 17.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Bold,
        ),
        bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold),
        labelMedium = base.labelMedium.copy(
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        ),
    )
}
