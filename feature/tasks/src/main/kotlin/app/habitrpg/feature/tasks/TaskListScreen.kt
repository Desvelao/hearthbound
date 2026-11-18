package app.habitrpg.feature.tasks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.habitrpg.data.repo.TaskItem
import app.habitrpg.domain.Schedule
import app.habitrpg.domain.TaskType
import app.habitrpg.domain.Worth
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import android.content.res.Resources
import androidx.annotation.StringRes
import app.habitrpg.domain.Difficulty

@Composable
fun TaskListScreen(
    type: TaskType,
    onAdd: () -> Unit,
    onAddFromText: () -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val rows by viewModel.items(type).collectAsStateWithLifecycle()
    val showCompleted by viewModel.showCompleted.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val selectedTag by viewModel.selectedTag.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<TaskItem?>(null) }

    Column(modifier.fillMaxSize()) {
        if (type == TaskType.TODO) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!showCompleted, { viewModel.setShowCompleted(false) }, { Text(stringResource(R.string.tasks_filter_active)) })
                FilterChip(showCompleted, { viewModel.setShowCompleted(true) }, { Text(stringResource(R.string.tasks_filter_completed)) })
            }
        }
        if (tags.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selectedTag == null, { viewModel.setSelectedTag(null) }, { Text(stringResource(R.string.tasks_filter_all)) }) }
                items(tags, key = { it.id }) { t ->
                    FilterChip(selectedTag == t.id, { viewModel.setSelectedTag(if (selectedTag == t.id) null else t.id) }, { Text(t.name, maxLines = 1, softWrap = false) })
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            val list = rows
            when {
                list == null -> Unit
                list.isEmpty() -> Text(
                    stringResource(
                        when {
                            selectedTag != null -> R.string.tasks_empty_tag
                            type == TaskType.TODO && showCompleted -> R.string.tasks_empty_completed
                            else -> type.emptyRes()
                        },
                    ),
                    Modifier.align(Alignment.Center).padding(32.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(list, key = { it.item.id }) { row ->
                        val item = row.item
                        val menu = MenuActions(
                            onEdit = { onEdit(item.id) },
                            onUp = { viewModel.moveUp(type, item.id) },
                            onDown = { viewModel.moveDown(type, item.id) },
                            onDelete = { pendingDelete = item },
                        )
                        when (type) {
                            TaskType.HABIT -> HabitCard(row, menu, { viewModel.plus(item.id) }, { viewModel.minus(item.id) })
                            else -> CheckCard(
                                row, menu,
                                onChecked = { viewModel.setDone(item, it) },
                                onChecklist = { itemId, done -> viewModel.setChecklistDone(item.id, itemId, done) },
                            )
                        }
                    }
                }
            }
            if (!(type == TaskType.TODO && showCompleted)) {
                Column(
                    Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SmallFloatingActionButton(onClick = onAddFromText) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.tasks_bulk_open_cd))
                    }
                    FloatingActionButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(type.addRes()))
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.tasks_delete_title, item.title)) },
            text = { Text(stringResource(R.string.tasks_delete_text)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(item.id); pendingDelete = null }) { Text(stringResource(R.string.tasks_delete)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.tasks_cancel)) } },
        )
    }
}

private class MenuActions(val onEdit: () -> Unit, val onUp: () -> Unit, val onDown: () -> Unit, val onDelete: () -> Unit)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitCard(row: TaskRow, menu: MenuActions, onPlus: () -> Unit, onMinus: () -> Unit) {
    val item = row.item
    val res = LocalResources.current
    val minusLabel = stringResource(R.string.tasks_minus_cd, item.title)
    val plusLabel = stringResource(R.string.tasks_plus_cd, item.title)
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = menu.onEdit, onLongClickLabel = stringResource(R.string.tasks_edit))) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.task.minusEnabled) {
                FilledTonalIconButton(
                    onClick = onMinus,
                    modifier = Modifier.semantics { contentDescription = minusLabel },
                ) { Text("−", style = MaterialTheme.typography.titleLarge) }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(row.summary(res), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.task.plusEnabled) {
                FilledTonalIconButton(
                    onClick = onPlus,
                    modifier = Modifier.semantics { contentDescription = plusLabel },
                ) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
            ItemMenu(item, menu)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CheckCard(row: TaskRow, menu: MenuActions, onChecked: (Boolean) -> Unit, onChecklist: (String, Boolean) -> Unit) {
    val item = row.item
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val done = if (item.type == TaskType.DAILY) item.task.doneToday else item.task.completed
    val enabled = row.dueToday || done
    val res = LocalResources.current
    val doneLabel = stringResource(R.string.tasks_done_cd, item.title)
    Card(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.6f)
            .combinedClickable(onClick = {}, onLongClick = menu.onEdit, onLongClickLabel = stringResource(R.string.tasks_edit)),
    ) {
        Column(Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = done, onCheckedChange = onChecked, enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = doneLabel },
                )
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        row.summary(res) + if (!row.dueToday && item.type == TaskType.DAILY) " · " + stringResource(R.string.tasks_not_due_today) else "",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ItemMenu(item, menu)
            }
            if (item.checklist.isNotEmpty()) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        stringResource(
                            if (expanded) R.string.tasks_checklist_hide else R.string.tasks_checklist_show,
                            item.checklist.count { it.done }, item.checklist.size,
                        ),
                    )
                }
                if (expanded) {
                    item.checklist.forEach { c ->
                        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(c.done, { onChecklist(c.id, it) })
                            Text(c.text)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemMenu(item: TaskItem, menu: MenuActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.tasks_more_cd, item.title)) }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text(stringResource(R.string.tasks_edit)) }, { open = false; menu.onEdit() })
            DropdownMenuItem({ Text(stringResource(R.string.tasks_move_up)) }, { open = false; menu.onUp() })
            DropdownMenuItem({ Text(stringResource(R.string.tasks_move_down)) }, { open = false; menu.onDown() })
            DropdownMenuItem({ Text(stringResource(R.string.tasks_delete)) }, { open = false; menu.onDelete() })
        }
    }
}

