package moozy.mosaic.data.article.network

import java.time.Instant
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The payload is untrusted input and the domain model refuses to hold unusable
 * values, so somebody has to stand between them. That is this mapper: it decides
 * per row whether it can become an article, and a row that cannot is left out
 * rather than allowed to take the page down with it.
 *
 * The fixture is a real response captured from
 * https://api.spaceflightnewsapi.net/v4/articles/?limit=3 on 2026-08-31, so the
 * field names under test are the ones the API sends, not the ones we assumed.
 */
class SpaceflightNewsMapperTest {

    private fun payload(name: String): String =
        checkNotNull(javaClass.getResource(name)) { "missing fixture $name" }.readText()

    private fun map(json: String) = SpaceflightNewsJson
        .decodeFromString(ArticlePageDto.serializer(), json)
        .toArticles()

    @Test
    fun `a real page becomes articles with each field where the reader expects it`() {
        val mapped = map(payload("/spaceflight-news-page.json"))

        assertEquals(0, mapped.dropped)
        assertEquals(listOf("39742", "39741", "39739"), mapped.articles.map { it.id.value })
        assertEquals(listOf("NASA", "European Spaceflight", "ESA"), mapped.articles.map { it.source })

        val first = mapped.articles.first()
        assertEquals("Roman Commissioning", first.title)
        assertEquals(
            "https://science.nasa.gov/missions/roman-space-telescope/roman-commissioning/",
            first.url,
        )
        assertEquals(
            "https://assets.science.nasa.gov/dynamicimage/assets/science/missions/rst/" +
                "spacecraft-illustrations/RST_Beauty_S2_4K_60_ProRes.00422_print.jpg",
            first.imageUrl,
        )
        assertEquals(true, first.summary.startsWith("Where is Roman?"))
        assertEquals(Instant.parse("2026-08-31T12:16:53Z"), first.publishedAt)
    }

    @Test
    fun `a row the domain would refuse is dropped and the rest of the page survives`() {
        val mapped = map(
            """
            {"count": 2, "next": null, "results": [
              {"id": 1, "title": "   ", "url": "https://example.com/1",
               "news_site": "Nowhere", "published_at": "2026-08-31T09:00:00Z"},
              {"id": 2, "title": "The one that is fine", "url": "https://example.com/2",
               "news_site": "Somewhere", "published_at": "2026-08-31T10:00:00Z"}
            ]}
            """.trimIndent(),
        )

        assertEquals(1, mapped.dropped)
        assertEquals("The one that is fine", mapped.articles.single().title)
    }

    @Test
    fun `a row whose timestamp cannot be read is dropped rather than thrown`() {
        val mapped = map(
            """
            {"count": 2, "next": null, "results": [
              {"id": 1, "title": "Published whenever", "url": "https://example.com/1",
               "news_site": "Nowhere", "published_at": "last tuesday"},
              {"id": 2, "title": "The one that is fine", "url": "https://example.com/2",
               "news_site": "Somewhere", "published_at": "2026-08-31T10:00:00Z"}
            ]}
            """.trimIndent(),
        )

        assertEquals(1, mapped.dropped)
        assertEquals("2", mapped.articles.single().id.value)
    }

    @Test
    fun `a row whose shape is wrong is dropped without taking the page with it`() {
        val mapped = map(
            """
            {"count": 3, "next": null, "results": [
              {"id": "not a number", "title": "Wrong type", "url": "https://example.com/1",
               "news_site": "Nowhere", "published_at": "2026-08-31T09:00:00Z"},
              {"title": "No id at all", "url": "https://example.com/2",
               "news_site": "Nowhere", "published_at": "2026-08-31T09:30:00Z"},
              {"id": 3, "title": "The one that is fine", "url": "https://example.com/3",
               "news_site": "Somewhere", "published_at": "2026-08-31T10:00:00Z"}
            ]}
            """.trimIndent(),
        )

        assertEquals(2, mapped.dropped)
        assertEquals("3", mapped.articles.single().id.value)
    }

    @Test
    fun `a row missing a field the reader needs is dropped`() {
        val mapped = map(
            """
            {"count": 2, "next": null, "results": [
              {"id": 1, "title": "Nowhere to go", "news_site": "Nowhere",
               "published_at": "2026-08-31T09:00:00Z"},
              {"id": 2, "title": "The one that is fine", "url": "https://example.com/2",
               "news_site": "Somewhere", "published_at": "2026-08-31T10:00:00Z"}
            ]}
            """.trimIndent(),
        )

        assertEquals(1, mapped.dropped)
        assertEquals("2", mapped.articles.single().id.value)
    }

    @Test
    fun `an empty image url is no image, not an image at the empty address`() {
        val mapped = map(
            """
            {"count": 1, "next": null, "results": [
              {"id": 3, "title": "No picture", "url": "https://example.com/3",
               "news_site": "Somewhere", "image_url": "",
               "published_at": "2026-08-31T10:00:00Z"}
            ]}
            """.trimIndent(),
        )

        assertNull(mapped.articles.single().imageUrl)
    }

    @Test
    fun `a summary the response leaves out is empty rather than missing`() {
        val mapped = map(
            """
            {"results": [
              {"id": 4, "title": "Sparse but usable", "url": "https://example.com/4",
               "news_site": "Somewhere", "published_at": "2026-08-31T10:00:00Z"}
            ]}
            """.trimIndent(),
        )

        assertEquals("", mapped.articles.single().summary)
    }

    @Test
    fun `a time written with an offset is the same instant as its UTC spelling`() {
        val mapped = map(
            """
            {"results": [
              {"id": 5, "title": "Taipei time", "url": "https://example.com/5",
               "news_site": "Somewhere", "published_at": "2026-08-31T18:00:00+08:00"}
            ]}
            """.trimIndent(),
        )

        assertEquals(Instant.parse("2026-08-31T10:00:00Z"), mapped.articles.single().publishedAt)
    }

    @Test
    fun `a response with no results at all is a broken response, not an empty feed`() {
        assertThrows(SerializationException::class.java) {
            map("""{"count": 0, "next": null}""")
        }
    }
}
