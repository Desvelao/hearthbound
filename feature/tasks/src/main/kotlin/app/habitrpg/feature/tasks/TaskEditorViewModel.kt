package app.habitrpg.feature.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.habitrpg.data.repo.ChecklistDraft
import app.habitrpg.data.repo.ReminderDraft
import app.habitrpg.data.repo.SettingsRepository
import app.habitrpg.data.repo.TagRepository
import app.habitrpg.data.repo.TaskDraft
import app.habitrpg.data.repo.TaskRepository
import app.habitrpg.domain.Difficulty
import app.habitrpg.domain.GameDay
import app.habitrpg.domain.Schedule
import app.habitrpg.domain.TaskType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import app.habitrpg.data.db.TagEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScheduleMode { WEEKDAYS, EVERY_N }

data class EditorState(
    val loaded: Boolean = false,
    val id: String? = null,
    val type: TaskType = TaskType.HABIT,
    val title: String = "",
    val notes: String = "",
    val difficulty: Difficulty = Difficulty.EASY,
    val plusEnabled: Boolean = true,
    val minusEnabled: Boolean = true,
    val scheduleMode: ScheduleMode = ScheduleMode.WEEKDAYS,
    val weekdayMask: Int = 0b1111111,
    val everyN: Int = 2,
    val everyNStart: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val checklist: List<ChecklistDraft> = emptyList(),
    val reminders: List<ReminderDraft> = emptyList(),
    val tagIds: Set<String> = emptySet(),
    /** Set once the editor has saved or deleted, so the screen can close. */
    val finished: Boolean = false,
) {
    val isNew get() = id == null
    val canSave: Boolean
        get() = title.isNotBlank() && when (type) {
            TaskType.HABIT -> plusEnabled || minusEnabled
            TaskType.DAILY -> scheduleMode == ScheduleMode.EVERY_N || weekdayMask != 0
            TaskType.TODO -> true
        }
}

@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: TaskRepository,
    private val tagRepo: TagRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val taskId: String? = savedState.get<String>("taskId")?.takeIf { it.isNotBlank() }
    private val type: TaskType = TaskType.valueOf(savedState.get<String>("type") ?: TaskType.HABIT.name)

    val allTags: StateFlow<List<TagEntity>> =
        tagRepo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A title to start a new task with (from "Add from text"); ignored when editing. */
    private val prefillTitle: String = savedState.get<String>("title").orEmpty()

    private val _state = MutableStateFlow(EditorState(type = type))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = taskId?.let { repo.get(it) }
            _state.value = if (existing == null) EditorState(loaded = true, type = type, title = prefillTitle) else {
                val s = existing.task.schedule
                EditorState(
                    loaded = true, id = existing.id, type = existing.type, title = existing.title,
                    notes = existing.notes, difficulty = existing.task.difficulty,
                    plusEnabled = existing.task.plusEnabled, minusEnabled = existing.task.minusEnabled,
                    scheduleMode = if (s is Schedule.EveryNDays) ScheduleMode.EVERY_N else ScheduleMode.WEEKDAYS,
                    weekdayMask = (s as? Schedule.Weekdays)?.mask ?: 0b1111111,
                    everyN = (s as? Schedule.EveryNDays)?.n ?: 2,
                    everyNStart = (s as? Schedule.EveryNDays)?.start,
                    dueDate = existing.dueDate,
                    checklist = existing.checklist.map { ChecklistDraft(it.text, it.done) },
                    reminders = existing.reminders.map { ReminderDraft(it.minuteOfDay, it.weekdayMask) },
                    tagIds = existing.tagIds,
                )
            }
        }
    }

    fun setTitle(v: String) = _state.update { it.copy(title = v) }
    fun setNotes(v: String) = _state.update { it.copy(notes = v) }
    fun setDifficulty(v: Difficulty) = _state.update { it.copy(difficulty = v) }
    fun setPlus(v: Boolean) = _state.update { it.copy(plusEnabled = v) }
    fun setMinus(v: Boolean) = _state.update { it.copy(minusEnabled = v) }
    fun setScheduleMode(v: ScheduleMode) = _state.update { it.copy(scheduleMode = v) }
    fun toggleWeekday(bit: Int) = _state.update { it.copy(weekdayMask = it.weekdayMask xor (1 shl bit)) }
    fun setEveryN(v: Int) = _state.update { it.copy(everyN = v.coerceIn(1, 365)) }
    fun setDueDate(v: LocalDate?) = _state.update { it.copy(dueDate = v) }
    fun addChecklistItem() = _state.update { it.copy(checklist = it.checklist + ChecklistDraft("")) }
    fun setChecklistText(i: Int, v: String) =
        _state.update { s -> s.copy(checklist = s.checklist.mapIndexed { n, c -> if (n == i) c.copy(text = v) else c }) }
    fun removeChecklistItem(i: Int) =
        _state.update { s -> s.copy(checklist = s.checklist.filterIndexed { n, _ -> n != i }) }

    fun toggleTag(id: String) = _state.update { s ->
        s.copy(tagIds = if (id in s.tagIds) s.tagIds - id else s.tagIds + id)
    }

    /** Creates the tag (or finds the existing one with that name) and selects it for this task. */
    fun addTag(name: String) {
        viewModelScope.launch { tagRepo.create(name)?.let { id -> _state.update { it.copy(tagIds = it.tagIds + id) } } }
    }

    fun addReminder(minuteOfDay: Int, weekdayMask: Int) =
        _state.update { s -> s.copy(reminders = (s.reminders + ReminderDraft(minuteOfDay, weekdayMask)).sortedBy { it.minuteOfDay }) }
    fun removeReminder(i: Int) =
        _state.update { s -> s.copy(reminders = s.reminders.filterIndexed { n, _ -> n != i }) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            val schedule = if (s.type != TaskType.DAILY) null else when (s.scheduleMode) {
                ScheduleMode.WEEKDAYS -> Schedule.Weekdays(s.weekdayMask)
                ScheduleMode.EVERY_N -> Schedule.EveryNDays(
                    s.everyN, s.everyNStart ?: GameDay.today(clock, settings.current().dayStartHour),
                )
            }
            repo.save(
                TaskDraft(
                    id = s.id, type = s.type, title = s.title, notes = s.notes, difficulty = s.difficulty,
                    plusEnabled = s.plusEnabled, minusEnabled = s.minusEnabled, schedule = schedule,
                    dueDate = s.dueDate.takeIf { s.type == TaskType.TODO },
                    checklist = if (s.type == TaskType.HABIT) emptyList() else s.checklist,
                    reminders = s.reminders,
                    tagIds = s.tagIds,
                ),
            )
            _state.update { it.copy(finished = true) }
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            repo.delete(id)
            _state.update { it.copy(finished = true) }
        }
    }
}
