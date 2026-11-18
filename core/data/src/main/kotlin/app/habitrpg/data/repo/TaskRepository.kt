package app.habitrpg.data.repo

import androidx.room.withTransaction
import app.habitrpg.data.db.AppDatabase
import app.habitrpg.data.db.ChecklistItemEntity
import app.habitrpg.data.db.ReminderEntity
import app.habitrpg.data.db.TaskEntity
import app.habitrpg.data.db.TaskTagEntity
import app.habitrpg.domain.TaskType
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class TaskRepository @Inject constructor(
    private val db: AppDatabase,
    private val scheduler: ReminderScheduler,
) {
    private val tasks = db.taskDao()
    private val checklists = db.checklistDao()
    private val tags = db.tagDao()
    private val reminders = db.reminderDao()

    fun observe(type: TaskType): Flow<List<TaskItem>> =
        combine(tasks.observeByType(type.name), checklists.observeAll(), tags.observeLinks(), reminders.observeAll()) {
            rows, items, links, rems ->
            val byTask = items.groupBy { it.taskId }
            val tagsByTask = links.groupBy({ it.taskId }, { it.tagId })
            val remindersByTask = rems.groupBy { it.taskId }
            rows.map {
                it.toItem(byTask[it.id].orEmpty(), tagsByTask[it.id].orEmpty().toSet(), remindersByTask[it.id].orEmpty())
            }
        }

    suspend fun get(id: String): TaskItem? {
        val row = tasks.get(id) ?: return null
        val tagIds = tags.getLinks().filter { it.taskId == id }.map { it.tagId }.toSet()
        return row.toItem(checklists.forTask(id), tagIds, reminders.forTask(id))
    }

    /** Creates or edits a task. Editing never touches engine-owned fields (counts, streak, done state). */
    suspend fun save(draft: TaskDraft): String = saveAll(listOf(draft)).single()

    /** Saves [drafts] in one transaction, in order: either all of them are stored or none. Returns their ids. */
    suspend fun saveAll(drafts: List<TaskDraft>): List<String> {
        val oldReminders = mutableListOf<ReminderEntity>()
        val newReminders = mutableListOf<ReminderEntity>()
        val ids = db.withTransaction {
            oldReminders.clear()
            newReminders.clear()
            drafts.map { draft ->
                oldReminders += draft.id?.let { reminders.forTask(it) }.orEmpty()
                val id = saveRows(draft)
                reminders.clearForTask(id)
                val rows = draft.reminders.filter { it.weekdayMask and 0b1111111 != 0 }.map {
                    ReminderEntity(UUID.randomUUID().toString(), id, it.minuteOfDay, it.weekdayMask)
                }
                reminders.insertAll(rows)
                newReminders += rows
                id
            }
        }
        // Alarms change only after the rows committed, so an alarm never outlives a rolled-back save.
        oldReminders.forEach { scheduler.cancel(it.id) }
        newReminders.forEach(scheduler::schedule)
        return ids
    }

    private suspend fun saveRows(draft: TaskDraft): String {
        val id = draft.id ?: UUID.randomUUID().toString()
        val existing = draft.id?.let { tasks.get(it) }
        val (kind, mask, every) = draft.schedule.toColumns()
        val base = existing ?: TaskEntity(
            id = id, type = draft.type.name, title = "", difficulty = draft.difficulty.name,
            sortOrder = tasks.maxSortOrder(draft.type.name) + 1,
        )
        tasks.upsert(
            base.copy(
                title = draft.title.trim(),
                notes = draft.notes,
                difficulty = draft.difficulty.name,
                plusEnabled = draft.plusEnabled,
                minusEnabled = draft.minusEnabled,
                scheduleKind = kind,
                weekdayMask = mask,
                everyN = every.first,
                startDate = every.second,
                dueDate = draft.dueDate?.toString(),
            ),
        )
        checklists.deleteForTask(id)
        checklists.insertAll(
            draft.checklist.filter { it.text.isNotBlank() }.mapIndexed { i, c ->
                ChecklistItemEntity(UUID.randomUUID().toString(), id, c.text.trim(), c.done, i)
            },
        )
        tags.clearLinks(id)
        tags.insertLinks(draft.tagIds.map { TaskTagEntity(id, it) })
        return id
    }

    suspend fun delete(id: String) {
        val alarms = reminders.forTask(id)
        tasks.delete(id) // reminders are removed by the foreign key cascade
        alarms.forEach { scheduler.cancel(it.id) }
    }

    /** Persists the order of [orderedIds] (all of one type). */
    suspend fun reorder(orderedIds: List<String>) = db.withTransaction {
        orderedIds.forEachIndexed { i, id -> tasks.setSortOrder(id, i) }
    }

    suspend fun setChecklistItemDone(itemId: String, taskId: String, done: Boolean) = db.withTransaction {
        checklists.insertAll(checklists.forTask(taskId).filter { it.id == itemId }.map { it.copy(done = done) })
    }

    private fun TaskEntity.toItem(
        items: List<ChecklistItemEntity>,
        tagIds: Set<String>,
        reminderRows: List<ReminderEntity>,
    ) = TaskItem(
        task = toEngine(),
        title = title,
        notes = notes,
        sortOrder = sortOrder,
        dueDate = dueDate?.let(LocalDate::parse),
        completedAt = completedAt?.let(Instant::ofEpochMilli),
        checklist = items.map { ChecklistItem(it.id, it.text, it.done, it.position) },
        tagIds = tagIds,
        reminders = reminderRows.map { ReminderItem(it.id, it.minuteOfDay, it.weekdayMask) },
    )
}
