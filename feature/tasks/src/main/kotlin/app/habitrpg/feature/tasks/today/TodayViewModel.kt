package app.habitrpg.feature.tasks.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.habitrpg.data.repo.GameRepository
import app.habitrpg.data.repo.HistoryMath
import app.habitrpg.data.repo.SettingsRepository
import app.habitrpg.data.repo.TaskItem
import app.habitrpg.data.repo.TaskRepository
import app.habitrpg.domain.Bonuses
import app.habitrpg.domain.Engine
import app.habitrpg.domain.QuestCatalog
import app.habitrpg.domain.QuestOutcome
import app.habitrpg.domain.Event
import app.habitrpg.domain.GameDay
import app.habitrpg.domain.TaskType
import app.habitrpg.domain.Worth
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TodayFilter { ALL, DAILIES, TODOS, HABITS }

/**
 * One line on the Today list. [xp] is what a tick or plus would earn right now, [note] a short right-hand label such
 * as "Due today", "Overdue" or the next reminder time.
 */
data class TodayRow(val item: TaskItem, val xp: Int, val note: RowNote?, val worth: Worth? = null)

/** The short right-hand label on a Today row. The screen turns it into words in the player's language. */
sealed interface RowNote {
    data object Overdue : RowNote
    data object DueToday : RowNote
    /** The next reminder still to come today. */
    data class Reminder(val minuteOfDay: Int) : RowNote
}

/** The running boss, as the Today card shows it. */
data class QuestCard(val bossId: String, val hp: Int, val maxHp: Int, val daysLeft: Int, val outcome: QuestOutcome)

data class TodayState(
    val quest: QuestCard? = null,
    val loaded: Boolean = false,
    val date: LocalDate = LocalDate.of(1970, 1, 1), // not LocalDate.EPOCH: that field needs Android 14
    val filter: TodayFilter = TodayFilter.ALL,
    val dueNow: List<TodayRow> = emptyList(),
    val habits: List<TodayRow> = emptyList(),
    val done: List<TodayRow> = emptyList(),
    /** Dailies due today plus the to-dos in play, finished or not. Habits are open-ended and not counted. */
    val total: Int = 0,
    val finished: Int = 0,
)

/** Turns the three task lists into what the Today screen shows. Pure, so the rules are easy to test. */
object TodayBuilder {
    fun build(
        habits: List<TaskItem>,
        dailies: List<TaskItem>,
        todos: List<TaskItem>,
        today: LocalDate,
        nowMinuteOfDay: Int,
        zone: ZoneId,
        dayStartHour: Int,
        filter: TodayFilter,
        bonuses: Bonuses = Bonuses.NONE,
    ): TodayState {
        val dueDailies = dailies.filter { it.task.schedule?.isDue(today) == true }
        val openDailies = dueDailies.filter { !it.task.doneToday }
        val doneDailies = dueDailies.filter { it.task.doneToday }

        // To-dos are in play when they have no date or are due by today. Later ones wait for their day.
        val openTodos = todos.filter { todo ->
            val due = todo.dueDate // a local copy: Kotlin cannot smart-cast a property from another module
            !todo.task.completed && (due == null || !due.isAfter(today))
        }
            .sortedBy { it.dueDate ?: LocalDate.MAX } // overdue, then due today, then undated; stable within each
        val doneTodos = todos.filter { todo ->
            todo.task.completed && todo.completedAt?.let { HistoryMath.gameDay(it, zone, dayStartHour) == today } == true
        }

        fun row(item: TaskItem): TodayRow {
            val xp = when (item.type) {
                TaskType.DAILY -> Engine.xpFor(item.task.difficulty, Engine.streakBonus(item.task.streak), bonuses)
                else -> Engine.xpFor(item.task.difficulty, 1.0, bonuses, item.task.value) // the same call the engine makes
            }
            val due = item.dueDate
            val note = when {
                item.type == TaskType.TODO && due != null && due.isBefore(today) -> RowNote.Overdue
                item.type == TaskType.TODO && due == today -> RowNote.DueToday
                else -> nextReminder(item, today, nowMinuteOfDay)
            }
            return TodayRow(item, xp, note, if (item.type == TaskType.DAILY) null else Worth.of(item.task.value))
        }

        val showDailies = filter == TodayFilter.ALL || filter == TodayFilter.DAILIES
        val showTodos = filter == TodayFilter.ALL || filter == TodayFilter.TODOS
        val showHabits = filter == TodayFilter.ALL || filter == TodayFilter.HABITS
        return TodayState(
            loaded = true,
            date = today,
            filter = filter,
            dueNow = (if (showDailies) openDailies.map(::row) else emptyList()) + (if (showTodos) openTodos.map(::row) else emptyList()),
            habits = if (showHabits) habits.map(::row) else emptyList(),
            done = (if (showDailies) doneDailies.map(::row) else emptyList()) + (if (showTodos) doneTodos.map(::row) else emptyList()),
            total = dueDailies.size + openTodos.size + doneTodos.size,
            finished = doneDailies.size + doneTodos.size,
        )
    }

    /** The earliest reminder still to come today. */
    private fun nextReminder(item: TaskItem, today: LocalDate, nowMinute: Int): RowNote.Reminder? =
        nextReminderMinute(item, today, nowMinute)?.let(RowNote::Reminder)

    private fun nextReminderMinute(item: TaskItem, today: LocalDate, nowMinute: Int): Int? =
        item.reminders
            .filter { it.weekdayMask and (1 shl (today.dayOfWeek.value - 1)) != 0 && it.minuteOfDay >= nowMinute }
            .minOfOrNull { it.minuteOfDay }
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    repo: TaskRepository,
    private val game: GameRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _filter = MutableStateFlow(TodayFilter.ALL)

    val state: StateFlow<TodayState> = combine(
        repo.observe(TaskType.HABIT), repo.observe(TaskType.DAILY), repo.observe(TaskType.TODO),
        combine(settings.settings, game.observeBonuses(), game.observeQuest()) { s, b, q -> Triple(s, b, q) }, _filter,
    ) { habits, dailies, todos, (s, bonuses, quest), filter ->
        val now = clock.instant().atZone(clock.zone)
        val today = GameDay.today(clock, s.dayStartHour)
        val card = quest?.takeIf { s.quests }?.let { q ->
            val boss = QuestCatalog.byId(q.bossId) ?: return@let null
            QuestCard(boss.id, q.hp, boss.hp, (java.time.temporal.ChronoUnit.DAYS.between(today, q.deadlineDate).toInt() + 1).coerceAtLeast(0), q.outcome)
        }
        TodayBuilder.build(
            habits, dailies, todos, today, now.hour * 60 + now.minute, clock.zone, s.dayStartHour, filter, bonuses,
        ).copy(quest = card)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    val filter: StateFlow<TodayFilter> = _filter.asStateFlow()
    fun setFilter(value: TodayFilter) { _filter.value = value }

    fun plus(id: String) { viewModelScope.launch { game.dispatch(Event.HabitPlus(id)) } }
    fun minus(id: String) { viewModelScope.launch { game.dispatch(Event.HabitMinus(id)) } }

    fun setDone(item: TaskItem, done: Boolean) {
        viewModelScope.launch {
            if (done) game.dispatch(if (item.type == TaskType.DAILY) Event.CompleteDaily(item.id) else Event.CompleteTodo(item.id))
            else game.uncomplete(item.id)
        }
    }
}
