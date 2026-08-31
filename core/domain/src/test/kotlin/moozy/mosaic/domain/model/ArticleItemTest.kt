package moozy.mosaic.domain.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * An article exists in the domain only once its fields are usable. The id keys the
 * list, the saved copy and the detail screen; the title is what a reader recognises
 * the article by. A response missing either one is the network layer's item to drop,
 * not something every screen downstream has to re-check.
 */
class ArticleItemTest {

    @Test
    fun `a well formed article keeps every field where it was put`() {
        val article = ArticleItem(
            id = ArticleId("39742"),
            title = "Roman Commissioning",
            summary = "Roman is making its three-month journey from Earth to L2.",
            source = "NASA",
            url = "https://science.nasa.gov/missions/roman-space-telescope/",
            imageUrl = "https://assets.science.nasa.gov/roman.jpg",
            publishedAt = Instant.parse("2026-08-31T12:16:53Z"),
        )

        assertEquals("39742", article.id.value)
        assertEquals("Roman Commissioning", article.title)
        assertEquals("NASA", article.source)
        assertEquals(Instant.parse("2026-08-31T12:16:53Z"), article.publishedAt)
    }

    @Test
    fun `an id made of whitespace is not an id`() {
        assertThrows(IllegalArgumentException::class.java) {
            ArticleId("   ")
        }
    }

    @Test
    fun `an article without a title cannot be built`() {
        val id = ArticleId("31337")

        assertThrows(IllegalArgumentException::class.java) {
            ArticleItem(
                id = id,
                title = "   ",
                summary = "A summary is allowed to be empty; a title is not.",
                source = "NASA",
                url = "https://www.nasa.gov/press-release/",
                imageUrl = null,
                publishedAt = Instant.parse("2026-08-31T09:00:00Z"),
            )
        }
    }
}
