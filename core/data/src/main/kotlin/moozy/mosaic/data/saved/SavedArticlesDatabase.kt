package moozy.mosaic.data.saved

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The one table, and the name says which one.
 *
 * Not MosaicDatabase and not AppDatabase: the feed is not going local-first and
 * the weather is a single reading a hundred-line file handles, so there is no
 * second table waiting for a vaguer name. A name that reads oddly the day a
 * second table arrives is cheaper than a name that reads vaguely today.
 */
@Database(entities = [SavedArticleEntity::class], version = 1, exportSchema = true)
internal abstract class SavedArticlesDatabase : RoomDatabase() {
    abstract fun saved(): SavedArticleDao
}
