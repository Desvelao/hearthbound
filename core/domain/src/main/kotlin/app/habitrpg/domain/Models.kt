package app.habitrpg.domain

import java.time.Instant
import java.time.LocalDate

enum class TaskType { HABIT, DAILY, TODO }

data class Character(
    val level: Int = 1,
    val xp: Int = 0,
    val health: Int = Constants.MAX_HEALTH,
    val gold: Int = 0,
    val lastRolloverDate: LocalDate? = null,
    /** Chosen class id (see [Classes]); null until the player picks one at [Constants.CLASS_UNLOCK_LEVEL]. */
    val classId: String? = null,
    val mana: Int = 0,
    val statMight: Int = 0,
    val statMind: Int = 0,
    val statVigor: Int = 0,
)

data class Task(
    val id: String,
    val type: TaskType,
    val difficulty: Difficulty = Difficulty.EASY,
    // habit
    val plusEnabled: Boolean = true,
    val minusEnabled: Boolean = true,
    val plusCount: Int = 0,
    val minusCount: Int = 0,
    /** Task value (see [Constants.VALUE_STEP]). Low means neglected and pays more; high means farmed and pays less. */
    val value: Double = 0.0,
    // daily
    val schedule: Schedule? = null,
    val streak: Int = 0,
    val doneToday: Boolean = false,
    // to-do
    val completed: Boolean = false,
)

/** The two phase 3 switches. Off means the rules do nothing and the data is kept as it is. */
data class Features(val quests: Boolean = true, val classes: Boolean = true)

enum class QuestOutcome { ACTIVE, WON, ESCAPED }

/**
 * The one running boss quest. [deadlineDate] is the last game day the boss can still be beaten on. A finished quest
 * (WON or ESCAPED) stays in state until dismissed, so undoing the killing blow still works.
 */
data class Quest(
    val bossId: String,
    val hp: Int,
    val startedDate: LocalDate,
    val deadlineDate: LocalDate,
    val outcome: QuestOutcome = QuestOutcome.ACTIVE,
    val endDate: LocalDate? = null,
)

data class GameState(
    val character: Character = Character(),
    val tasks: Map<String, Task> = emptyMap(),
    val paused: Boolean = false,
    val dayStartHour: Int = Constants.DEFAULT_DAY_START_HOUR,
    val bonuses: Bonuses = Bonuses.NONE,
    val quest: Quest? = null,
    val features: Features = Features(),
)

enum class ChangeKind { PLUS, MINUS, DONE, UNDONE, MISSED }

/**
 * One score change. Carries before/after snapshots so the log can power exact undo and stats.
 * [xp], [gold] and [health] are the signed deltas the user saw (before level effects).
 */
data class Change(
    val taskId: String,
    val kind: ChangeKind,
    val timestamp: Instant,
    val xp: Int,
    val gold: Int,
    val health: Int,
    val characterBefore: Character,
    val characterAfter: Character,
    val taskBefore: Task,
    val taskAfter: Task,
    val day: LocalDate? = null,
    /** Boss hp this change removed (zero when quests are off or there was no active boss). */
    val bossDamage: Int = 0,
)

/** Level or zero-health events the UI turns into dialogs. */
enum class Milestone { LEVEL_UP, HEALTH_ZERO, QUEST_WON, QUEST_ESCAPED }

data class EngineResult(
    val state: GameState,
    val changes: List<Change> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
)

sealed interface Event {
    data class HabitPlus(val taskId: String) : Event
    data class HabitMinus(val taskId: String) : Event
    data class CompleteDaily(val taskId: String) : Event
    data class CompleteTodo(val taskId: String) : Event
    /** Reverses [change] (a DONE, PLUS or MINUS change). */
    data class Undo(val change: Change) : Event
    /**
     * Reverses a logged change from its recorded deltas, for un-ticking after a restart when no [Change]
     * snapshot exists. Only valid for PLUS, MINUS and DONE changes.
     */
    data class Revert(val taskId: String, val kind: ChangeKind, val xp: Int, val gold: Int, val health: Int, val bossDamage: Int = 0) : Event
    /** Pays [amount] gold for a reward. Does nothing when gold is short. */
    data class Spend(val amount: Int) : Event
    /**
     * Moves the stored last-rollover date to today without scoring anything. Used when the day-start hour
     * changes, so the new setting never turns into surprise damage or a skipped day.
     */
    data class StartQuest(val bossId: String) : Event
    /** Moves a finished (won or escaped) quest out of the way so another can start. */
    data object DismissQuest : Event
    data class ChooseClass(val classId: String) : Event
    data class SpendStat(val stat: Stat) : Event
    /** Clears class and stat points for [Constants.RESPEC_COST] gold. */
    data object Respec : Event
    data class CastSkill(val skillId: String) : Event
    data object AnchorToday : Event
    data object Rollover : Event
}
