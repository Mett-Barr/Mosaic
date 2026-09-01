package moozy.mosaic.feature.feed

import moozy.mosaic.domain.model.FeedFailure

/**
 * A [FeedFailure] on its way through Paging.
 *
 * `LoadResult.Error` carries a `Throwable` and nothing else, so a typed failure
 * has to travel inside one to reach the screen. This exists only for that trip:
 * it is unwrapped the moment the load state is read, and nothing ever throws it.
 *
 * The cost is that the compiler stops checking the failure is handled -- a
 * `Throwable` has no exhaustive `when`. That is the price of the library's
 * signature, and it is named here rather than hidden behind a cast.
 */
internal class FeedRefused(val reason: FeedFailure) : Exception(reason.toString())
