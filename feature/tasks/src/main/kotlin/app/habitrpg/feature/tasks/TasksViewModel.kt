package app.habitrpg.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.habitrpg.data.repo.GameRepository
import app.habitrpg.data.db.TagEntity
import app.habitrpg.data.repo.SettingsRepository
import app.habitrpg.data.repo.TagRepository
import app.habitrpg.data.repo.TaskItem
import app.habitrpg.data.repo.TaskRepository
import app.habitrpg.domain.Event
import app.habitrpg.domain.GameDay
import app.habitrpg.domain.TaskType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A task as shown in a list: dailies also know whether they are due today. */
data class TaskRow(val item: TaskItem, val dueToday: Boolean = true, val tags: List<String> = emptyList())

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repo: TaskRepository,
    private val game: GameRepository,
    private val settings: SettingsRepository,
    private val tagRepo: TagRepository,
    private val clock: Clock,
) : ViewModel() {

    val tags: StateFlow<List<TagEntity>> =
        tagRepo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedTag = MutableStateFlow<String?>(null)
    /** The tag every list is filtered by, or null for all. A deleted tag stops filtering by itself. */
    val selectedTag: StateFlow<String?> = combine(_selectedTag, tags) { sel, all -> sel?.takeIf { id -> all.any { it.id == id } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun setSelectedTag(id: String?) { _selectedTag.value = id }


    private val _showCompleted = MutableStateFlow(false)
    /** To-do filter: false shows active to-dos, true shows completed ones. */
    val showCompleted: StateFlow<Boolean> = _showCompleted.asStateFlow()
    fun setShowCompleted(value: Boolean) { _showCompleted.value = value }

    private fun rows(type: TaskType): Flow<List<TaskRow>?> = when (type) {
        TaskType.HABIT -> repo.observe(type).map { list -> list.map { TaskRow(it) } }
        TaskType.DAILY -> combine(repo.observe(type), settings.settings) { list, s ->
            val today = GameDay.today(clock, s.dayStartHour)
            val rows = list.map { TaskRow(it, it.task.schedule?.isDue(today) ?: false) }
            rows.sortedByDescending { it.dueToday } // due today first; sortedBy is stable so the user's order holds
        }
        TaskType.TODO -> combine(repo.observe(type), _showCompleted) { list, completed ->
            list.filter { it.task.completed == completed }.map { TaskRow(it) }
        }
    }

    private val visible: Map<TaskType, StateFlow<List<TaskRow>?>> = TaskType.entries.associateWith { type ->
        combine(rows(type), selectedTag, tagRepo.observe()) { list, selected, allTags ->
            val names = allTags.associate { it.id to it.name }
            list?.filter { selected == null || selected in it.item.tagIds }
                ?.map { it.copy(tags = it.item.tagIds.mapNotNull(names::get).sorted()) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    /** `null` while the first load is in flight, so screens can tell "loading" from "empty". */
    fun items(type: TaskType): StateFlow<List<TaskRow>?> = visible.getValue(type)

    fun plus(id: String) { viewModelScope.launch { game.dispatch(Event.HabitPlus(id)) } }
    fun minus(id: String) { viewModelScope.launch { game.dispatch(Event.HabitMinus(id)) } }

    fun setDone(item: TaskItem, done: Boolean) {
        viewModelScope.launch {
            if (done) {
                game.dispatch(if (item.type == TaskType.DAILY) Event.CompleteDaily(item.id) else Event.CompleteTodo(item.id))
            } else {
                game.uncomplete(item.id)
            }
        }
    }

    fun setChecklistDone(taskId: String, itemId: String, done: Boolean) {
        viewModelScope.launch { repo.setChecklistItemDone(itemId, taskId, done) }
    }

    fun moveUp(type: TaskType, id: String) = move(type, id, -1)
    fun moveDown(type: TaskType, id: String) = move(type, id, +1)

    private fun move(type: TaskType, id: String, delta: Int) {
        val shown = visible.getValue(type).value?.map { it.item.id } ?: return
        val neighbour = shown.getOrNull(shown.indexOf(id) + delta) ?: return
        viewModelScope.launch {
            // Reorder the full list (read fresh, not the filtered view) so hidden items keep their place.
            val full = repo.observe(type).first().map { it.id }.toMutableList()
            val from = full.indexOf(id)
            val target = full.indexOf(neighbour)
            if (from < 0 || target < 0) return@launch
            full.add(target, full.removeAt(from))
            repo.reorder(full)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }
}
