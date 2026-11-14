package app.habitrpg.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** One table for all three task types; type-specific columns are simply unused by the others. */
@Serializable
@Entity(tableName = "tasks", indices = [Index("type", "sortOrder")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val notes: String = "",
    val difficulty: String,
    val sortOrder: Int,
    val archived: Boolean = false,
    // habit
    val plusEnabled: Boolean = true,
    val minusEnabled: Boolean = true,
    val plusCount: Int = 0,
    val minusCount: Int = 0,
    /** Task value: low pays more, high pays less. Habits and to-dos only. Added in schema version 3. */
    @ColumnInfo(defaultValue = "0") val value: Double = 0.0,
    // daily: scheduleKind 0 = none, 1 = weekday mask, 2 = every N days from startDate
    val scheduleKind: Int = 0,
    val weekdayMask: Int = 0,
    val everyN: Int = 1,
    val startDate: String? = null,
    val streak: Int = 0,
    val doneToday: Boolean = false,
    // to-do
    val dueDate: String? = null,
    val completedAt: Long? = null,
)

@Serializable
@Entity(
    tableName = "checklist_items",
    foreignKeys = [ForeignKey(TaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId")],
)
data class ChecklistItemEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val text: String,
    val done: Boolean,
    val position: Int,
)

/** Append-only score log. Deliberately has no foreign key: history outlives deleted tasks. */
@Serializable
@Entity(tableName = "task_events", indices = [Index("taskId"), Index("timestamp")])
data class TaskEventEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    /** Title at the time of the event, so history stays readable after the task is deleted. */
    val taskTitle: String,
    val timestamp: Long,
    val kind: String,
    val xp: Int,
    val gold: Int,
    val health: Int,
    val day: String?,
    /** Boss hp this change removed. Added in schema version 4. */
    @ColumnInfo(defaultValue = "0") val bossDamage: Int = 0,
)

@Serializable
@Entity(tableName = "character")
data class CharacterEntity(
    @PrimaryKey val id: Int = 1,
    val level: Int,
    val xp: Int,
    val health: Int,
    val gold: Int,
    val lastRolloverDate: String?,
    // Classes, added in schema version 4.
    val classId: String? = null,
    @ColumnInfo(defaultValue = "0") val mana: Int = 0,
    @ColumnInfo(defaultValue = "0") val statMight: Int = 0,
    @ColumnInfo(defaultValue = "0") val statMind: Int = 0,
    @ColumnInfo(defaultValue = "0") val statVigor: Int = 0,
)

@Serializable
@Entity(tableName = "rewards")
data class RewardEntity(
    @PrimaryKey val id: String,
    val title: String,
    val cost: Int,
    val notes: String = "",
    val sortOrder: Int = 0,
)

@Serializable
@Entity(tableName = "reward_purchases", indices = [Index("rewardId")])
data class RewardPurchaseEntity(
    @PrimaryKey val id: String,
    val rewardId: String,
    val timestamp: Long,
    val costPaid: Int,
)

@Serializable
@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Serializable
@Entity(
    tableName = "task_tags",
    primaryKeys = ["taskId", "tagId"],
    foreignKeys = [
        ForeignKey(TaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class TaskTagEntity(val taskId: String, val tagId: String)

@Serializable
@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(TaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId")],
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val minuteOfDay: Int,
    val weekdayMask: Int,
)

/**
 * Shop items the player has bought. Kept in its own table rather than on the character row, because
 * `GameRepository.persist` rewrites that row from the domain character and would wipe extra columns.
 */
@Serializable
@Entity(tableName = "owned_items")
data class OwnedItemEntity(
    @PrimaryKey val itemId: String,
    val acquiredAt: Long,
    val pricePaid: Int,
)

/** What is worn or kept as a pet, one row per slot (a `Slot` name). */
@Serializable
@Entity(tableName = "equipped")
data class EquippedEntity(
    @PrimaryKey val slot: String,
    val itemId: String,
)

/** The one running (or just finished, not yet dismissed) boss quest. Single row, id 1. Added in schema version 4. */
@Serializable
@Entity(tableName = "quest")
data class QuestEntity(
    @PrimaryKey val id: Int = 1,
    val bossId: String,
    val hp: Int,
    val startedDate: String,
    val deadlineDate: String,
    /** A `QuestOutcome` name. */
    val outcome: String,
    val endDate: String?,
)

/** A dismissed quest, kept for the Quests tab. Added in schema version 4. */
@Serializable
@Entity(tableName = "quest_history")
data class QuestHistoryEntity(
    @PrimaryKey val id: String,
    val bossId: String,
    val startedDate: String,
    val endDate: String,
    val outcome: String,
)
