package app.habitrpg.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.habitrpg.domain.TaskType

/**
 * "Add from text": type or paste one task per line, check the type each line got, then add them all. A line's
 * pencil opens the full editor for that line; once saved there, the line leaves this list.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BulkAddScreen(
    onClose: () -> Unit,
    onEditLine: (type: TaskType, lineId: String, title: String) -> Unit,
    /** The id of a line the editor just saved as a real task (from the nav entry), or null. */
    savedLine: String? = null,
    onSavedLineHandled: () -> Unit = {},
    viewModel: BulkAddViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    var showHelp by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(s.finished) { if (s.finished) onClose() }
    LaunchedEffect(savedLine) {
        savedLine?.toIntOrNull()?.let(viewModel::removeLine)
        if (savedLine != null) onSavedLineHandled()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tasks_bulk_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (s.lines != null) viewModel.editText() else onClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.tasks_back_cd))
                    }
                },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.tasks_bulk_help_cd))
                    }
                },
            )
        },
    ) { padding ->
        val lines = s.lines
        if (lines == null) {
            Column(
                Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = s.text,
                    onValueChange = viewModel::setText,
                    label = { Text(stringResource(R.string.tasks_bulk_field)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                )
                Text(stringResource(R.string.tasks_bulk_tip), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { showHelp = true }) { Text(stringResource(R.string.tasks_bulk_help_link)) }
                Button(onClick = viewModel::preview, enabled = s.canPreview, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tasks_bulk_preview))
                }
            }
        } else {
            Column(Modifier.padding(padding).fillMaxSize()) {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(lines, key = { it.id }) { line ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = line.title,
                                        onValueChange = { viewModel.setTitle(line.id, it) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(
                                        onClick = { onEditLine(line.type, line.id.toString(), line.title) },
                                        enabled = line.title.isNotBlank(),
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.tasks_bulk_edit_options_cd))
                                    }
                                    IconButton(onClick = { viewModel.removeLine(line.id) }) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.tasks_bulk_remove_cd))
                                    }
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TaskType.entries.forEach { t ->
                                        FilterChip(
                                            selected = line.type == t,
                                            onClick = { viewModel.setType(line.id, t) },
                                            label = { Text(stringResource(t.labelRes())) },
                                        )
                                    }
                                }
                                when (line.guess) {
                                    Guess.KEYWORD -> Text(stringResource(R.string.tasks_bulk_guessed), style = MaterialTheme.typography.bodySmall)
                                    Guess.DEFAULT -> Text(stringResource(R.string.tasks_bulk_no_match), style = MaterialTheme.typography.bodySmall)
                                    Guess.EXPLICIT -> Unit
                                }
                            }
                        }
                    }
                }
                val count = lines.count { it.title.isNotBlank() }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = viewModel::editText) { Text(stringResource(R.string.tasks_bulk_edit_text)) }
                    Button(onClick = viewModel::addAll, enabled = s.canAdd, modifier = Modifier.weight(1f)) {
                        Text(pluralStringResource(R.plurals.tasks_bulk_add, count, count))
                    }
                }
            }
        }
    }

    if (showHelp) BulkHelpDialog(viewModel.defaultType) { showHelp = false }
}

/** What the guesser understands, built from the same word lists the classifier uses. */
@Composable
private fun BulkHelpDialog(defaultType: TaskType, onDismiss: () -> Unit) {
    val language = LocalConfiguration.current.locales[0].language
    val words = BulkParser.keywordsFor(language)
    val habitWords = (listOf("+", "+/-") + words.habit).joinToString(", ")
    val dailyWords = words.daily.joinToString(", ")
    val weekly = words.every.first() + " " + words.weekdays.first()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tasks_bulk_help_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.tasks_bulk_help_prefix))
                Text(stringResource(R.string.tasks_bulk_help_clean))
                Text(stringResource(R.string.tasks_bulk_help_habit, habitWords))
                Text(stringResource(R.string.tasks_bulk_example, stringResource(R.string.tasks_bulk_example_habit)))
                Text(stringResource(R.string.tasks_bulk_help_daily, dailyWords, weekly))
                Text(stringResource(R.string.tasks_bulk_example, stringResource(R.string.tasks_bulk_example_daily)))
                Text(stringResource(R.string.tasks_bulk_help_fallback, stringResource(defaultType.labelRes())))
                Text(stringResource(R.string.tasks_bulk_example, stringResource(R.string.tasks_bulk_example_todo)))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tasks_ok)) } },
    )
}
