package app.habitrpg.data.repo

import app.habitrpg.data.db.AppDatabase
import app.habitrpg.data.db.TagEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class TagRepository @Inject constructor(db: AppDatabase) {
    private val dao = db.tagDao()

    fun observe(): Flow<List<TagEntity>> = dao.observeAll()

    /** Creates a tag, or returns the id of the existing one with the same name (ignoring case). Null if blank. */
    suspend fun create(name: String): String? {
        val clean = name.trim().removePrefix("#").trim().take(MAX_LENGTH)
        if (clean.isEmpty()) return null
        dao.getAll().firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return it.id }
        val id = UUID.randomUUID().toString()
        dao.upsert(TagEntity(id, clean))
        return id
    }

    /** Deleting a tag removes it from every task (the link rows cascade) but never deletes a task. */
    suspend fun delete(id: String) = dao.delete(id)

    companion object { const val MAX_LENGTH = 24 }
}
