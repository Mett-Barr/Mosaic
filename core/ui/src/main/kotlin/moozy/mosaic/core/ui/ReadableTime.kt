package moozy.mosaic.core.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * When something happened, as a person would read it: `31 Aug, 21:14`.
 *
 * In English, explicitly, because the sentence it sits inside is English --
 * "NASA · 31 Aug, 21:14". Leaving the locale to the device produced half a
 * sentence in each language on a Chinese phone. The fix for that is to
 * translate the app, not to translate one word of it.
 *
 * In the reader's timezone, because "21:14" is only useful if it is the clock
 * they would have looked at.
 */
fun readableTime(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    FORMAT.withZone(zone).format(at)

private val FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH)

/**
 * Which day of the week, as a person would read it: `Tue`.
 *
 * English for the reason above, and it is the same mistake one column further
 * along: a formatter with no locale reads the device's, and the weather card's
 * strip would come out in a language the rest of the card is not in.
 *
 * Three letters rather than the whole word because it is a heading over a
 * temperature, not a sentence -- and because "Wednesday" over "29°" would set
 * the width of every column in the strip.
 *
 * No zone parameter: a [java.time.LocalDate] is already a date somebody is
 * having, not a moment that has to be turned into one.
 */
fun readableWeekday(date: LocalDate): String = WEEKDAY.format(date)

private val WEEKDAY: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
