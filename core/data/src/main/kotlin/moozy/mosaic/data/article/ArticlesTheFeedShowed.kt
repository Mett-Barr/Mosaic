package moozy.mosaic.data.article

import moozy.mosaic.domain.model.ArticleId
import moozy.mosaic.domain.model.ArticleItem

/**
 * The articles the list on screen was drawn from, for as long as it is the list.
 *
 * A card in the feed is not a summary of an article the app has to go and find
 * again -- it *is* an article the app already has, drawn. Only the id crosses to
 * the screen that opens it, because a key is a thing that has to survive being
 * written to a Bundle, and that is the whole reason the second request existed.
 * This is where the rest of the article waits in the meantime.
 *
 * **What bounds it is the list, not a clock.** Nothing puts anything in here but
 * the repository handing a page to the feed, and asking for the top of the list
 * again -- which is one of exactly two things, an app starting or a reader
 * pulling (`DECISIONS.md` 25) -- empties it and starts over. So what is held is
 * what the current generation of the list is holding, and an article can only be
 * opened from a card that generation drew. It cannot answer for an article that
 * is no longer on screen, and it cannot be staler than the screen is.
 *
 * That is deliberately not a fourth freshness policy. The README argues that the
 * three this app has are the source's own rather than numbers somebody picked; a
 * TTL here would be the picked number that argument is against, and it would be
 * measuring the age of something the reader is looking at.
 *
 * **It is memory and only memory.** A process that was killed comes back with
 * nothing in here, which is exactly the case the loading state exists for, and
 * `DECISIONS.md` 25 already ruled that a cold start is worth a fresh request.
 *
 * It holds the same [ArticleItem] objects Paging is holding for the same list, so
 * what it costs over that is one map entry per article on screen.
 */
internal class ArticlesTheFeedShowed {

    private val shown = LinkedHashMap<ArticleId, ArticleItem>()

    /**
     * Remember a page the feed was just handed.
     *
     * [fromTheTop] is the refresh: the list is starting again, so this starts
     * again with it. Insertion order is kept so that what is dropped and what is
     * held stay the same thing the list dropped and held.
     */
    @Synchronized
    fun handedOut(articles: List<ArticleItem>, fromTheTop: Boolean) {
        if (fromTheTop) shown.clear()
        articles.forEach { shown[it.id] = it }
    }

    /**
     * The copy the feed showed, or nothing.
     *
     * Nothing is the ordinary answer, not a failure: a deep link, a cold start
     * and an article scrolled past two refreshes ago all arrive here and all mean
     * the same thing, which is that there is still a source left to ask.
     */
    @Synchronized
    fun find(id: ArticleId): ArticleItem? = shown[id]
}
