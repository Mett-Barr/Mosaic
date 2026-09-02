package moozy.mosaic.data.saved

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * The local source, and the only one.
 *
 * This app has no remote idea of what a reader kept -- the news API has no
 * accounts and no per-reader collection -- so there is nothing for a repository
 * to choose between and no second source to reconcile against.
 */
@Dao
internal interface SavedArticleDao {

    /** Most recently saved first; the id breaks ties so the order is total. */
    @Query("SELECT * FROM saved_articles ORDER BY saved_at DESC, id DESC")
    fun saved(): Flow<List<SavedArticleEntity>>

    /**
     * The one row a reader kept under this id, or nothing.
     *
     * Separate from [saved] because the questions are different sizes. Answering
     * "is this one here" out of the list means loading every kept article to
     * look at one of them, which is work that grows with the reading list and
     * buys nothing; the index on the primary key already knows the answer.
     */
    @Query("SELECT * FROM saved_articles WHERE id = :id")
    suspend fun find(id: String): SavedArticleEntity?

    /**
     * Saving something already saved is how a reader updates it, not how they
     * get two of it: INSERT OR REPLACE keeps one row per id, and because
     * saved_at is written fresh the article moves back to the top.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(article: SavedArticleEntity)

    /** One statement, and therefore one transaction: the import is all or nothing. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAll(articles: List<SavedArticleEntity>)

    @Query("DELETE FROM saved_articles WHERE id = :id")
    suspend fun forget(id: String)
}
