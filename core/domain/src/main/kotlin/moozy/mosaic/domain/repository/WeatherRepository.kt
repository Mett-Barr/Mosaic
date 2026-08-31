package moozy.mosaic.domain.repository

import moozy.mosaic.domain.model.WeatherResult

/**
 * What the feed needs in order to show the weather.
 *
 * There is no paging and no id: a place has one current reading, and the only
 * question is how old it is. That is why this is a second interface rather than a
 * second method on the article one -- the shapes have nothing in common, and a
 * repository that pretended otherwise would be an abstraction over a coincidence.
 */
interface WeatherRepository {

    suspend fun current(): WeatherResult
}