internal fun TaskRow.summary(res: Resources): String =
    item.summary(res) + tags.joinToString(separator = "") { " · #$it" }

/** What a "worth more" hint says: the task currently pays above or below its base reward. */
internal fun Worth.label(res: Resources): String = res.getString(
    when (this) {
        Worth.MORE -> R.string.tasks_worth_more
        Worth.LESS -> R.string.tasks_worth_less
    },
)

@StringRes internal fun TaskType.addRes() = when (this) {
    TaskType.HABIT -> R.string.tasks_add_habit_cd
    TaskType.DAILY -> R.string.tasks_add_daily_cd
    TaskType.TODO -> R.string.tasks_add_todo_cd
}

@StringRes internal fun TaskType.newRes() = when (this) {
    TaskType.HABIT -> R.string.tasks_new_habit
    TaskType.DAILY -> R.string.tasks_new_daily
    TaskType.TODO -> R.string.tasks_new_todo
}

@StringRes internal fun TaskType.editRes() = when (this) {
    TaskType.HABIT -> R.string.tasks_edit_habit
    TaskType.DAILY -> R.string.tasks_edit_daily
    TaskType.TODO -> R.string.tasks_edit_todo
}

@StringRes internal fun TaskType.emptyRes() = when (this) {
    TaskType.HABIT -> R.string.tasks_empty_habits
    TaskType.DAILY -> R.string.tasks_empty_dailies
    TaskType.TODO -> R.string.tasks_empty_todos
}

/** The plural name of a task type, for tabs and filters. */
@StringRes internal fun TaskType.labelRes() = when (this) {
    TaskType.HABIT -> R.string.tasks_type_habits
    TaskType.DAILY -> R.string.tasks_type_dailies
    TaskType.TODO -> R.string.tasks_type_todos
}

@StringRes internal fun Difficulty.labelRes() = when (this) {
    Difficulty.TRIVIAL -> R.string.tasks_difficulty_trivial
    Difficulty.EASY -> R.string.tasks_difficulty_easy
    Difficulty.MEDIUM -> R.string.tasks_difficulty_medium
    Difficulty.HARD -> R.string.tasks_difficulty_hard
}

private fun Resources.locale(): Locale = configuration.locales[0]

/** "+3 −1": the plus and minus counts a habit shows, only for the buttons it has. */
internal fun TaskItem.habitCounts(): String = listOfNotNull(
    "+${task.plusCount}".takeIf { task.plusEnabled },
    "−${task.minusCount}".takeIf { task.minusEnabled },
).joinToString(" ")

internal fun TaskItem.summary(res: Resources): String {
    val parts = mutableListOf(res.getString(task.difficulty.labelRes()))
    when (type) {
        TaskType.HABIT -> parts += res.getString(R.string.tasks_summary_today, habitCounts())
        TaskType.DAILY -> {
            parts += task.schedule.describe(res)
            if (task.streak > 0) parts += res.getString(R.string.tasks_summary_streak, task.streak)
        }
        TaskType.TODO -> {
            dueDate?.let { parts += res.getString(R.string.tasks_summary_due, it.formatMedium(res.locale())) }
            completedAt?.let { parts += res.getString(R.string.tasks_summary_done) }
        }
    }
    // Dailies pay by streak, so only habits and open to-dos carry a value hint.
    if (type == TaskType.HABIT || (type == TaskType.TODO && !task.completed)) {
        Worth.of(task.value)?.let { parts += it.label(res) }
    }
    return parts.joinToString(" · ")
}

/** A date written the way the player's language writes it, such as "2 Mar 2026". */
internal fun LocalDate.formatMedium(locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(this)

internal fun Schedule?.describe(res: Resources): String = when (this) {
    null -> res.getString(R.string.tasks_schedule_none)
    is Schedule.EveryNDays ->
        if (n == 1) res.getString(R.string.tasks_schedule_every_day) else res.getString(R.string.tasks_schedule_every_n, n)
    is Schedule.Weekdays -> if (mask == 0b1111111) res.getString(R.string.tasks_schedule_every_day) else {
        val monday = LocalDate.of(2026, 3, 2)
        DayOfWeek.entries.filter { isDue(monday.plusDays((it.value - 1).toLong())) }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, res.locale()) }
    }
}
