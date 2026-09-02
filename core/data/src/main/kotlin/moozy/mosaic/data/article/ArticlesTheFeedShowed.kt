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
 * opened from a card that generation drew. It cannot be staler than the screen
 * is, because the screen and this were filled by the same pages.
 *
 * **The bound is that generation and not the viewport, and the difference
 * matters.** Nothing is evicted as the reader scrolls: a page Paging has since
 * dropped from memory is still in here, and so is every page since the last
 * refresh. So this can answer for an article that has scrolled off the screen,
 * and it grows with how far the reader scrolled rather than with how much of
 * the list is visible. `DECISIONS.md` 41 chose that on purpose and gave the
 * reason -- a capacity number would be a picked number, and the list is already
 * a bound -- but the sentences here used to claim the tighter one, which the
 * code has never had.
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
 * It holds the same [ArticleItem] objects the feed was handed, so what it costs
 * over those is one map entry per article loaded since the last refresh -- a
 * reference and a key, not a second copy of the article.
 */
internal class ArticlesTheFeedShowed {

    private val shown = mutableMapOf<ArticleId, ArticleItem>()

    /**
     * Remember a page the feed was just handed.
     *
     * [fromTheTop] is the refresh: the list is starting again, so this starts
     * again with it. That is the only thing that ever removes anything -- there
     * is no eviction as the reader scrolls, because there is nothing here to
     * evict against (see above).
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
