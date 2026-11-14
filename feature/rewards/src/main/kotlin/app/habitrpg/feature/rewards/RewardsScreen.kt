package app.habitrpg.feature.rewards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.habitrpg.data.db.RewardEntity

@Composable
fun RewardsScreen(modifier: Modifier = Modifier, viewModel: RewardsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // null = closed, a reward with blank id = new
    var editing by remember { mutableStateOf<RewardEntity?>(null) }

    Box(modifier.fillMaxSize()) {
        val rewards = state.rewards
        when {
            rewards == null -> Unit
            rewards.isEmpty() -> Text(
                stringResource(R.string.rewards_empty),
                Modifier.align(Alignment.Center).padding(32.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(rewards, key = { it.id }) { r ->
                    val affordable = state.gold >= r.cost
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.title, style = MaterialTheme.typography.titleMedium)
                                val cost = stringResource(R.string.rewards_cost, r.cost)
                                val needMore = if (affordable) null else stringResource(R.string.rewards_need_more, r.cost - state.gold)
                                Text(
                                    listOfNotNull(cost, needMore, r.notes.takeIf { it.isNotBlank() }).joinToString(" · "),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Button(onClick = { viewModel.buy(r.id) }, enabled = affordable) { Text(stringResource(R.string.rewards_buy)) }
                            IconButton(onClick = { editing = r }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rewards_edit_cd, r.title))
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { editing = RewardEntity("", "", 10) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.rewards_add_cd)) }
    }

    editing?.let { r ->
        RewardDialog(
            reward = r,
            onDismiss = { editing = null },
            onSave = { title, cost, notes -> viewModel.save(r.id.ifBlank { null }, title, cost, notes); editing = null },
            onDelete = { viewModel.delete(r.id); editing = null },
        )
    }
}

@Composable
private fun RewardDialog(reward: RewardEntity, onDismiss: () -> Unit, onSave: (String, Int, String) -> Unit, onDelete: () -> Unit) {
    var title by remember { mutableStateOf(reward.title) }
    var cost by remember { mutableStateOf(reward.cost.toString()) }
    var notes by remember { mutableStateOf(reward.notes) }
    val costValue = cost.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (reward.id.isBlank()) R.string.rewards_dialog_new else R.string.rewards_dialog_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.rewards_field_title)) }, singleLine = true)
                OutlinedTextField(
                    cost, { cost = it.filter(Char::isDigit).take(6) }, label = { Text(stringResource(R.string.rewards_field_cost)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                )
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.rewards_field_notes)) })
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, costValue ?: 0, notes) }, enabled = title.isNotBlank() && costValue != null) { Text(stringResource(R.string.rewards_save)) }
        },
        dismissButton = {
            Row {
                if (reward.id.isNotBlank()) TextButton(onClick = onDelete) { Text(stringResource(R.string.rewards_delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.rewards_cancel)) }
            }
        },
    )
}
