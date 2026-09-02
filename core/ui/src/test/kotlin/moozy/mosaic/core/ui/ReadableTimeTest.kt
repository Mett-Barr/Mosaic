package moozy.mosaic.core.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * A timestamp sits inside a sentence -- "NASA · 31 Aug, 21:14" -- and the rest
 * of that sentence is English, because every word this app shows is. Letting the
 * month follow the device's language instead produced "31 8月, 21:14" on a
 * Chinese phone: half of one language, half of another, chosen by nobody.
 */
class ReadableTimeTest {

    private lateinit var deviceLanguage: Locale

    @Before
    fun rememberTheDeviceLanguage() {
        deviceLanguage = Locale.getDefault()
    }

    @After
    fun putTheDeviceLanguageBack() {
        Locale.setDefault(deviceLanguage)
    }

    @Test
    fun `a time reads in the same language as the words around it`() {
        Locale.setDefault(Locale.TAIWAN)

        val time = readableTime(Instant.parse("2026-08-31T13:14:00Z"), ZoneId.of("Asia/Taipei"))

        assertEquals("31 Aug, 21:14", time)
    }

    @Test
    fun `a weekday reads in the same language as the words around it`() {
        // The same bug as the month above, one column further along: the strip
        // under the weather card is three weekday names over three temperatures,
        // and a formatter with no locale would write them in the phone's.
        Locale.setDefault(Locale.TAIWAN)

        assertEquals("Tue", readableWeekday(LocalDate.parse("2026-09-01")))
    }

    @Test
    fun `it says when the reader was there, not when the server was`() {
        val instant = Instant.parse("2026-08-31T13:14:00Z")

        assertEquals("31 Aug, 21:14", readableTime(instant, ZoneId.of("Asia/Taipei")))
        assertEquals("31 Aug, 14:14", readableTime(instant, ZoneId.of("Europe/London")))
    }
}
