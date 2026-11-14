package app.habitrpg.feature.tasks.today

import app.habitrpg.data.repo.dayMonthFormatter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import app.habitrpg.domain.QuestOutcome
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import app.habitrpg.data.Names
import app.habitrpg.feature.tasks.R
import app.habitrpg.feature.tasks.habitCounts
import app.habitrpg.feature.tasks.label
import app.habitrpg.feature.tasks.labelRes
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.habitrpg.domain.TaskType
import java.time.format.DateTimeFormatter

/** Everything that wants doing today in one list: what is due, your habits, and what you have already finished. */
@Composable
fun TodayScreen(onEdit: (TaskType, String) -> Unit, modifier: Modifier = Modifier, viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loaded) return
    TodayContent(
        state, viewModel::setFilter, onEdit,
        onToggle = viewModel::setDone, onPlus = viewModel::plus, onMinus = viewModel::minus, modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TodayContent(
    state: TodayState,
    onFilter: (TodayFilter) -> Unit,
    onEdit: (TaskType, String) -> Unit,
    onToggle: (app.habitrpg.data.repo.TaskItem, Boolean) -> Unit,
    onPlus: (String) -> Unit,
    onMinus: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    var showDone by rememberSaveable { mutableStateOf(false) }
    val everythingDone = state.total > 0 && state.finished == state.total
    val nothingToShow = state.dueNow.isEmpty() && state.habits.isEmpty() && state.done.isEmpty()

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tasks_today_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                    Text(
                        state.date.format(dayMonthFormatter(locale)) +
                            if (state.total > 0) " · " + stringResource(R.string.tasks_today_done_count, state.finished, state.total) else "",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.total > 0) ProgressRing(state.finished, state.total)
            }
        }
        state.quest?.let { q -> item(key = "quest") { QuestCardRow(q) } }
        item(key = "filters") {
            // Scrolls sideways instead of wrapping, so a long word (German "Gewohnheiten") is never broken mid-word.
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TodayFilter.entries.forEach { f ->
                    FilterChip(
                        selected = state.filter == f, onClick = { onFilter(f) },
                        label = { Text(stringResource(f.labelRes()), maxLines = 1, softWrap = false) },
                    )
                }
            }
        }

        if (nothingToShow) {
            item(key = "empty") {
                Text(
                    stringResource(
                        when (state.filter) {
                            TodayFilter.ALL -> R.string.tasks_today_empty_all
                            TodayFilter.DAILIES -> R.string.tasks_today_empty_dailies
                            TodayFilter.TODOS -> R.string.tasks_today_empty_todos
                            TodayFilter.HABITS -> R.string.tasks_today_empty_habits
                        },
                    ),
                    Modifier.padding(vertical = 24.dp), style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        if (state.dueNow.isNotEmpty()) {
            item(key = "due-label") { SectionLabel(stringResource(R.string.tasks_today_due_now, state.dueNow.size)) }
            items(state.dueNow, key = { "due-" + it.item.id }) { row ->
                CheckRow(row, checked = false, onToggle = { onToggle(row.item, true) }, onEdit = { onEdit(row.item.type, row.item.id) })
            }
        } else if (everythingDone && state.filter != TodayFilter.HABITS) {
            item(key = "all-done") {
                Text(stringResource(R.string.tasks_today_all_done), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            }
        }

        if (state.habits.isNotEmpty()) {
            item(key = "habit-label") { SectionLabel(stringResource(TaskType.HABIT.labelRes())) }
            items(state.habits, key = { "habit-" + it.item.id }) { row ->
                HabitRow(row, onPlus = { onPlus(row.item.id) }, onMinus = { onMinus(row.item.id) }, onEdit = { onEdit(TaskType.HABIT, row.item.id) })
            }
        }

        if (state.done.isNotEmpty()) {
            item(key = "done-toggle") {
                val expandedLabel = stringResource(if (showDone) R.string.tasks_expanded else R.string.tasks_collapsed)
                Surface(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { showDone = !showDone }
                        .semantics { stateDescription = expandedLabel },
                    shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.tasks_today_done_toggle, state.done.size), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(if (showDone) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
                    }
                }
            }
            if (showDone) {
                items(state.done, key = { "done-" + it.item.id }) { row ->
                    CheckRow(row, checked = true, onToggle = { onToggle(row.item, false) }, onEdit = { onEdit(row.item.type, row.item.id) })
                }
            }
        }
    }
}

@StringRes
private fun TodayFilter.labelRes() = when (this) {
    TodayFilter.ALL -> R.string.tasks_filter_all
    TodayFilter.DAILIES -> R.string.tasks_type_dailies
    TodayFilter.TODOS -> R.string.tasks_type_todos
    TodayFilter.HABITS -> R.string.tasks_type_habits
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), Modifier.padding(top = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A ring that fills as the day's tasks get done. The count in the middle says the same thing in words. */
@Composable
internal fun ProgressRing(finished: Int, total: Int) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.secondary
    val ringLabel = stringResource(R.string.tasks_ring_cd, finished, total)
    Box(Modifier.size(56.dp).semantics(mergeDescendants = true) { contentDescription = ringLabel }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(56.dp)) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2
            val box = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), box, style = Stroke(stroke))
            if (total > 0) drawArc(fill, -90f, 360f * finished / total, false, Offset(inset, inset), box, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("$finished/$total", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

/** A daily or to-do. The whole row is the tap target, and long-press edits. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CheckRow(row: TodayRow, checked: Boolean, onToggle: () -> Unit, onEdit: () -> Unit) {
    val item = row.item
    val res = LocalResources.current
    val meta = buildList {
        add(res.getString(if (item.type == TaskType.DAILY) R.string.tasks_meta_daily else R.string.tasks_meta_todo))
        if (item.type == TaskType.DAILY && item.task.streak > 0) add(res.getString(R.string.tasks_summary_streak, item.task.streak))
        if (item.checklist.isNotEmpty()) add(res.getString(R.string.tasks_meta_checklist, item.checklist.count { it.done }, item.checklist.size))
        if (!checked) add(res.getString(R.string.tasks_meta_xp, row.xp))
        if (!checked) row.worth?.let { add(it.label(res)) }
    }.joinToString(" · ")
    val markLabel = stringResource(if (checked) R.string.tasks_mark_not_done else R.string.tasks_mark_done)
    val editLabel = stringResource(R.string.tasks_edit)
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .combinedClickable(role = Role.Checkbox, onClickLabel = markLabel, onClick = onToggle,
                    onLongClickLabel = editLabel, onLongClick = onEdit)
                .semantics { toggleableState = if (checked) ToggleableState.On else ToggleableState.Off; contentDescription = item.title }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked, onCheckedChange = null, modifier = Modifier.padding(12.dp).clearAndSetSemantics {})
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            row.note?.let {
                Text(it.text(res), Modifier.padding(end = 12.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitRow(row: TodayRow, onPlus: () -> Unit, onMinus: () -> Unit, onEdit: () -> Unit) {
    val item = row.item
    val res = LocalResources.current
    val minusLabel = stringResource(R.string.tasks_minus_cd, item.title)
    val plusLabel = stringResource(R.string.tasks_plus_cd, item.title)
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).combinedClickable(onClick = {}, onLongClickLabel = stringResource(R.string.tasks_edit), onLongClick = onEdit)
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    res.getString(R.string.tasks_summary_today, item.habitCounts()) +
                        " · " + res.getString(R.string.tasks_habit_xp_each, row.xp) + (row.worth?.let { " · ${it.label(res)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.task.minusEnabled) {
                FilledTonalIconButton(onClick = onMinus, Modifier.semantics { contentDescription = minusLabel }) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
            }
            if (item.task.plusEnabled) {
                FilledTonalIconButton(onClick = onPlus, Modifier.semantics { contentDescription = plusLabel }) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

/** The words for a row's right-hand note. */
private fun RowNote.text(res: android.content.res.Resources): String = when (this) {
    RowNote.Overdue -> res.getString(R.string.tasks_note_overdue)
    RowNote.DueToday -> res.getString(R.string.tasks_note_due_today)
    is RowNote.Reminder -> "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
}

@Composable
internal fun QuestCardRow(q: QuestCard, modifier: Modifier = Modifier) {
    val name = Names.boss(q.bossId)?.let { stringResource(it) } ?: ""
    val status = when (q.outcome) {
        QuestOutcome.WON -> stringResource(R.string.tasks_quest_won)
        QuestOutcome.ESCAPED -> stringResource(R.string.tasks_quest_escaped)
        QuestOutcome.ACTIVE -> stringResource(
            R.string.tasks_quest_status,
            stringResource(R.string.tasks_quest_hp, q.hp, q.maxHp),
            if (q.daysLeft == 1) stringResource(R.string.tasks_quest_last_day)
            else pluralStringResource(R.plurals.tasks_quest_days_left, q.daysLeft, q.daysLeft),
        )
    }
    val description = stringResource(R.string.tasks_quest_cd, name, status)
    androidx.compose.material3.Card(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp).semantics(mergeDescendants = true) { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.tasks_quest_title, name), style = MaterialTheme.typography.titleMedium)
            androidx.compose.material3.LinearProgressIndicator(
                progress = { if (q.maxHp == 0) 0f else q.hp.toFloat() / q.maxHp }, modifier = Modifier.fillMaxWidth(),
            )
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
