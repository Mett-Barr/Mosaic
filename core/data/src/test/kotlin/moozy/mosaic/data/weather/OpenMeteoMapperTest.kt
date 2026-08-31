package moozy.mosaic.data.weather

import java.time.Instant
import moozy.mosaic.domain.model.Sky
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Weather arrives in a shape nothing else in this app shares: no id, no title, no
 * list -- one reading, in local time, with the sky described as a number from a
 * standards body. Turning that into something a feed can show is the whole job.
 *
 * The fixture is a real response captured from Open-Meteo on 2026-09-01.
 */
class OpenMeteoMapperTest {

    private fun payload(name: String): String =
        checkNotNull(javaClass.getResource(name)) { "missing fixture $name" }.readText()

    private fun map(json: String) = OpenMeteoJson
        .decodeFromString(ForecastDto.serializer(), json)
        .toWeather(place = "Taipei")

    @Test
    fun `a real forecast becomes a reading somebody can look at`() {
        val weather = map(payload("/open-meteo-taipei.json"))

        assertEquals("Taipei", weather.place)
        assertEquals(26, weather.temperature)
        assertEquals(32, weather.high)
        assertEquals(25, weather.low)
        assertEquals(Sky.CLOUDY, weather.sky)
    }

    @Test
    fun `the local time it was measured becomes an instant`() {
        // 02:30 in Taipei, which is UTC+8, is 18:30 the day before in UTC. A
        // reading is only comparable to a clock if it stops being local.
        val weather = map(payload("/open-meteo-taipei.json"))

        assertEquals(Instant.parse("2026-08-31T18:30:00Z"), weather.measuredAt)
    }

    @Test
    fun `the standards body's numbers become something with a name`() {
        assertEquals(Sky.CLEAR, skyFor(0))
        assertEquals(Sky.CLOUDY, skyFor(3))
        assertEquals(Sky.FOG, skyFor(45))
        assertEquals(Sky.DRIZZLE, skyFor(53))
        assertEquals(Sky.RAIN, skyFor(65))
        assertEquals(Sky.RAIN, skyFor(81))
        assertEquals(Sky.SNOW, skyFor(73))
        assertEquals(Sky.THUNDERSTORM, skyFor(95))
    }

    @Test
    fun `a code nobody has heard of is a sky nobody can name`() {
        assertEquals(Sky.UNKNOWN, skyFor(4242))
    }

    @Test
    fun `a forecast with no reading in it is not a forecast`() {
        assertThrows(Exception::class.java) {
            map("""{"utc_offset_seconds": 28800, "timezone": "Asia/Taipei"}""")
        }
    }

    private fun skyFor(code: Int) = map(
        """
        {"utc_offset_seconds": 28800, "timezone": "Asia/Taipei",
         "current": {"time": "2026-09-01T02:30", "temperature_2m": 25.6, "weather_code": $code},
         "daily": {"temperature_2m_max": [31.8], "temperature_2m_min": [25.4]}}
        """.trimIndent(),
    ).sky
}
