package moozy.mosaic.domain.model

/**
 * Where to continue a list from.
 *
 * Opaque on purpose: it is whatever the source needs in order to hand over what
 * comes next, and nothing above the data layer should be doing arithmetic on it.
 */
@JvmInline
value class PageCursor(val value: String) {
    init {
        require(value.isNotBlank()) { "A page cursor cannot be blank." }
    }
}

/**
 * Why the feed could not be shown.
 *
 * These are separate cases rather than one error carrying a message, because the
 * app promises the reader four different screens and a screen cannot be chosen
 * from a string. [detail] is for whoever is diagnosing this later; it is not
 * something to put in front of a reader.
 */
sealed interface FeedFailure {

    val detail: String?

    /** The request never left, or never arrived. Trying again later is reasonable. */
    data class Offline(override val detail: String? = null) : FeedFailure

    /** It left, and nothing came back in time. */
    data class Timeout(override val detail: String? = null) : FeedFailure

    /** Something came back, and it was an error. */
    data class Server(val status: Int, override val detail: String? = null) : FeedFailure

    /** Something came back, and it was not something this app can read. */
    data class Unreadable(override val detail: String? = null) : FeedFailure

    /**
     * Something went wrong that this app did not anticipate.
     *
     * It exists so that the promise "failures become answers here" has no
     * exceptions: a case nobody thought of is still not allowed to travel up as
     * a crash.
     */
    data class Unexpected(override val detail: String? = null) : FeedFailure
}

/**
 * What asking for articles produced.
 *
 * A sealed type rather than a nullable list, so that "no articles" and "could not
 * ask" cannot be mistaken for one another by a caller that forgot to check.
 */
sealed interface ArticlesResult {

    /**
     * [dropped] is how many rows arrived and could not be used. A page where
     * every row was unusable loads as an empty list, which is indistinguishable
     * from a feed that has run out -- unless the count comes with it.
     */
    data class Loaded(
        val articles: List<ArticleItem>,
        val next: PageCursor?,
        val dropped: Int = 0,
    ) : ArticlesResult

    data class Failed(val reason: FeedFailure) : ArticlesResult
}

/**
 * What asking for one article produced. Separate from [ArticlesResult] because the
 * questions are different: a list can be empty and still be an answer, and a single
 * article cannot.
 */
sealed interface ArticleResult {

    data class Loaded(val article: ArticleItem) : ArticleResult

    data class Failed(val reason: FeedFailure) : ArticleResult
}
