package app.habitrpg.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Schema changes need a version bump and a real Migration; the exported schema in `schemas/` is committed.
 */
@Database(
    entities = [
        TaskEntity::class, ChecklistItemEntity::class, TaskEventEntity::class, CharacterEntity::class,
        RewardEntity::class, RewardPurchaseEntity::class, TagEntity::class, TaskTagEntity::class,
        ReminderEntity::class, OwnedItemEntity::class, EquippedEntity::class, QuestEntity::class, QuestHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun eventDao(): EventDao
    abstract fun characterDao(): CharacterDao
    abstract fun rewardDao(): RewardDao
    abstract fun tagDao(): TagDao
    abstract fun reminderDao(): ReminderDao
    abstract fun shopDao(): ShopDao
    abstract fun questDao(): QuestDao
}
