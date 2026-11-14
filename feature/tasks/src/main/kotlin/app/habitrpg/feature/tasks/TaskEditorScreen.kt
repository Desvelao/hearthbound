package app.habitrpg.feature.tasks

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.habitrpg.data.repo.TagRepository
import app.habitrpg.domain.Difficulty
import app.habitrpg.domain.TaskType
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.TextStyle

/** One editor that adapts to the task type (habit, daily or to-do). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(onClose: () -> Unit, viewModel: TaskEditorViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.finished) { if (s.finished) onClose() }
    var showDate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (s.isNew) s.type.newRes() else s.type.editRes())) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.tasks_back_cd)) }
                },
                actions = { TextButton(onClick = viewModel::save, enabled = s.canSave && s.loaded) { Text(stringResource(R.string.tasks_save)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                s.title, viewModel::setTitle, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.tasks_field_title)) }, singleLine = true,
            )
            OutlinedTextField(s.notes, viewModel::setNotes, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.tasks_field_notes)) })

            Text(stringResource(R.string.tasks_section_difficulty), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Difficulty.entries.forEach { d ->
                    FilterChip(
                        selected = s.difficulty == d, onClick = { viewModel.setDifficulty(d) },
                        label = { Text(stringResource(d.labelRes())) },
                    )
                }
            }

            when (s.type) {
                TaskType.HABIT -> {
                    SwitchRow(stringResource(R.string.tasks_plus_enabled), s.plusEnabled, viewModel::setPlus)
                    SwitchRow(stringResource(R.string.tasks_minus_enabled), s.minusEnabled, viewModel::setMinus)
                    if (!s.plusEnabled && !s.minusEnabled) {
                        Text(stringResource(R.string.tasks_enable_one), color = MaterialTheme.colorScheme.error)
                    }
                }
                TaskType.DAILY -> ScheduleSection(s, viewModel)
                TaskType.TODO -> {
                    Text(stringResource(R.string.tasks_section_due), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { showDate = true }) { Text(s.dueDate?.formatMedium(LocalConfiguration.current.locales[0]) ?: stringResource(R.string.tasks_no_due)) }
                        if (s.dueDate != null) {
                            IconButton(onClick = { viewModel.setDueDate(null) }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.tasks_clear_due_cd))
                            }
                        }
                    }
                }
            }

            if (s.type != TaskType.HABIT) ChecklistSection(s, viewModel)

            TagsSection(s, viewModel)

            RemindersSection(s, viewModel)

            if (!s.isNew) {
                TextButton(onClick = viewModel::delete) { Text(stringResource(R.string.tasks_delete_task), color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (showDate) {
        val initial = s.dueDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        viewModel.setDueDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDate = false
                }) { Text(stringResource(R.string.tasks_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.tasks_cancel)) } },
        ) { DatePicker(pickerState) }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
private fun ScheduleSection(s: EditorState, vm: TaskEditorViewModel) {
    Text(stringResource(R.string.tasks_section_repeats), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(s.scheduleMode == ScheduleMode.WEEKDAYS, { vm.setScheduleMode(ScheduleMode.WEEKDAYS) }, { Text(stringResource(R.string.tasks_repeat_weekdays)) })
        FilterChip(s.scheduleMode == ScheduleMode.EVERY_N, { vm.setScheduleMode(ScheduleMode.EVERY_N) }, { Text(stringResource(R.string.tasks_repeat_every_n)) })
    }
    if (s.scheduleMode == ScheduleMode.WEEKDAYS) {
        val locale = LocalConfiguration.current.locales[0]
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DayOfWeek.entries.forEachIndexed { bit, day ->
                FilterChip(
                    selected = s.weekdayMask and (1 shl bit) != 0,
                    onClick = { vm.toggleWeekday(bit) },
                    label = { Text(day.getDisplayName(TextStyle.NARROW, locale)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (s.weekdayMask == 0) Text(stringResource(R.string.tasks_pick_day), color = MaterialTheme.colorScheme.error)
    } else {
        OutlinedTextField(
            value = s.everyN.toString(),
            onValueChange = { text -> text.toIntOrNull()?.let(vm::setEveryN) },
            label = { Text(stringResource(R.string.tasks_field_days_between)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
    }
}

@Composable
private fun ChecklistSection(s: EditorState, vm: TaskEditorViewModel) {
    Text(stringResource(R.string.tasks_section_checklist), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    s.checklist.forEachIndexed { i, item ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                item.text, { vm.setChecklistText(i, it) }, Modifier.weight(1f),
                label = { Text(stringResource(R.string.tasks_checklist_item, i + 1)) }, singleLine = true,
            )
            IconButton(onClick = { vm.removeChecklistItem(i) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.tasks_checklist_remove_cd, i + 1))
            }
        }
    }
    OutlinedButton(onClick = vm::addChecklistItem) { Text(stringResource(R.string.tasks_checklist_add)) }
}

/** Asks for the notification permission (Android 13+) the first time a reminder is being added. */
private fun needsNotificationPermission(context: Context) =
    Build.VERSION.SDK_INT >= 33 &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemindersSection(s: EditorState, vm: TaskEditorViewModel) {
    val context = LocalContext.current
    val res = context.resources
    val locale = LocalConfiguration.current.locales[0]
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var adding by remember { mutableStateOf(false) }

    Text(stringResource(R.string.tasks_section_reminders), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    s.reminders.forEachIndexed { i, r ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${formatTime(r.minuteOfDay)} · ${describeMask(r.weekdayMask, res, locale)}",
                Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
            )
            IconButton(onClick = { vm.removeReminder(i) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.tasks_reminder_remove_cd, formatTime(r.minuteOfDay)))
            }
        }
    }
    OutlinedButton(onClick = {
        if (needsNotificationPermission(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        adding = true
    }) { Text(stringResource(R.string.tasks_reminder_add)) }

    if (adding) {
        val time = rememberTimePickerState(9, 0, DateFormat.is24HourFormat(context))
        var mask by remember {
            mutableIntStateOf(
                if (s.type == TaskType.DAILY && s.scheduleMode == ScheduleMode.WEEKDAYS && s.weekdayMask != 0) s.weekdayMask else 0b1111111,
            )
        }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.tasks_reminder_new)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimePicker(time)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        DayOfWeek.entries.forEachIndexed { bit, day ->
                            FilterChip(
                                selected = mask and (1 shl bit) != 0,
                                onClick = { mask = mask xor (1 shl bit) },
                                label = { Text(day.getDisplayName(TextStyle.NARROW, locale)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (mask == 0) Text(stringResource(R.string.tasks_pick_day), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = mask != 0,
                    onClick = { vm.addReminder(time.hour * 60 + time.minute, mask); adding = false },
                ) { Text(stringResource(R.string.tasks_add)) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.tasks_cancel)) } },
        )
    }
}

internal fun formatTime(minuteOfDay: Int) = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

internal fun describeMask(mask: Int, res: android.content.res.Resources, locale: java.util.Locale): String {
    if (mask and 0b1111111 == 0b1111111) return res.getString(R.string.tasks_schedule_every_day)
    return DayOfWeek.entries.filterIndexed { bit, _ -> mask and (1 shl bit) != 0 }
        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsSection(s: EditorState, vm: TaskEditorViewModel) {
    val tags by vm.allTags.collectAsStateWithLifecycle()
    var newTag by remember { mutableStateOf("") }
    Text(stringResource(R.string.tasks_section_tags), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    if (tags.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { t ->
                FilterChip(selected = t.id in s.tagIds, onClick = { vm.toggleTag(t.id) }, label = { Text(t.name) })
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            newTag, { newTag = it.take(TagRepository.MAX_LENGTH) }, Modifier.weight(1f),
            label = { Text(stringResource(R.string.tasks_new_tag)) }, singleLine = true,
        )
        OutlinedButton(onClick = { vm.addTag(newTag); newTag = "" }, enabled = newTag.isNotBlank()) { Text(stringResource(R.string.tasks_add)) }
    }
}
