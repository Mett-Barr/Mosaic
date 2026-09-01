package moozy.mosaic.core.ui

import java.time.Instant
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
