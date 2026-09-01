package moozy.mosaic.domain.repository

import kotlinx.coroutines.flow.StateFlow
import moozy.mosaic.domain.model.Weather

/**
 * The weather where the reader is, as it changes.
 *
 * A stream rather than a question, because the weather changes whether or not
 * anybody asks. Asking made every caller responsible for deciding when, and a
 * caller that forgets leaves a card frozen at whatever it said when the app
 * started -- which is what happened.
 *
 * Nothing here says when to ask. That belongs to whatever knows how often the
 * source produces a new reading, and the source is the only thing that does.
 *
 * The value is a reading or nothing. There is no failure case: a card that
 * cannot be filled is better absent than apologising, because the reader opened
 * a feed of articles and this sits on top of it.
 */
interface WeatherRepository {

    val current: StateFlow<Weather?>
}
