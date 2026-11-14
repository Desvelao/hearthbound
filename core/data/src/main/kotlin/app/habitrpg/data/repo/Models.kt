package app.habitrpg.data.repo

import app.habitrpg.domain.Difficulty
import app.habitrpg.domain.Schedule
import app.habitrpg.domain.Task
import app.habitrpg.domain.TaskType
import java.time.Instant
import java.time.LocalDate

data class ReminderItem(val id: String, val minuteOfDay: Int, val weekdayMask: Int)

/** A reminder as the editor produces it. [weekdayMask] bit 0 is Monday. */
data class ReminderDraft(val minuteOfDay: Int, val weekdayMask: Int = 0b1111111)

data class ChecklistItem(val id: String, val text: String, val done: Boolean, val position: Int)

/** Read model: the engine's [Task] plus everything only the UI and storage care about. */
data class TaskItem(
    val task: Task,
    val title: String,
    val notes: String,
    val sortOrder: Int,
    val dueDate: LocalDate?,
    val completedAt: Instant?,
    val checklist: List<ChecklistItem>,
    val tagIds: Set<String> = emptySet(),
    val reminders: List<ReminderItem> = emptyList(),
) {
    val id: String get() = task.id
    val type: TaskType get() = task.type
}

data class ChecklistDraft(val text: String, val done: Boolean = false)

/** What the editor produces. `id == null` means a new task. */
data class TaskDraft(
    val id: String? = null,
    val type: TaskType,
    val title: String,
    val notes: String = "",
    val difficulty: Difficulty = Difficulty.EASY,
    val plusEnabled: Boolean = true,
    val minusEnabled: Boolean = true,
    val schedule: Schedule? = null,
    val dueDate: LocalDate? = null,
    val checklist: List<ChecklistDraft> = emptyList(),
    val tagIds: Set<String> = emptySet(),
    val reminders: List<ReminderDraft> = emptyList(),
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

const val DEFAULT_HAIR = "hair_short_brown"
const val DEFAULT_BODY = "body_default"

data class Settings(
    val dayStartHour: Int = app.habitrpg.domain.Constants.DEFAULT_DAY_START_HOUR,
    val paused: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Id of the hair sprite in `art/manifest.yaml`. */
    val hair: String = DEFAULT_HAIR,
    val onboardingDone: Boolean = false,
    /** Id of the body sprite in `art/manifest.yaml`. */
    val body: String = DEFAULT_BODY,
    /** When on, equipped gear gives its small bonuses; when off, gear is cosmetic. */
    val gearBonuses: Boolean = true,
    /** Timed boss quests. Off: bosses take no damage and never escape; the running quest waits. */
    val quests: Boolean = true,
    /** Classes, stat points, mana and skills. Off: no bonuses and no skills; choices are kept. */
    val classes: Boolean = true,
    /** An [AppLanguage] tag. `system` follows the phone. */
    val language: String = AppLanguage.SYSTEM.tag,
)
