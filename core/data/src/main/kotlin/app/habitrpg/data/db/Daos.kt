package app.habitrpg.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE type = :type AND archived = 0 ORDER BY sortOrder, id")
    fun observeByType(type: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE archived = 0 ORDER BY sortOrder, id")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    /** Every task including archived ones, for backups. */
    @Query("SELECT * FROM tasks ORDER BY type, sortOrder, id")
    suspend fun exportAll(): List<TaskEntity>

    @Insert
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM tasks WHERE type = :type")
    suspend fun maxSortOrder(type: String): Int

    /** Updates in place. `INSERT OR REPLACE` would delete the row first and cascade away its children. */
    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Update
    suspend fun update(tasks: List<TaskEntity>)

    @Query("UPDATE tasks SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: String, order: Int)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklist_items ORDER BY taskId, position")
    fun observeAll(): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items WHERE taskId = :taskId ORDER BY position")
    suspend fun forTask(taskId: String): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklist_items ORDER BY taskId, position")
    suspend fun getAll(): List<ChecklistItemEntity>

    @Query("DELETE FROM checklist_items")
    suspend fun deleteAll()

    @Query("DELETE FROM checklist_items WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChecklistItemEntity>)

    /** A new day starts every daily's checklist unticked. */
    @Query("UPDATE checklist_items SET done = 0 WHERE taskId IN (SELECT id FROM tasks WHERE type = 'DAILY')")
    suspend fun resetDailyChecklists()
}

@Dao
interface EventDao {
    @Insert
    suspend fun insertAll(events: List<TaskEventEntity>)

    @Query("DELETE FROM task_events")
    suspend fun deleteAll()

    @Query("SELECT * FROM task_events ORDER BY timestamp DESC, rowid DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TaskEventEntity>>

    @Query("SELECT * FROM task_events WHERE timestamp >= :since ORDER BY timestamp, rowid")
    fun observeSince(since: Long): Flow<List<TaskEventEntity>>

    @Query("SELECT * FROM task_events ORDER BY timestamp, rowid")
    suspend fun getAll(): List<TaskEventEntity>

    @Query("SELECT * FROM task_events ORDER BY timestamp, rowid")
    fun observeAll(): Flow<List<TaskEventEntity>>

    /** The latest DONE or UNDONE entry for a task: if it is DONE, the task is currently ticked. */
    @Query("SELECT * FROM task_events WHERE taskId = :taskId AND kind IN ('DONE', 'UNDONE') ORDER BY timestamp DESC, rowid DESC LIMIT 1")
    suspend fun lastCompletion(taskId: String): TaskEventEntity?
}

@Dao
interface CharacterDao {
    @Query("SELECT * FROM character WHERE id = 1")
    fun observe(): Flow<CharacterEntity?>

    @Query("SELECT * FROM character WHERE id = 1")
    suspend fun get(): CharacterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(character: CharacterEntity)

    @Query("DELETE FROM character")
    suspend fun deleteAll()
}

@Dao
interface RewardDao {
    @Query("SELECT * FROM rewards ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<RewardEntity>>

    @Query("SELECT * FROM rewards ORDER BY sortOrder, id")
    suspend fun getAll(): List<RewardEntity>

    @Query("SELECT * FROM rewards WHERE id = :id")
    suspend fun get(id: String): RewardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reward: RewardEntity)

    @Query("DELETE FROM rewards WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COALESCE(SUM(costPaid), 0) FROM reward_purchases")
    fun observeSpent(): Flow<Int>

    @Insert
    suspend fun insertPurchase(purchase: RewardPurchaseEntity)

    @Insert
    suspend fun insertPurchases(purchases: List<RewardPurchaseEntity>)

    @Insert
    suspend fun insertAll(rewards: List<RewardEntity>)

    @Query("DELETE FROM rewards")
    suspend fun deleteAll()

    @Query("DELETE FROM reward_purchases")
    suspend fun deleteAllPurchases()

    @Query("SELECT * FROM reward_purchases ORDER BY timestamp")
    suspend fun getPurchases(): List<RewardPurchaseEntity>
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name")
    suspend fun getAll(): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: String)

    @Insert
    suspend fun insertAll(tags: List<TagEntity>)

    @Query("DELETE FROM tags")
    suspend fun deleteAll()

    @Query("DELETE FROM task_tags")
    suspend fun deleteAllLinks()

    @Query("SELECT * FROM task_tags")
    fun observeLinks(): Flow<List<TaskTagEntity>>

    @Query("SELECT * FROM task_tags")
    suspend fun getLinks(): List<TaskTagEntity>

    @Query("DELETE FROM task_tags WHERE taskId = :taskId")
    suspend fun clearLinks(taskId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<TaskTagEntity>)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY taskId, minuteOfDay")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY taskId, minuteOfDay")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE taskId = :taskId ORDER BY minuteOfDay")
    suspend fun forTask(taskId: String): List<ReminderEntity>

    @Query("DELETE FROM reminders WHERE taskId = :taskId")
    suspend fun clearForTask(taskId: String)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<ReminderEntity>)
}

@Dao
interface ShopDao {
    @Query("SELECT * FROM owned_items ORDER BY acquiredAt, itemId")
    fun observeOwned(): Flow<List<OwnedItemEntity>>

    @Query("SELECT * FROM owned_items ORDER BY acquiredAt, itemId")
    suspend fun getOwned(): List<OwnedItemEntity>

    @Query("SELECT * FROM owned_items WHERE itemId = :itemId")
    suspend fun getOwnedItem(itemId: String): OwnedItemEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOwned(item: OwnedItemEntity)

    @Insert
    suspend fun insertAllOwned(items: List<OwnedItemEntity>)

    @Query("SELECT COALESCE(SUM(pricePaid), 0) FROM owned_items")
    fun observeSpent(): Flow<Int>

    @Query("SELECT * FROM equipped")
    fun observeEquipped(): Flow<List<EquippedEntity>>

    @Query("SELECT * FROM equipped")
    suspend fun getEquipped(): List<EquippedEntity>

    /** Updates in place; one row per slot. */
    @Upsert
    suspend fun setEquipped(row: EquippedEntity)

    @Query("DELETE FROM equipped WHERE slot = :slot")
    suspend fun clearSlot(slot: String)

    @Insert
    suspend fun insertAllEquipped(rows: List<EquippedEntity>)

    @Query("DELETE FROM equipped")
    suspend fun deleteAllEquipped()

    @Query("DELETE FROM owned_items")
    suspend fun deleteAllOwned()
}

@Dao
interface QuestDao {
    @Query("SELECT * FROM quest WHERE id = 1")
    fun observe(): Flow<QuestEntity?>

    @Query("SELECT * FROM quest WHERE id = 1")
    suspend fun get(): QuestEntity?

    @Upsert
    suspend fun upsert(row: QuestEntity)

    @Query("DELETE FROM quest")
    suspend fun delete()

    @Query("SELECT * FROM quest_history ORDER BY endDate DESC, id")
    fun observeHistory(): Flow<List<QuestHistoryEntity>>

    @Query("SELECT * FROM quest_history ORDER BY endDate, id")
    suspend fun getHistory(): List<QuestHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(row: QuestHistoryEntity)

    @Insert
    suspend fun insertAllHistory(rows: List<QuestHistoryEntity>)

    @Query("DELETE FROM quest_history")
    suspend fun deleteHistory()
}
