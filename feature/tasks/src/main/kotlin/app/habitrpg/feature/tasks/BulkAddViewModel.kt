package app.habitrpg.feature.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.habitrpg.data.repo.TaskDraft
import app.habitrpg.data.repo.TaskRepository
import app.habitrpg.domain.Schedule
import app.habitrpg.domain.TaskType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The key the editor writes (into the bulk screen's nav entry) when it saved the task of a line. */
const val BULK_ADDED_KEY = "bulkAdded"

data class BulkLine(val id: Int, val title: String, val type: TaskType, val guess: Guess)

data class BulkState(
    val text: String = "",
    /** Null while the text is being typed; a list once "Preview" was tapped. */
    val lines: List<BulkLine>? = null,
    val finished: Boolean = false,
) {
    val canPreview get() = text.isNotBlank()
    val canAdd get() = lines?.any { it.title.isNotBlank() } == true
}

@HiltViewModel
class BulkAddViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: TaskRepository,
) : ViewModel() {
    /** The tab the screen was opened from: the type of lines with no keyword. */
    val defaultType: TaskType = TaskType.valueOf(savedState.get<String>("type") ?: TaskType.TODO.name)

    private val _state = MutableStateFlow(BulkState())
    val state: StateFlow<BulkState> = _state.asStateFlow()
    private var nextId = 0

    fun setText(v: String) = _state.update { it.copy(text = v) }

    fun preview() = _state.update { s ->
        val parsed = BulkParser.parse(s.text, defaultType).map { BulkLine(nextId++, it.title, it.type, it.guess) }
        if (parsed.isEmpty()) s else s.copy(lines = parsed)
    }

    /** Back from the list to the text, keeping the text. */
    fun editText() = _state.update { it.copy(lines = null) }

    fun setTitle(id: Int, v: String) = update(id) { it.copy(title = v) }
    fun setType(id: Int, v: TaskType) = update(id) { it.copy(type = v, guess = Guess.EXPLICIT) }
    fun removeLine(id: Int) = _state.update { s ->
        val left = s.lines?.filterNot { it.id == id }
        // Nothing left to add (all removed or all sent to the editor): the screen is done.
        if (left != null && left.isEmpty()) s.copy(lines = left, finished = true) else s.copy(lines = left)
    }

    private fun update(id: Int, f: (BulkLine) -> BulkLine) =
        _state.update { s -> s.copy(lines = s.lines?.map { if (it.id == id) f(it) else it }) }

    fun addAll() {
        val lines = _state.value.lines?.filter { it.title.isNotBlank() } ?: return
        viewModelScope.launch {
            repo.saveAll(lines.map { it.toDraft() })
            _state.update { it.copy(finished = true) }
        }
    }

    private fun BulkLine.toDraft() = TaskDraft(
        type = type,
        title = title,
        schedule = if (type == TaskType.DAILY) Schedule.Weekdays.EVERY_DAY else null,
    )
}
